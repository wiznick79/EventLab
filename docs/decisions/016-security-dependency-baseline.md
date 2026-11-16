# ADR-016: Security dependency baseline

**Status:** Accepted

## Context

Image scans found fixable Spring MVC and Jackson vulnerabilities in the Spring Boot 3.5.16 dependency graph.
The Spring Framework 6.2 fixes for CVE-2026-47884 and CVE-2026-47890 are available only through enterprise support.
The public fixes are in Spring Framework 7.0.9 and later.

## Decision

Use Spring Boot 4.0.9, which manages Spring Framework 7.0.10 and Tomcat 11. Keep Java 21 and Maven 3.9.
Remove the old Netty and Tomcat overrides so Boot manages compatible versions.

Keep the existing Jackson 2 message and HTTP representation through Boot's `spring-boot-jackson2` compatibility
module and `spring.http.converters.preferred-json-mapper=jackson2`. Pin the Jackson 2 BOM to 2.21.8 for security
fixes, including dependencies used by the Azure SDK. A future Jackson 3 migration must preserve persisted events
and API compatibility; the Jackson 2 compatibility module is deprecated upstream.

Declare Boot 4's Flyway, REST client, Web MVC test, and OpenTelemetry tracing modules explicitly. Use the new
OTLP tracing endpoint property while retaining the existing `OTEL_TRACES_ENDPOINT` environment variable.

Upgrade the Azure Service Bus SDK to 7.18.1 and Azure Identity to 1.18.7. Explicitly manage proton-j at 0.35.0
to fix CVE-2026-66274: the resolved Azure SDK graph still selects 0.34.1 without this override.

Upgrade Vite to 8.3.8 and Vitest to 4.1.12 and regenerate the npm lockfile to resolve npm audit findings.

Refresh pinned Java 21 and Node 24 base digests, Nginx 1.31.6, Grafana 13.3.1, and Tempo 2.10.10. Keep major
runtime versions aligned with CI. Apply Alpine security package updates to the service, frontend, Grafana,
and telemetry gateway images. In CI, use the same run-specific cache key for scanning and publishing.

Keep only Grafana's signed Tempo datasource plugin, which is the only datasource used by the lab.
Disable plugin preinstallation and automatic updates so runtime plugins match the scanned image.
Local Compose builds this same Grafana image.

## Consequences

- This supersedes the Spring Boot 3 portion of ADR-009 while retaining the Java 21 toolchain.
- Context, persistence, JSON API, and broker integration checks must pass after the framework upgrade.
- Container scans must inspect the rebuilt images; updating a tag alone does not establish that an image is clean.
- Security findings are fixed through dependency and package updates, without adding vulnerability suppressions.

## References

- [Spring advisory CVE-2026-47884](https://spring.io/security/cve-2026-47884/)
- [Spring advisory CVE-2026-47890](https://spring.io/security/cve-2026-47890/)
- [Spring Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
