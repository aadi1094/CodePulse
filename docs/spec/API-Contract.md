# CodePulse REST API contract

Specification 1.0 · Proposed MVP contract · 17 September 2026  
Read with [the engineering blueprint](CodePulse-Engineering-Blueprint.md) and [reference schema](Database-Schema.sql). Routes below describe work to implement; they are not running endpoints. V1 extensions are explicitly separated.

## 1. Shared conventions

Base path: `/api/v1`. JSON names use camelCase; database names use snake_case. UUIDs are strings, instants are ISO-8601 UTC, counts are integers, and a missing measurement is JSON `null`. A property shown as nullable is returned with `null`, not silently omitted. Human messages may evolve; clients branch on stable codes.

Authentication labels:

- **Public:** callable without login; unsafe public methods still require CSRF.
- **User:** authenticated USER or ADMIN, scoped to the principal's own project.
- **Admin:** authenticated ADMIN, restricted to the stated operational data.

Sessions use a production cookie named `__Host-codepulse`, Secure, HttpOnly, SameSite=Lax, Path=/, with no Domain attribute. Local HTTP uses a distinct `codepulse` name without Secure; do not use the `__Host-` prefix with an insecure local cookie. Browser calls use same-origin credentials. Session idle timeout is 30 minutes. Restart can invalidate sessions in MVP.

All POST/PATCH/DELETE requests include the CSRF header returned by `GET /auth/csrf`. Tokens are not API access tokens. Fetch a new token after login/logout. Login alone is form encoded; other request bodies are JSON. Reject unsupported Content-Type with 415 and bodies above 16 KiB with 413.

Shared error format (`application/problem+json`):

```json
{
  "type": "urn:codepulse:problem:validation",
  "title": "Invalid request",
  "status": 400,
  "detail": "Correct the highlighted fields.",
  "instance": "/api/v1/projects",
  "code": "VALIDATION_ERROR",
  "traceId": "c1db932fa451",
  "violations": [{"field": "name", "message": "Must contain 1 to 80 characters."}]
}
```

Shared errors apply to every relevant row below even if not repeated: 400 invalid UUID/query/JSON; 401 no valid session on protected safe requests; 403 invalid/missing CSRF or denied role/disabled operation; 404 missing or not-owned resource; 413 oversized body; 415 unsupported media type; 429 quota/rate limit; 500 safe internal error with traceId. A state-changing unauthenticated request may encounter CSRF rejection before the authentication entry point. Configure JSON security handlers rather than redirecting API failures to HTML.

For a nested ID, verify that it belongs to the named parent and principal. Cross-owner or cross-run guesses return 404. A UUID is not an authorization mechanism.

Pagination contract:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

`page >= 0`, `size = 1..100`, default 20. Overflow or malformed integers are 400. Stable sorting appends UUID ASC. Fields with NULL sort last. Sort names are allowlisted; user input is never inserted into SQL. Page beyond the end returns 200 with an empty list. Scope filter values are PRODUCTION (MAIN + OTHER_SOURCE), TEST, ALL; default PRODUCTION for metrics/hotspots. File inventory defaults ALL so failures are visible.

Cache policy: private API responses `Cache-Control: no-store` for the MVP. Public bundled demo JSON can have its own static cache headers and is not served through protected run IDs. All dates and IDs in examples are illustrative.

## 2. Authentication and users

| Method and path | Auth | Request | Success | Validation and specific errors |
|---|---|---|---|---|
| GET `/auth/csrf` | Public | None | 200 `CsrfToken` | Creates/loads token/session; no-store |
| POST `/auth/register` | Public + CSRF | `RegisterRequest` | 201 `User`; Location `/api/v1/users/me`; registration does not log in | 400 fields; 409 `EMAIL_EXISTS`; 403 `REGISTRATION_DISABLED`; 429 registration limit |
| POST `/auth/login` | Public + CSRF | Form `email=...&password=...` | 200 `User` + session cookie; no redirect | 401 `INVALID_CREDENTIALS` for wrong user/password; 400 malformed form; 429 login throttling |
| POST `/auth/logout` | User + CSRF | No body | 204; session invalidated, cookie expired | Fetch a new CSRF token before next login; standard filter handles logout |
| GET `/users/me` | User | None | 200 `User` | 401 expired session |
| PATCH `/users/me` | User + CSRF | `{"displayName":"Karan"}` | 200 `User` | 400 blank/>80; extra fields such as email/role/password rejected |

