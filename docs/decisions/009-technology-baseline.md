# ADR-009: Initial technology baseline

**Status:** Accepted

## Context

EventLab should build on the developer's existing Java experience while adding distributed-system and Azure skills. Spring Boot 4 is current, but the agreed project direction and much of the target employment ecosystem use Spring Boot 3. Reproducible builds require explicit versions and toolchain checks.

## Decision

Start with Java 21, Maven 3.9, Spring Boot 3.5.16, React 19.2.8, TypeScript 7.0.2, and Vite 8.2.1. Commit Maven Wrapper and the npm lockfile. Enforce Java 21 and Maven 3.9 through the build.

Use supported stable dependency lines and upgrade deliberately through reviewed dependency changes. A future Spring Boot 4 migration is a maintenance decision, not an MVP learning objective.

## Consequences

- The project remains aligned with its declared Java 21 and Spring Boot 3 scope.
- Local and CI builds use pinned dependency graphs.
- Java or Maven drift fails early.
- Framework upgrades remain separate from distributed-systems milestones.
