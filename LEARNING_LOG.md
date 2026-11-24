# Learning Log

One entry per session, newest at the bottom. AI agents: read the last entry before starting, and append a new one at the end of every session.

## Entry template

```
### Session N: YYYY-MM-DD
**Phase / concept:** …
**Learned:** what was covered, in 2–4 bullets
**Explained back (learner's own words):** short summary of the learner's explanation
**Gaps found:** anything shaky to revisit
**Break-it result:** what failed and how it was fixed
**Code written:** files touched; ✍️ by learner / 🧰 scaffolded by agent
**Recall questions for next session:** 2–3 questions
**Next step:** the next unchecked item in ROADMAP.md
```

---

### Session 0: 2026-10-29 (Refined 2026-11-03)
**Phase / concept:** Planning & Architecture Refinement
**Learned:** Project scope agreed. Settle is a learning-first project: every concept gets built, broken and explained back.
**Decisions:**
- Java 21 + Spring Boot for domain services; Go for the saga orchestrator; AI only in human-in-the-loop fraud case review; evals live inside each AI project.
- **Core-First MVP:** Build and chaos-test the 3 core services (`account-service`, `payment-orchestrator` + `rail-stub`, `risk-service`) first to hit the Chaos Test & Invariant Checker by Phase 3 before peripheral plumbing (`notification-service`, `api-gateway`, `identity-service`).
- **Flaky External Bank Rail (`rail-stub`):** Added a simulated external bank rail that injects timeouts and `PENDING` async webhook settlements so the distributed saga is genuinely necessary (cannot be solved by a single Postgres transaction) and handles ambiguous timeout states.
- **Defense Metrics:** Track explicit counters (`fencing_conflicts_blocked_total`, `idempotency_duplicates_caught_total`, `expired_leases_recovered_total`, `rail_ambiguous_timeouts_resolved_total`) during chaos runs to prove how many bugs the safety nets caught.
**Next step:** Phase 0, "Monorepo layout and build tools"

### Session 1: 2026-11-04
**Phase / concept:** Phase 0, Monorepo layout and build tools
**Learned:**
- Maven multi-module: aggregation (`<modules>`, parent points down) vs inheritance (`<parent>`, child points up)
- `dependencyManagement` (the "menu": versions only) vs `dependencies` (the "order": forced onto every child); BOM import pins versions together
- Lifecycle phases run every earlier phase; plugin goals do the work (`mvn test` compiles + tests, no jar)
- Go modules: `go.mod` / `go.sum`, one module with two binaries under `cmd/`, MVS vs Maven's "nearest wins"
**Break-it result:** Moved `spring-boot-starter-data-redis` into the parent's `<dependencies>`: `mvn dependency:tree` showed Redis + Lettuce appearing in account-service without touching its POM. Reverted.
**Code written:** 🧰 root `pom.xml`, `services/account-service/pom.xml` + `AccountServiceApplication.java`, `.gitignore`, Go module `services/payment-orchestrator` (`cmd/orchestrator`, `cmd/rail-stub`); `git init` (nothing committed)
**Process change:** Switched to lite mode (ROADMAP.md section 3): build first, no blocking quizzes, learner writes core logic, target full project in ~1 week.
**Environment:** JDK 24 compiling with `release=21`; Maven 3.9.11; Go 1.27.3 installed; `buf` not installed yet
**Next step:** Phase 0, "Docker and Docker Compose"

### Session 2: 2026-11-23
**Phase / concept:** Phase 0 completed (Docker Compose, Protobuf/gRPC + buf, CI + gitleaks)
**Learned:**
- Docker volumes (`pgdata:/var/lib/postgresql`), internal DNS (`postgres:5432`, `kafka:9092`, `redis:6379` vs `localhost`), two Kafka listeners (`INTERNAL` vs `EXTERNAL`), why Postgres/Redis/Kafka vs S3.
- Enterprise payment contract naming (`CreateHold`, `CaptureHold`, `ReleaseHold`, `source_account_id`, `destination_account_id`, `amount`, `currency`), Protobuf wire tag numbers (`= 1`, `= 2`), code generation with `buf generate`.
- Local `.git/hooks/pre-commit` with `gitleaks` vs GitHub Actions `.github/workflows/ci.yml`.
**Code written:** ✍️ `proto/settle/account/v1/account.proto`, `deploy/docker-compose.yml` (volumes + UI containers); 🧰 `proto/buf.yaml`, `proto/buf.gen.yaml`, `.github/workflows/ci.yml`, `.git/hooks/pre-commit`, `docs/ARCHITECTURE.md`, `docs/FRAUD_ENGINE_RND.md`, `notes/01..04`.
**Next step:** Phase 1, "The ledger (`account-service`, Java)"

### Session 3: 2026-11-24
**Phase / concept:** Phase 1 completed (`account-service`: Double-entry ledger, holds, locking, idempotency, gRPC server, Testcontainers)
**Learned:**
- Enterprise LLD layered architecture (`controller` -> `dto` (`record`) -> `service`/`service/impl` -> `dao`/`dao/impl` -> `repository` -> `entity`).
- Dependency Injection (IoC container wiring beans via constructors), POJO vs DTO vs `@Entity`.
- 4-table ledger schema (`accounts`, `holds` with `PENDING`/`COMPLETED`/`CANCELLED`, `ledger_entries` with `DEBIT`/`CREDIT`, `idempotency_records`).
- Pessimistic locking (`SELECT ... FOR UPDATE`), deadlock prevention via alphabetical account ID lock ordering, and checking idempotency after acquiring the row lock.
- Java 21 virtual threads on the gRPC server + Testcontainers `@ServiceConnection` integration tests.
**Break-it result:** Fired 50 parallel duplicate `createHold` requests with the same `idempotencyKey` and 20 simultaneous cross-transfers (`A->B` and `B->A`): 1 hold created, 0 deadlocks, 0 paise lost.
**Code written:** ✍️ `V1__init_ledger.sql`, `AccountServiceImpl.java`; 🧰 Entities, Repositories, DAO, DTOs, `AccountGrpcController.java`, `GrpcServerConfig.java`, `AccountServiceIntegrationTest.java`, `notes/05..12`.
**Next step:** Phase 2, "The saga engine + flaky external rail (`payment-orchestrator`, Go)"