Schema definitions:

```text
CsrfToken {
  headerName: string,              // normally X-CSRF-TOKEN
  parameterName: string,           // normally _csrf
  token: string
}
RegisterRequest {
  email: string[3..254],           // valid email, trimmed/lowercased on server
  displayName: string[1..80],      // trim; no control characters
  password: string[12..64]         // also <=72 UTF-8 bytes; do not trim
}
User {
  id: UUID, email: string, displayName: string,
  role: "USER" | "ADMIN", createdAt: Instant
}
```

Unknown fields in mutation DTOs are rejected, avoiding a misleading successful response to a submitted `role` or `ownerId`. Password reset/email change/account deletion are not MVP endpoints. Use the standard Spring form authentication filter with a custom email parameter and JSON success/failure handlers; do not write a password check in a controller and forget to persist the security context.

Example login request sequence:

```text
GET /api/v1/auth/csrf
POST /api/v1/auth/login
  Content-Type: application/x-www-form-urlencoded
  X-CSRF-TOKEN: <the returned token>
  body: email=developer%40example.com&password=<URL-encoded password>
GET /api/v1/auth/csrf     # refreshed after successful login
GET /api/v1/users/me
```

## 3. Projects and public repository connections

| Method and path | Auth | Request | Success | Validation and specific errors |
|---|---|---|---|---|
| GET `/projects` | User | `page,size`; fixed createdAt DESC order | 200 `Page<Project>` | Owner scoped |
| POST `/projects` | User + CSRF | `ProjectCreate` | 201 `Project`; Location to new project | 400 fields; 409 `PROJECT_NAME_EXISTS`; 429 `PROJECT_LIMIT` at five |
| GET `/projects/{projectId}` | User | UUID | 200 `Project` | Shared ownership errors |
| PATCH `/projects/{projectId}` | User + CSRF | `ProjectUpdate` | 200 `Project` | 409 stale `version` or duplicate name |
| DELETE `/projects/{projectId}` | User + CSRF | No body | 204; cascades terminal data | 409 `ACTIVE_ANALYSIS` if any queued/running job |
| POST `/projects/{projectId}/repository` | User + CSRF | `RepositoryConnect` | 201 `Repository`; Location to GET route | 409 `REPOSITORY_ALREADY_CONNECTED`; 422 `REPOSITORY_UNAVAILABLE`; 403 `SOURCE_NOT_ALLOWED`; 429 `UPSTREAM_RATE_LIMITED`; 502/504 upstream failure/timeout |
| GET `/projects/{projectId}/repository` | User | None | 200 `Repository` | 404 if not connected |

Connecting is immutable in MVP. Create another project to change repository, or delete/recreate the project after removing active work. There is no token upload, OAuth connection, arbitrary host, private repository, custom branch, or arbitrary commit input endpoint. Each run resolves the current default branch.

```text
ProjectCreate { name: string[1..80], description?: string[0..500] }
ProjectUpdate { name: string[1..80], description: string[0..500], version: integer>=0 }
Project {
  id: UUID, name: string, description: string, version: integer,
  createdAt: Instant, updatedAt: Instant,
  repositoryId: UUID|null, latestSucceededAnalysisId: UUID|null
}
RepositoryConnect { owner: string, name: string }
Repository {
  id: UUID, projectId: UUID, provider: "GITHUB", providerRepositoryId: string,
  owner: string, name: string, canonicalUrl: string,
  defaultBranch: string, visibility: "PUBLIC",
  createdAt: Instant, verifiedAt: Instant
}
```

Use a string for GitHub's numeric repository ID over JSON to avoid JavaScript integer precision assumptions. Database storage is bigint. Project names are trimmed, 1–80 characters; case-sensitive uniqueness within an owner is deliberate. Reject control characters.

