# Security Policy

**English** | [Русский](./SECURITY.ru.md)

## Automated Security Scanning

HvostID uses three automated scanners to detect vulnerabilities, plus
an optional vulnerability management platform to aggregate and triage
their findings.

| Tool                       | Scope                                            | Trigger                                                              | Failure threshold      |
|----------------------------|--------------------------------------------------|----------------------------------------------------------------------|------------------------|
| **Dependabot**             | Gradle, npm, Docker, GitHub Actions dependencies | Weekly                                                               | Opens PR               |
| **OWASP Dependency Check** | Java + npm transitive dependency CVEs            | Daily on cron and on dependency-declaration changes merged to `main` | CVSS >= 9.0 (critical) |
| **Trivy**                  | Docker image layers and OS packages              | Every merge to `main`                                                | CRITICAL severity      |

Findings flow to two destinations:

- **GitHub Code Scanning** - both Trivy and OWASP Dependency Check
  upload SARIF reports that surface in the repository
  **Security -> Code scanning** tab. This requires GitHub Advanced
  Security; on private repositories without GHAS the SARIF upload
  steps skip themselves automatically.
- **DefectDojo** (optional) - when the `DEFECTDOJO_URL` and
  `DEFECTDOJO_TOKEN` repository secrets are set, the same Trivy and
  OWASP Dependency Check runs reimport their JSON/XML reports into a
  self-hosted DefectDojo instance. DefectDojo deduplicates findings
  across scanners, tracks accept-risk decisions and provides a
  unified vulnerability dashboard. When the URL secret is empty, the
  reimport steps skip themselves without failing the workflow.

## Suppressing False Positives

Known false positives for OWASP Dependency Check are documented in
[`dependency-check-suppressions.xml`](./dependency-check-suppressions.xml).
Each suppression entry must include a `<notes>` element explaining why
the finding is not applicable.

To add a suppression:

1. Identify the CVE and confirm it is a false positive (wrong CPE,
   unexposed code path, etc.).
2. Add an entry to `dependency-check-suppressions.xml`:
   ```xml
   <suppress>
       <notes>CVE-XXXX-XXXXX does not apply because ...</notes>
       <cve>CVE-XXXX-XXXXX</cve>
   </suppress>
   ```
3. Open a PR with the suppression and the justification in the PR
   description. The Tech Lead must approve it.

## Reporting a Vulnerability

If you discover a security vulnerability in HvostID, please **do not
open a public GitHub issue**.

**Steps:**

1. Open a [GitHub Security Advisory](https://github.com/hvostid/hvostid/security/advisories/new)
   in this repository (private by default).
2. Describe the vulnerability: affected component, reproduction steps,
   potential impact.
3. The Tech Lead will acknowledge the report within **2 business days**
   and provide an initial assessment within **5 business days**.

## Response Process

| Step                         | Owner                 | SLA                                                                             |
|------------------------------|-----------------------|---------------------------------------------------------------------------------|
| Acknowledge report           | Tech Lead             | 2 business days                                                                 |
| Assess severity (CVSS score) | Tech Lead             | 5 business days                                                                 |
| Develop and test fix         | Responsible developer | Depends on severity: Critical - 3 days, High - 7 days, Medium/Low - next sprint |
| Deploy fix to `main`         | Tech Lead             | After fix is reviewed and merged                                                |
| Close advisory               | Tech Lead             | After deploy                                                                    |

## Dependency Update Policy

- **Dependabot PRs** for patch and minor updates are grouped and should
  be reviewed and merged within one week.
- **Major version updates** are handled manually -- a dedicated task is
  created in the backlog.
- If a Dependabot PR fixes a known CVE, it is treated as a security fix
  and merged with priority.
