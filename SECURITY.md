# Security policy

## Supported version

Security fixes are applied to the `main` branch. EventLab is a portfolio laboratory, not a production service, and its disposable Azure environment is intentionally short-lived.

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting feature for this repository. Do not include credentials, access tokens, personal data, or an exploit against the live demonstration in a public issue. Include the affected component, reproduction steps, impact, and a suggested mitigation when possible.

## Public-lab boundaries

The live lab accepts only synthetic scenario data. Do not submit personal, confidential, regulated, or production information. Requests are bounded by body-size, rate, concurrency, and workload ceilings. The public interface has no administrative credentials and cannot provision or destroy Azure resources.

Dependencies, containers, source, secrets, and Terraform are scanned in GitHub Actions. Deployment uses short-lived GitHub OIDC tokens, immutable image digests, private database networking, managed identities, and an automatically expiring Azure environment.