Owner validation: 1–39 ASCII alphanumerics/hyphens, beginning and ending in an alphanumeric. Name validation: 1–100 ASCII alphanumerics/periods/underscores/hyphens, excluding `.` and `..`. Backend constructs the URL; it does not fetch a pasted URL. GitHub metadata determines canonical spelling/identity. Treat inaccessible/private/deleted similarly as `REPOSITORY_UNAVAILABLE`.

Example:

```json
{"owner":"example-user","name":"codepulse-fixtures"}
```

The server checks its repository allowlist before network access where configured. Connection validation has short HTTP timeouts; long source acquisition occurs only in the job. At scan time revalidate public visibility and provider ID so a deleted-and-recreated repository name cannot silently become a different source.

## 4. Analysis lifecycle and history

| Method and path | Auth | Request | Success | Validation and specific errors |
|---|---|---|---|---|
| POST `/repositories/{repositoryId}/analyses` | User + CSRF | Empty JSON `{}` | 202 `AnalysisRun`; Location `/api/v1/analyses/{id}`; Retry-After: 2 | 403 `ANALYSIS_DISABLED`/`SOURCE_NOT_ALLOWED`; 409 `ACTIVE_ANALYSIS` with activeRunId; 429 quota/queue full |
| GET `/repositories/{repositoryId}/analyses` | User | `page,size,status?` | 200 `Page<AnalysisRun>`; newest first | status enum only; returns failed/active and succeeded history |
| GET `/analyses/{analysisId}` | User | UUID | 200 `AnalysisRun` | This is also the progress/status endpoint |
| DELETE `/analyses/{analysisId}` | User + CSRF | No body | 204 | 409 `ACTIVE_ANALYSIS` for queued/running |

No cancel or reset-status endpoint in MVP. Retry uses the normal POST and creates a new immutable run. If the client loses a POST response, fetch repository history; an existing active run produces 409 on retry and its ID can be followed. The contract does not promise global exactly-once submission or request-id idempotency.

`AnalysisRun` response:

```json
{
  "id": "00000000-0000-4000-8000-000000000001",
  "repositoryId": "00000000-0000-4000-8000-000000000002",
  "status": "RUNNING",
  "stage": "PARSING",
  "createdAt": "2026-09-17T08:00:00Z",
  "startedAt": "2026-09-17T08:00:01Z",
  "finishedAt": null,
  "commitSha": "0123456789abcdef0123456789abcdef01234567",
  "sourceUrl": "https://github.com/example-user/codepulse-fixtures/tree/0123456789abcdef0123456789abcdef01234567",
  "versions": {
    "engine": "0.2.0",
    "parser": "3.28.2",
    "metrics": "java-metrics-v1",
    "graph": "explicit-import-v1",
    "riskPolicy": "structural-v1"
  },
  "configHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "progress": {"completedFiles": 7, "eligibleFiles": 12},
  "warnings": [],
  "error": null
}
```

`commitSha`, `sourceUrl`, `startedAt`, `finishedAt`, and `progress.eligibleFiles` may be null before known. `status ∈ QUEUED,RUNNING,SUCCEEDED,FAILED`; `stage ∈ QUEUED,RESOLVING,DOWNLOADING,INVENTORY,PARSING,LINKING,SCORING,PERSISTING,COMPLETE,FAILED`. Warnings are `{code,message,count?}`; count is nonnegative when present. Error is null or `{code,message,retryable,retryAfterSeconds:integer|null}`. Elapsed time is calculated by the client from timestamps; do not persist an independently drifting duration.

Async execution failures remain a **200 status response with status FAILED**, not a 500 for GET. Codes include `REPOSITORY_UNAVAILABLE`, `SOURCE_IDENTITY_CHANGED`, `UPSTREAM_RATE_LIMITED`, `UPSTREAM_TIMEOUT`, `ARCHIVE_REJECTED`, `SIZE_LIMIT_EXCEEDED`, `NO_ANALYZABLE_JAVA`, `ANALYSIS_TIMEOUT`, `RESULT_LIMIT_EXCEEDED`, `PERSISTENCE_FAILED`, and `INTERRUPTED`. Derive retryability from the code. If a provider supplies a rate-limit reset, include its safe retry delay in the failed run's warning/error metadata.

