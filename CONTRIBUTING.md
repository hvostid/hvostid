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
```

at the repo root. The `prepare` script wires up Husky automatically, so
the hook is active for every subsequent commit. Invalid messages are
rejected before the commit is created.

Allowed types and the task-id rule are defined in
[`commitlint.config.js`](./commitlint.config.js).

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
