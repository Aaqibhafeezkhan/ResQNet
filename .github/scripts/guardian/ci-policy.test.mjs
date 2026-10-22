import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  expectedChecksForFiles,
  pathMatches,
} from './ci-policy.mjs';

test('path matching supports exact files and double-star prefixes', () => {
  assert.equal(pathMatches('apps/web/src/a.ts', 'apps/web/**'), true);
  assert.equal(pathMatches('apps/web', 'apps/web/**'), false);
  assert.equal(pathMatches('.github/workflows/web.yml', '.github/workflows/web.yml'), true);
  assert.equal(pathMatches('apps/web/src/a.ts', 'apps/android/**'), false);
});

test('web changes require web CI only once', () => {
  const result = expectedChecksForFiles([
    { filename: 'apps/web/src/a.ts' },
    { filename: 'apps/web/src/b.ts' },
  ]);
  assert.deepEqual(result.groups, ['web']);
  assert.deepEqual(result.checks.map((check) => check.name), ['typecheck + test + build']);
});

test('Android and mesh-core changes require Android, instrumented, backend, and Java checks', () => {
  const result = expectedChecksForFiles([{ filename: 'packages/mesh-core/src/main/java/App.java' }]);
  assert.deepEqual(new Set(result.groups), new Set(['android', 'android-instrumented', 'backend', 'java']));
  assert.ok(result.checks.some((check) => check.name === 'assemble + unit tests'));
  assert.ok(result.checks.some((check) => check.name === 'emulator instrumentation'));
  assert.ok(result.checks.some((check) => check.name === 'mesh core + simulator'));
  assert.ok(result.checks.some((check) => check.name === 'boots against PostgreSQL'));
});

test('backend changes require backend CI', () => {
  const result = expectedChecksForFiles([{ filename: 'services/backend/src/main/java/App.java' }]);
  assert.deepEqual(result.groups, ['backend']);
  assert.deepEqual(result.checks.map((check) => check.name), [
    'unit + H2 integration',
    'PostgreSQL migrations and integration',
    'boots against PostgreSQL',
  ]);
});

test('Java simulator and workflow changes require Java CI', () => {
  const simulator = expectedChecksForFiles([{ filename: 'packages/mesh-simulator/src/main/java/App.java' }]);
  assert.deepEqual(simulator.groups, ['java']);
  const workflow = expectedChecksForFiles([{ filename: '.github/workflows/java.yml' }]);
  assert.deepEqual(workflow.groups, ['java']);
});

test('docs-only changes do not invent required code CI', () => {
  const result = expectedChecksForFiles([{ filename: 'README.md' }, { filename: 'docs/guardian.md' }]);
  assert.deepEqual(result.groups, []);
  assert.deepEqual(result.checks, []);
});