History “complete” refers to saved CodePulse runs. It does not imply Git commit history was fetched.

## 5. Result APIs

All routes in this section require User auth, run ownership, and `SUCCEEDED`. Other existing owned states return 409 `RESULTS_NOT_READY`, including failed runs. Read the job endpoint for failure details. All file IDs must belong to the run.

| Method and path | Query/request | Success | Specific validation |
|---|---|---|---|
| GET `/analyses/{id}/overview` | `scope?` | 200 `Overview` | scope enum |
| GET `/analyses/{id}/health` | `scope?` | 200 `HealthProfile` | scope enum |
| GET `/analyses/{id}/files` | `page,size,scope?,parseStatus?,pathPrefix?,sort?` | 200 `Page<FileSummary>` | prefix <=200 characters, treated literally; sorts path,ncloc,maxComplexity,score |
| GET `/analyses/{id}/files/{fileId}` | None | 200 `FileDetail` | Same-run file required |
| GET `/analyses/{id}/files/{fileId}/methods` | `page,size,sort?` | 200 `Page<MethodMetric>` | sorts beginLine,complexity,ncloc; parsed file required else 409 `FILE_UNASSESSED` |
| GET `/analyses/{id}/files/{fileId}/findings` | `page,size,ruleCode?` | 200 `Page<Finding>` | known rule or 400; failure findings allowed |
| GET `/analyses/{id}/files/{fileId}/risk` | None | 200 `RiskExplanation` | Returns null score/empty factors for unassessed files |
| GET `/analyses/{id}/dependencies` | `page,size,fileId?,direction?` | 200 `Page<DependencyEdge>` plus `graphMode` | direction INCOMING/OUTGOING/ALL; requires fileId if not ALL |
| GET `/analyses/{id}/packages` | `page,size,scope?` | 200 `Page<PackageSummary>` | Sort packageName ASC; default package string is empty |
| GET `/analyses/{id}/risk` | `page,size,scope?,minPriority?` | 200 `Page<RiskHotspot>` | minPriority LOW/MODERATE/HIGH/VERY_HIGH; default HIGH |
| GET `/analyses/{id}/report` | None | 200 `ReportExport`; attachment | Whole finalized snapshot; maximum serialized size 20 MiB; 422 `EXPORT_LIMIT_EXCEEDED` |

Sort syntax is `field:asc` or `field:desc`; defaults: files `path:asc`, methods `beginLine:asc`. Risk lists always sort priority rank descending, score descending, path ascending, UUID ascending; UNASSESSED files are excluded and surfaced through coverage/file filters. Rate limits still apply to report export. If export is too large, return a clear error before sending response bytes; serialize to a bounded temporary buffer/file first and clean it afterward. No streaming partial JSON success.

### Overview and health

```text
Overview {
  analysisId, commitSha, versions, configHash,
  scope: "PRODUCTION"|"TEST"|"ALL",
  inventory: {
    totalFiles, javaFiles, eligibleFiles, parsedFiles, parseFailedFiles, excludedJavaFiles
  },
  scopedMetrics: {
    eligibleFiles, parsedFiles, parseFailedFiles,
    ncloc, classCount, interfaceCount, enumCount, recordCount, annotationCount,
    anonymousClassCount, methodCount, constructorCount, executableCount,
    todoCount, fixmeCount
  },
  health: HealthProfile,
  topHotspots: RiskHotspot[0..5],
  graphMode: "explicit-import-v1",
  historyStatus: "NOT_COLLECTED",
  warnings: Warning[]
}
HealthProfile {
  analysisId, scope,
  eligibleFiles: integer, parsedFiles: integer, parseFailedFiles: integer,
  parseCoverage: number[0..1]|null,
  assessment: "AVAILABLE"|"PARTIAL"|"UNAVAILABLE",
  medianComplexity: number|null, p90Complexity: integer|null,
  executableCount: integer, highPriorityFiles: integer,
  veryHighPriorityFiles: integer, unassessedFiles: integer,
  complexityHistogram: [{bucket:"1-5"|"6-10"|"11-20"|"21+",count:integer}],
  nclocTotal: integer,
  limitations: string[]
}
```

