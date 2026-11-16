# Dependency security validation

Validated on 2026-11-16. The framework migration is explained in
[ADR-016](decisions/016-security-dependency-baseline.md).

## Verification

- Maven verification: 40 tests pass, including application contexts, Flyway migrations, JSON APIs, and the
  fulfilment unsupported-contract quarantine test. The existing coverage threshold remains unchanged.
- Frontend: 25 tests pass; the production build succeeds; `npm audit` reports zero vulnerabilities.
- Real PostgreSQL and Service Bus emulator: happy-path and duplicate-payment-result workflows both reach
  the backend evidence assessment `PROVED` with the patched Azure SDK and proton-j 0.35.0.
- Tempo 2.10.10 receives traces from the upgraded services.
- Grafana 13.3.1 starts successfully, provisions the signed Tempo datasource, and executes a TraceQL query.
  Unused bundled datasource plugins are removed, and automatic plugin installs and updates are disabled.
- Docker Compose configuration validates, and the modified XML, Java, JSON, and YAML files are formatted.

## Container scan results

Trivy 0.76.1 scanned exported Linux amd64 images with its current database, `--scanners vuln`,
`--ignore-unfixed`, and `--severity HIGH,CRITICAL`. These results concern fixable HIGH/CRITICAL findings,
not an assertion that no lower-severity or undisclosed vulnerabilities exist.

| Rebuilt image | HIGH findings | CRITICAL findings |
| --- | ---: | ---: |
| Fulfilment service | 0 | 0 |
| Lab console | 0 | 0 |
| Frontend | 0 | 0 |
| Telemetry gateway | 0 | 0 |
| Grafana 13.3.1 (Tempo plugin only) | 3 | 0 |
| Tempo 2.10.10 | 2 | 0 |

The fulfilment image exercises the shared backend dependency graph; the console was scanned separately
because it additionally includes Boot's REST client module.

## Remaining upstream findings

The latest stable upstream images checked still bundle the following flagged dependencies. Updating OS
packages does not replace these compiled Go dependencies. They remain outstanding and require patched
upstream binaries or a separately reviewed source rebuild.

| Image | Bundled component | Reported CVEs |
| --- | --- | --- |
| Tempo | go-jose v4.1.4 | CVE-2026-116587 (fixed in 4.1.6) |
| Tempo | golang.org/x/net v0.60.0 | CVE-2026-78661 (fixed in 0.62.0) |
| Grafana Tempo datasource plugin | golang.org/x/net v0.61.0 | CVE-2026-78661 (fixed in 0.62.0) |
| Grafana Tempo datasource plugin | github.com/grafana/tempo pseudo-version | CVE-2026-21728, CVE-2026-28377 |

No new vulnerability suppressions or severity exemptions were added. The existing CRITICAL-only image
gates for Grafana and Tempo remain in place; passing those gates does not resolve the HIGH findings above.
Recheck these images when upstream publishes new maintenance releases.

Removing unused Grafana datasource plugins reduced its findings from 20 to 3 without changing plugin
signatures or enabling unsigned plugins. Local Compose uses this same minimized image.
