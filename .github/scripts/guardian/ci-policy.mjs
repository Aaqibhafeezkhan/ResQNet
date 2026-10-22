import { CHECK_RUN_NAME } from './constants.mjs';

/**
 * Path-aware expected-check policy for this repository.
 *
 * The names below are the GitHub Actions job names, not workflow names: that
 * is what appears in the checks API. The policy intentionally mirrors the
 * `paths` filters in `.github/workflows/*.yml`. A change to a workflow trigger
 * or a job name must update this file and its tests in the same review.
 *
 * This is the static floor. Branch-protection and ruleset requirements are
 * added at evaluation time when the token can read them.
 */
export const CI_CHECK_GROUPS = Object.freeze([
  {
    id: 'web',
    paths: ['apps/web/**', '.github/workflows/web.yml'],
    checks: ['typecheck + test + build'],
  },
  {
    id: 'android',
    paths: [
      'apps/android/**',
      'packages/mesh-core/**',
      '.github/workflows/android.yml',
    ],
    checks: ['assemble + unit tests'],
  },
  {
    id: 'android-instrumented',
    paths: [
      'apps/android/**',
      'packages/mesh-core/**',
      '.github/workflows/android-instrumented.yml',
    ],
    checks: ['emulator instrumentation'],
  },
  {
    id: 'backend',
    paths: [
      'services/backend/**',
      'packages/mesh-core/**',
      'pom.xml',
      '.github/workflows/backend.yml',
    ],
    checks: [
      'unit + H2 integration',
      'PostgreSQL migrations and integration',
      'boots against PostgreSQL',
    ],
  },
  {
    id: 'java',
    paths: [
      'packages/mesh-core/**',
      'packages/mesh-simulator/**',
      'scripts/**',
      'pom.xml',
      '.github/workflows/java.yml',
    ],
    checks: ['mesh core + simulator', 'benchmark smoke'],
  },
]);

/**
 * Security checks enforced when present. GitGuardian is not a required CI job
 * for every path, but a reported failure must never be hidden as optional.
 */
export const SECURITY_CHECK_NAMES = Object.freeze(['GitGuardian Security Checks']);

/**
 * Automation checks that are not code CI. Their failures are still surfaced as
 * warnings, but they do not decide whether code readiness is satisfied.
 */
export const AUTOMATION_CHECK_NAMES = Object.freeze(['validate', CHECK_RUN_NAME]);

export function normalizeCheckName(value) {
  return String(value ?? '').trim().toLowerCase();
}

export function pathMatches(file, pattern) {
  const path = String(file ?? '').replace(/\\/g, '/');
  const rule = String(pattern ?? '');
  if (!path || !rule) return false;
  const escaped = rule
    .replace(/[.+^${}()|[\]\\]/g, '\\$&')
    .replace(/\*\*/g, '\u0000')
    .replace(/\*/g, '[^/]*')
    .replace(/\u0000/g, '.*');
  return new RegExp(`^${escaped}$`).test(path);
}

/**
 * Return the expected checks for a changed-file list. Every check carries the
 * policy group(s) that selected it so the summary can explain an absence.
 */
export function expectedChecksForFiles(files = []) {
  const paths = [...new Set((Array.isArray(files) ? files : [])
    .map((item) => (typeof item === 'string' ? item : item?.filename))
    .filter((item) => typeof item === 'string' && item))];
  const selected = new Map();
  const groups = [];
  for (const group of CI_CHECK_GROUPS) {
    if (!paths.some((file) => group.paths.some((pattern) => pathMatches(file, pattern)))) continue;
    groups.push(group.id);
    for (const name of group.checks) {
      const key = normalizeCheckName(name);
      const current = selected.get(key);
      if (current) {
        if (!current.groups.includes(group.id)) current.groups.push(group.id);
      } else {
        selected.set(key, { name, groups: [group.id] });
      }
    }
  }
  return {
    checks: [...selected.values()],
    groups,
  };
}