Inventory is always whole-repository and labeled as such; scopedMetrics/health use the chosen scope. Summed metrics include only parsed files and say so. HighPriorityFiles counts HIGH only; veryHighPriorityFiles counts VERY_HIGH only. Coverage is `parsed/eligible`; null for empty scope. Assessment is UNAVAILABLE when zero parsed, PARTIAL when any eligible parse failure exists, AVAILABLE otherwise. Add the prominent low-coverage warning when coverage <0.9. Median is the middle value (average two middle values for even N); p90 uses nearest rank `ceil(0.9*N)`. Values are null for no executable declarations.

### Files, methods, findings

```text
FileSummary {
  id: UUID, analysisId: UUID, path: string, packageName: string|null,
  sourceScope: "MAIN"|"TEST"|"OTHER_SOURCE",
  eligible: boolean, parseStatus: "PARSED"|"PARSE_FAILED"|"SKIPPED",
  dispositionCode: string|null,
  ncloc: integer|null, maxMethodComplexity: integer|null,
  observedFanIn: integer|null, observedFanOut: integer|null,
  attentionScore: integer[0..100]|null,
  priority: "UNASSESSED"|"LOW"|"MODERATE"|"HIGH"|"VERY_HIGH"
}
FileDetail = FileSummary + {
  contentSha256: string|null, byteSize: integer,
  sourceUrl: string|null,
  metrics: {
    physicalLoc, ncloc, commentLines, blankLines,
    classCount, interfaceCount, enumCount, recordCount, annotationCount,
    anonymousClassCount, methodCount, constructorCount, executableCount,
    lambdaCount, todoCount, fixmeCount, maxMethodComplexity, maxMethodNcloc
  },
  importEvidence: {
    totalImportDeclarations: integer,
    resolvedSingleTypeImports: integer,
    wildcardImports: integer, staticImports: integer,
    ambiguousImports: integer, unresolvedOrExternalImports: integer,
    unresolvedExamples: string[0..20], examplesTruncated: boolean
  }|null,
  risk: RiskExplanation
}
MethodMetric {
  id: UUID, fileId: UUID, ownerLabel: string, signature: string,
  declarationKind: "METHOD"|"CONSTRUCTOR"|"COMPACT_CONSTRUCTOR",
  hasBody: boolean, beginLine: integer>=1, endLine: integer>=beginLine,
  ncloc: integer>=0, complexity: integer>=1|null, sourceUrl: string
}
Finding {
  id: UUID, fileId: UUID, ruleCode: string,
  severity: "INFO"|"WARNING"|"HIGH",
  beginLine: integer|null, endLine: integer|null,
  message: string, evidence: object
}
```

FileDetail metric values are nullable if not measured. An excluded file may have only byte size and path. A parsed file with no methods has max complexity 0; a no-body MethodMetric has complexity null. Do not use JavaScript `value || 0` to erase missing-data distinctions.

Finding rules include `HIGH_METHOD_COMPLEXITY` (complexity >=20), `EXTREME_METHOD_COMPLEXITY` (>=30, emit this instead of HIGH for that method), `LARGE_FILE` (NCLOC >=1000), `VERY_LARGE_FILE` (>=2000, replacing LARGE), `LONG_METHOD` (method NCLOC >=80), `TODO_MARKER`, `FIXME_MARKER`, `PARSE_FAILED`, and `UNRESOLVED_IMPORTS`. A finding's threshold need not equal the start of a smoothly increasing score; explain both. Every threshold is tied to the metric/policy version. Evidence contains typed measurements and line references, not raw repository comment bodies or source code. Count all markers but cap stored findings at 10,000 and include truncation in run warnings/summary.

### Dependency and package DTOs

