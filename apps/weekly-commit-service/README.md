# weekly-commit-service

Spring Boot 3.3 + Java 21 service: REST controllers, state machine, scheduled
jobs (Shedlock-coordinated), RCDO + notification-svc clients. One deployment
(see [../../docs/MEMO.md](../../docs/MEMO.md) decision #1). Postgres 16.4 via
JPA + Flyway. See [../../docs/TECH_STACK.md](../../docs/TECH_STACK.md).

Scaffolded in task group 3. Does not participate in the Yarn/Nx graph — built
with Maven.
