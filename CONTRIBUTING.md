# Contributing to hvostid

This file is owned jointly by T41 (general contributing guide) and T43
(repo hygiene). The sections below are the T43 portion. T41 will add the
remaining sections (project setup, code style, testing, review process).

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