```text
DependencyEdge {
  id: UUID, sourceFileId: UUID, sourcePath: string,
  targetFileId: UUID, targetPath: string,
  kind: "EXPLICIT_IMPORT",
  evidence: {importedTypes: string[1..20], importLines: integer[], truncated:boolean}
}
PackageSummary {
  packageName: string, parsedFileCount: integer,
  ncloc: integer, methodCount: integer,
  highPriorityFileCount: integer,
  observedIncomingFileEdges: integer,
  observedOutgoingFileEdges: integer
}
```

Package highPriorityFileCount combines HIGH + VERY_HIGH and explicitly says so in the UI. Incoming/outgoing file-edge counts include only cross-package edges. Graph edges still use the full successfully parsed snapshot, even if the displayed file cohort is filtered; this preserves fixed per-file scores. Explain that a TEST file may import a production file. V1 can introduce a separately versioned scope-limited graph if useful.

### Risk DTOs and example

```text
RiskHotspot = FileSummary + {topReasons: string[0..3]}
RiskExplanation {
  fileId: UUID, policyVersion: "structural-v1", graphMode: "explicit-import-v1",
  score: integer[0..100]|null, priority: Priority,
  factors: RiskFactor[0..5], priorityOverrides: string[],
  additionalSignals: [{code:string,count:integer}], limitations: string[]
}
RiskFactor {
  code: "MAX_COMPLEXITY"|"FILE_NCLOC"|"MAX_METHOD_NCLOC"|"OBSERVED_FAN_OUT"|"OBSERVED_FAN_IN",
  rawValue: number, lowThreshold: number, highThreshold: number,
  normalizedValue: number[0..1], weight: number[0..100], contribution: number,
  explanation: string
}
```

```json
{
  "code": "MAX_COMPLEXITY",
  "rawValue": 24,
  "lowThreshold": 10,
  "highThreshold": 30,
  "normalizedValue": 0.7,
  "weight": 50,
  "contribution": 35,
  "explanation": "Maximum executable-method complexity is 24; this contributes 35 points."
}
```

For unassessed files, factors is empty, score null, priority UNASSESSED, and limitations explains why. AdditionalSignals includes TODO/FIXME and “history not collected” is a limitation, not a numeric zero signal.

### Export

```text
ReportExport {
  reportSchemaVersion: "1.0", exportedAt: Instant,
  repository: {provider,providerRepositoryId,owner,name,canonicalUrl},
  run: AnalysisRun, effectiveConfig: object,
  overview: Overview, // scope ALL for export; includes full inventory
  files: FileDetail[], methods: MethodMetric[],
  dependencies: DependencyEdge[], findings: Finding[],
  limitations: string[]
}
```

Header: `Content-Disposition: attachment; filename="codepulse-<analysis UUID>.json"`. No passwords, tokens, owner email, absolute local paths, raw source, or raw parser exception text. IDs may be included for cross-references. Deterministic report comparisons ignore `exportedAt`, generated IDs, and run timestamps. A static sample export must be chosen by the operator and reviewed for publication; ordinary user reports remain private.

## 6. Dashboard and public capabilities

| Method and path | Auth | Request | Success | Errors/constraints |
|---|---|---|---|---|
| GET `/dashboard` | User | None | 200 `Dashboard` | Owner-scoped, bounded recent lists |
| GET `/meta` | Public | None | 200 `Capabilities` | Returns feature flags/limits only, no secrets or allowlist contents |

```text
Dashboard {
  projectCount: integer, activeAnalysisCount: integer,
  succeededAnalysisCount: integer, failedAnalysisCount: integer,
  recentAnalyses: AnalysisRun[0..5], recentProjects: Project[0..5]
}
Capabilities {
  applicationVersion: string,
  registrationEnabled: boolean, analysisEnabled: boolean,
  deploymentMode: "LOCAL"|"DEMO",
  sourcePolicy: "OPERATOR_ALLOWLIST",
  supportedLanguage: "JAVA_21_NO_PREVIEW",
  maximumJavaFiles: integer, maximumArchiveBytes: integer,
  features: string[]
}
```

No public `/users` enumeration or arbitrary dashboard user ID. Health probes are infrastructure endpoints outside this versioned API and reveal only operational status.

## 7. Explicit V1 extensions — implement only a selected feature

