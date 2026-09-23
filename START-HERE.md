# CodePulse specification pack

**Recommendation:** build a Java-only, explainable codebase analyzer first. Keep the core deterministic and free of AI API dependencies. Plan approximately **140–195 focused hours** for the portfolio MVP, with an earlier local analyzer checkpoint suitable for demonstrating work in progress.

This pack specifies the project. It does **not** contain an implemented application or claim that one was deployed.

## Read and use the files in this order

1. [Engineering blueprint](docs/spec/CodePulse-Engineering-Blueprint.md) — critical evaluation, releases, architecture, analysis/risk definitions, security, jobs, frontend, tests, Docker, verified hosting constraints, learning roadmap, 84 interview questions, and resume wording. Read sections 1–5 before beginning Phase 0; consult the rest as needed.
2. [Claude build playbook](Claude-Build-Playbook.md) — 13 phase prompts, four optional extension prompts, three reusable prompts, and an implementation-state template. Core MVP is phases 0–11; phase 12 is optional comparison work.
3. [CLAUDE.md](CLAUDE.md) — copy into the root of your implementation repository to establish the mentoring/implementation rules.
4. [API contract](docs/spec/API-Contract.md) — endpoint-specific authentication, input/output schemas, validation, errors, and explicitly deferred extensions.
5. [Database schema](docs/spec/Database-Schema.sql) — fields, types, constraints, relationships, and indexes. Introduce it through staged migrations; do not generate every entity on day one.

Copy the blueprint, API contract, and schema into `docs/spec/` in the implementation repository. Create `docs/implementation-state.md` from the playbook template, then paste **only the Phase 0 prompt** into Claude. Review, run, test, and explain each slice before continuing.

## The four scope decisions to preserve

- Java 21 source analysis with JavaParser; explicit import relationships rather than a complete runtime graph.
- Versioned attention scores with factor evidence, parse coverage, and visible unknowns.
- One Spring Boot application, PostgreSQL, session authentication, and one durable scheduled worker. JWT, Redis, brokers, private repositories, and broad language support are deferred.
- Full functionality locally; a clearly labeled static sample is the reliable ₹0 public demo. Free live hosting is optional and limited. Unrestricted internet submissions require future worker isolation.

## Coverage of the original 23 requirements

| Requested area | Location |
|---|---|
| Product definition | Blueprint 1–2 |
| Complete feature set and difficulty | Blueprint 3, 18, 22 |
| Roles and user flows | Blueprint 4 |
| System/backend architecture | Blueprint 5–6, 23 |
| Database and ER diagram | Blueprint 7 + Database-Schema.sql |
| REST API | Blueprint 8 + API-Contract.md |
| Analysis engine | Blueprint 9 |
| Risk engine | Blueprint 10 |
| Security | Blueprint 11 |
| Background processing | Blueprint 12 |
| Frontend | Blueprint 13 |
| Testing | Blueprint 14 |
| Docker | Blueprint 15 |
| Deployment | Blueprint 16 |
| Git/GitHub strategy | Blueprint 17 |
| Learning roadmap | Blueprint 18 |
| Claude prompts and working style | Blueprint 19 + playbook + CLAUDE.md |
| Interview preparation | Blueprint 20 |
| Resume/README/LinkedIn | Blueprint 21 |
| Scope control | Blueprint 22 |

## Validation performed on this pack

- Markdown parsed; internal file links and fenced JSON/YAML examples checked.
- Verified sequential numbering of 84 interview questions and presence of all 13 phase prompts.
- Recomputed the five-factor worked example: 59 points.
- Applied the reference SQL to an isolated local PostgreSQL 14.18 instance and exercised active-run uniqueness, cross-run edge rejection, missing-score/parse-state constraints, executable complexity validation, successful publication state, and delete cascades.

The recommended implementation database is PostgreSQL 17; these document checks do not substitute for the application's future PostgreSQL 17 integration tests. The application, Dockerfiles, benchmarks, and deployment are still implementation work. Provider facts and framework recommendations are linked to official sources inside the blueprint, checked on 17 September 2026.
