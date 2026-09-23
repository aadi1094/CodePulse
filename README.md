# CodePulse

A Java-focused, explainable codebase analyzer. It analyzes small Java repositories and produces deterministic structural reports (files, types, methods, complexity, explicit internal import relationships, parse coverage, and review-priority signals with evidence) to help developers prioritize code review.

**Status: Phase 2 in progress (AST metrics). Local, plain-Java engine only; no web app yet.**

This repository currently contains the specification pack and repository scaffolding only. There is no implemented application, no deployment, and no benchmark. Features are listed here only when they exist and have been verified.

## What is implemented

- Phase 0 learning bridge: `FileMetrics` record and a package summary (hand-written data).
- Phase 1 source inventory: lists files under a trusted local folder, skips and counts excluded directories (`target`, `.git`, `node_modules`, ...), never follows symlinks, counts physical lines of `.java` files with a 256 KiB per-file limit, and labels Java files MAIN / TEST / OTHER_SOURCE.
- Phase 2 (in progress): parses one Java 21 file with JavaParser 3.28.2 into PARSED or PARSE_FAILED with sanitized diagnostics. No metrics from the tree yet.

See [docs/implementation-state.md](docs/implementation-state.md) for the current slice.

Build and run it:

```
cd backend
./mvnw test
java -cp target/classes dev.codepulse.InventoryMain .
```

## Roadmap (planned, not implemented)

Phase 0 Java bridge → 1 source inventory → 2 AST metrics and complexity (JavaParser) → 3 import graph and transparent priorities → 4 Spring Boot API → 5 PostgreSQL, JPA, Flyway → 6 sessions, CSRF, ownership → 7 GitHub snapshot acquisition → 8 durable background jobs → 9 React report UI and JSON export → 10 containers and CI → 11 release. Phase 12 (snapshot comparison) is optional.

## Documents

- [START-HERE.md](START-HERE.md) — reading order for the specification pack
- [docs/spec/CodePulse-Engineering-Blueprint.md](docs/spec/CodePulse-Engineering-Blueprint.md) — product and architecture authority
- [docs/spec/API-Contract.md](docs/spec/API-Contract.md) — planned REST contract
- [docs/spec/Database-Schema.sql](docs/spec/Database-Schema.sql) — reference destination schema, introduced through staged migrations
- [Claude-Build-Playbook.md](Claude-Build-Playbook.md) — phase prompts and working method
- [CLAUDE.md](CLAUDE.md) — mentoring and implementation rules

## Stack (planned)

Java 21, Maven Wrapper, Spring Boot, Spring Security (cookie sessions + CSRF), Spring Data JPA/Hibernate, PostgreSQL 17, Flyway, JavaParser, JUnit Jupiter, Testcontainers, React + TypeScript + Vite, Docker Compose, GitHub Actions. No AI APIs, no Redis, no message broker, no microservices.

## Honest scope

CodePulse builds an *explicit internal import graph*, not a complete call or dependency-injection graph. It does not predict bugs or produce a validated code-health percentage. See Blueprint sections 9 and 10 for the metric and risk definitions.