These are forward designs, not MVP promises. An unimplemented route is absent (404), and `/meta.features` omits it. Do not create stub “success” data.

| Method and path | Auth / request | Response | Validation/errors |
|---|---|---|---|
| GET `/projects/{projectId}/comparison` | User; `baselineId,currentId,page,size` | 200 `Comparison` | Both succeeded runs of same connected repository; 400 same ID; 404 wrong parent; 409 incomplete run |
| GET `/analyses/{id}/graph` | User; `level=PACKAGE`, `maxNodes=1..100` | 200 `PackageGraph` | 409 results not ready; node cap sets truncated true |
| GET `/analyses/{id}/files/{fileId}/impact` | User; `depth=1..3,limit=1..200` | 200 `ImpactCandidates` | Same-run/owner rules; BFS reverse direction |
| GET `/analyses/{id}/files/{fileId}/activity` | User; no body | 200 `FileActivity` | Enabled history feature; unavailable data returned with coverage status |
| GET `/analyses/{id}/duplicates` | User; `page,size` | 200 `Page<CloneGroup>` | Enabled duplicates feature; 409 result not ready |
| GET `/admin/operations` | Admin; no body | 200 `Operations` | 403 nonadmin; aggregate operational data only |

```text
Comparison {
  baselineId, currentId,
  scoreComparable: boolean, incompatibilityReasons: string[],
  counts: {added:integer,removed:integer,changed:integer,unchanged:integer},
  fileChanges: Page<{
    path:string, change:"ADDED"|"REMOVED"|"CHANGED"|"UNCHANGED",
    baselineFileId:UUID|null, currentFileId:UUID|null,
    baselineScore:integer|null, currentScore:integer|null, scoreDelta:integer|null,
    baselineNcloc:integer|null, currentNcloc:integer|null,
    baselineMaxComplexity:integer|null, currentMaxComplexity:integer|null
  }>
}
PackageGraph {
  graphMode:string, truncated:boolean,
  nodes:[{packageName:string,fileCount:integer}],
  edges:[{sourcePackage:string,targetPackage:string,fileEdgeCount:integer}]
}
ImpactCandidates {
  fileId, graphMode:string, depth:integer, truncated:boolean,
  candidates:[{fileId:UUID,path:string,distance:integer}], limitations:string[]
}
FileActivity {
  fileId, coverage:"COMPLETE"|"PARTIAL"|"UNAVAILABLE",
  reasonCode:string|null, windowStart:Instant, windowEnd:Instant,
  historyPolicyVersion:string, touchingCommitCount:integer|null,
  changedLines:integer|null, lastChangedAt:Instant|null,
  activityScore:integer|null, changeReviewPriority:integer|null
}
CloneGroup {
  id:UUID, algorithmVersion:string, tokenCount:integer,
  occurrences:[{fileId:UUID,path:string,beginLine:integer,endLine:integer}]
}
Operations {
  queuedCount:integer, runningCount:integer,
  failedLast24Hours:integer,
  failuresByCode:[{code:string,count:integer}],
  oldestQueuedAt:Instant|null, analysisEnabled:boolean
}
```

Comparison raw deltas remain available even when scoreComparable=false; scoreDelta is then null. Missing files also have null scoreDelta, not a fabricated improvement to zero. Changed means content hash or measured result differs at the same normalized path; no rename detection. Sort fileChanges by path. Graph node selection is deterministic by file count descending then package name; edges include selected endpoints only and truncation is explicit. Impact candidates sort distance then path. Partial history never receives a complete-window activity score. Clone groups need at least two verified occurrences and never claim semantic equivalence.

## 8. Contract acceptance checks

Every protected operation needs a successful owner test and at least one non-owner test. Every mutation needs validation and CSRF tests. Queries with pagination need boundary/sort tests. Every 409/429 path should return a stable code and actionable UI text. Test JSON nulls for missing data and confirm no entity fields or secrets leak. Generate an OpenAPI file from this agreed contract during implementation if it helps the frontend, then review it against behavior; this document deliberately avoids an unverified framework-specific OpenAPI dependency.
