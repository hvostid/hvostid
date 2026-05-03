**English** | [Русский](./CONTRIBUTING.ru.md)

# Contributing to hvostid

Thanks for working on hvostid. Read this end to end the first time
through and skim it before you open a PR. The intent is that the
checks here match what reviewers actually look at, so following them
makes review fast.

## Contents

- [Project setup](#project-setup)
- [Workflow](#workflow)
- [Code style](#code-style)
- [Testing](#testing)
- [Review process](#review-process)

## Project setup

After cloning, install the hook tooling once:

```bash
npm install                    # commitlint + husky at the repo root
npm install --prefix frontend  # eslint + prettier + lint-staged
cp .env.example .env
```

For day-to-day development, see:

- [`README.md`](./README.md#quick-start) -- end-to-end Compose start.
- [`README.md`](./README.md#development) -- IDE workflow, Gradle and
  Vite commands, migration handling.
- Per-service `README.md` files -- responsibilities, endpoints, env
  vars, dependencies on other services.

## Workflow

### Branch naming

All work happens on a branch named after its task:

```
feature/TXX-short-name   # new functionality
fix/TXX-short-name       # bug fix on something already merged
```

`TXX` is the task id (for example `T07`); `short-name` is a kebab-case
summary of the work. Every change must trace back to a task -- if a fix
does not have one yet, open a ticket first and use its id. The same id
is required in the commit subject (see below), so branch and commit
stay in sync.

### Commit messages -- Conventional Commits

Commits follow [Conventional Commits](https://www.conventionalcommits.org/)
with one project-specific addition: every commit subject must reference
its task id.

```
<type>(<scope>): TXX <subject>
```

* `<type>` -- one of `feat`, `fix`, `docs`, `style`, `refactor`, `test`,
  `chore`, `ci`.
* `<scope>` -- optional, kebab-case. Typically, the affected service or
  package: `auth`, `listing`, `gateway`, `frontend`, `docker`.
* `TXX` -- task id; the commit is rejected if missing.
* `<subject>` -- short imperative summary, no trailing period.

Examples:

```
feat(listing): T07 add CRUD endpoints
fix(auth): T12 reject expired refresh tokens
ci: T43 enforce conventional commits via commitlint
```

The header is capped at 100 characters. Body and footer follow the
standard Conventional Commits format and are optional.

### Local enforcement

Commit messages are validated locally through a Husky `commit-msg` hook
that runs `commitlint`. The first time you clone the repo, run:

```sh
npm install
npm install --prefix frontend
```

The first command installs root tooling (commitlint, husky) and wires up
Husky via the `prepare` script. The second installs frontend tooling
(eslint, prettier, lint-staged) so the `pre-commit` hook can run on
staged frontend files.

After this, both hooks are active for every subsequent commit:

* `commit-msg` -- validates the commit message against `commitlint.config.js`.
* `pre-commit` -- runs `lint-staged` from `frontend/`, which applies
  `eslint --fix` and `prettier --write` to staged JS/JSX/CSS/HTML/JSON
  files. Files outside `frontend/` are ignored by this hook.

Allowed commit types and the task-id rule are defined in
[`commitlint.config.js`](./commitlint.config.js).

### Code formatting

* **Backend (Java).** Spotless is wired into every Gradle module with
  the [palantir-java-format](https://github.com/palantir/palantir-java-format)
  formatter. `./gradlew spotlessCheck` runs as part of `./gradlew check`
  (and therefore CI). Run `./gradlew spotlessApply` to fix violations
  locally.
* **Frontend (JS/JSX).** ESLint and Prettier are configured in
  `frontend/`. Use `npm run lint` / `npm run lint:fix` and
  `npm run format` / `npm run format:check` from inside `frontend/`.
  The pre-commit hook fixes staged files automatically.

Repository-wide editor defaults (UTF-8, LF line endings, 4-space indent)
are pinned in [`.editorconfig`](./.editorconfig).

### Bypassing hooks

In rare cases (typos in WIP commits, emergency hotfixes), you can skip
the hooks with `git commit --no-verify`. Do not use this routinely --
formatting and lint failures should be fixed before commit, not pushed
to CI.

### Reviewers

Reviewers are assigned automatically based on
[`.github/CODEOWNERS`](./.github/CODEOWNERS). You do not need to add
reviewers manually; GitHub picks them from the rule that matches the
files you touched.

### Pull requests

Open the PR against `main`. The PR template
([`.github/pull_request_template.md`](./.github/pull_request_template.md))
asks for the linked task, what changed, how to test, and a checklist.
Frontend PRs additionally require screenshots of the affected screens.

## Code style

Formatting is handled by tools (Spotless on the backend, ESLint +
Prettier on the frontend) and is not up for debate -- if `./gradlew
spotlessCheck` and `npm run lint` are green, formatting is fine. The
conventions below are about things tools do not catch.

### Backend (Java)

* **Use shared constants.** HTTP header names and role names live in
  [`common`](./common/src/main/java/ru/hvostid/common). Use
  `SecurityHeaders.USER_ID` / `USER_ROLES` and `UserRole.*` rather than
  string literals such as `"X-User-Id"` or `"BUYER"`. Adding a new
  shared constant is preferable to scattering literals.
* **Comments explain *why*, not *what*.** Code that needs a paragraph
  of narration usually needs to be rewritten instead. The bar for a
  comment is "a future reader would be surprised without it" -- a
  hidden constraint, a workaround for a specific bug, an invariant
  that is not obvious from the types.
* **Apply review feedback project-wide.** When a reviewer flags
  something in one file, fix the same pattern in sibling files in the
  same PR. A repeated issue is a single comment, not five.
* **DTOs are records.** Request and response DTOs are Java records
  with Bean Validation annotations. Keep the controller boundary thin;
  push business logic into services.
* **Avoid placeholder values.** If an API parameter is genuinely
  unused, drop it rather than passing a dummy value. The tooling will
  flag unused method parameters; do not silence the warning by
  inventing data.
* **ASCII English only.** Source, comments, and commit messages stay
  in ASCII English. No em dashes, smart quotes, ellipses, or
  non-English text.

### Frontend (JS/JSX)

* **Functional components only,** with React hooks. Class components
  are not used in this codebase.
* **Auth context is the source of truth** for the current user. Do not
  read tokens directly from `localStorage` outside `AuthContext` and
  the axios client.
* **Routes are protected** through `<ProtectedRoute>`. Add new private
  pages by wrapping them, not by re-implementing the redirect.
* Tailwind utility classes over hand-rolled CSS. Co-locate small bits
  of CSS only when Tailwind cannot express them.

### Configuration

* **Environment variables are documented** in the affected service's
  `application.yml` (with a sensible default for local dev) and listed
  in the per-service README. Add new entries to
  [`.env.example`](./.env.example) if they need to be set in
  Compose.
* **Secrets do not live in the repo.** SonarQube and registry tokens
  are GitHub Actions secrets; DB and MinIO credentials in
  `.env.example` are *defaults for local dev only*.

## Testing

Tests are required for behavior changes. For pure refactors, the
existing tests are usually sufficient -- but they must still pass
after the change.

### What we use

| Layer        | Tooling                                                                                                                                |
|--------------|----------------------------------------------------------------------------------------------------------------------------------------|
| Unit         | JUnit 5, Mockito, AssertJ                                                                                                              |
| Web layer    | `@WebMvcTest` slices for controller logic, `MockMvc` assertions                                                                        |
| Integration  | Testcontainers PostgreSQL via [`AbstractPostgresContainerTest`](./common/src/testFixtures/java/ru/hvostid/common/testfixtures/AbstractPostgresContainerTest.java) |
| Coverage     | JaCoCo (`<module>/build/reports/jacoco/test/jacocoTestReport.xml`)                                                                     |
| Load         | k6 scripts in [`k6/`](./k6) (catalog search, listing create, match score)                                                              |

### Conventions

* **Test class names mirror production class names** with a `Test`
  suffix (`AuthService` -> `AuthServiceTest`).
* **Use the shared Postgres fixture** for any test that needs a real
  database; avoid spinning up your own container.
* **Do not mock what you own.** Real services in the same module are
  exercised end to end; only HTTP boundaries (introspection client,
  inter-service calls) are mocked.
* **Frontend tests** are not yet wired up (tracked in T22). Until
  then, manual verification is documented in the PR's "How to test"
  section.

### Running tests

```bash
./gradlew build                       # all modules, includes Spotless
./gradlew :auth-service:test          # one module
./gradlew :auth-service:test --tests AuthServiceTest
k6 run k6/search-listings.js          # load test against a running stack
```

## Review process

### As an author

Before requesting review:

* The PR template's checklist is filled in.
* `./gradlew build` and the frontend `npm run build` pass locally.
* New behavior has tests; the PR description explains how to verify
  manually.
* The branch is rebased (or merged) onto current `main`. Resolve
  conflicts on your branch, not in the merge commit.
* The diff is what you actually mean to change -- no stray formatter
  output from a tool the rest of the project does not use, no
  unrelated refactor mixed into a feature PR.

If the PR is large, leave a short review-guide comment on the diff
pointing reviewers at the right starting file.

### As a reviewer

Reviewers are assigned automatically by
[CODEOWNERS](./.github/CODEOWNERS); you do not need to ask. When
reviewing, focus on:

* **Correctness.** Does it do the thing the ticket says? Does it break
  something documented elsewhere?
* **Tests.** Do new code paths have tests, and do those tests
  meaningfully exercise the change?
* **Security boundaries.** Anything that touches auth, the gateway
  filters, or `@PreAuthorize` deserves a careful read.
* **Public contracts.** OpenAPI changes, DTO field renames, env-var
  changes, and migration files are hard to take back.
* **Consistency.** If a comment applies to one place, it usually
  applies to several -- ask the author to fix the rest, do not relitigate.

Approve when the change is correct and adequately tested, even if you
would have written it differently. Style preferences that are not in
this document or the formatter belong as suggestions, not blockers.

### Merging

PRs are merged with a merge commit (default GitHub merge), which
preserves the per-commit history. Squash-merging is reserved for
trivial fix PRs where the per-commit history adds no value.
