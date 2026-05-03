/**
 * Conventional Commits with a project-specific rule: every subject must
 * carry a task id (TXX) so commits can be traced back to the issue tracker.
 *
 * Format: <type>(<scope>): TXX <subject>
 * Example: feat(listing): T07 add CRUD endpoints
 */
module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [
      2,
      'always',
      ['feat', 'fix', 'docs', 'style', 'refactor', 'test', 'chore', 'ci'],
    ],
    'scope-case': [2, 'always', 'kebab-case'],
    'subject-case': [0],
    'subject-empty': [2, 'never'],
    'header-max-length': [2, 'always', 100],
    'task-id-required': [2, 'always'],
  },
  plugins: [
    {
      rules: {
        'task-id-required': ({ subject }) => {
          if (subject == null) {
            return [false, 'subject is required'];
          }
          const hasTaskId = /^T\d+\b/.test(subject);
          return [
            hasTaskId,
            'subject must start with a task id like T07 (e.g. "T07 add CRUD endpoints")',
          ];
        },
      },
    },
  ],
  ignores: [
    (message) => /^Merge (branch|pull request|remote-tracking branch)\b/.test(message),
    (message) => /^Revert\s/.test(message),
  ],
};
