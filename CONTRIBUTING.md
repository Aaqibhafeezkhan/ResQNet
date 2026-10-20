# Contributing to ResQNet

ResQNet is safety-adjacent software: a routing bug can send a rescue request to
the wrong place, and a UI bug can make a responder believe the mesh is healthy
when it is not. Contributions are welcome, but correctness and honest evidence
matter more than speed.

By participating you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

## Before you start

- Read the [README](Readme.md), especially the sections on what is verified,
  what is simulated, and what is untested.
- Read [docs/security/threat-model.md](docs/security/threat-model.md) before
  changing the trust, routing, or transport model.
- Never report a security vulnerability in a public issue. See
  [SECURITY.md](SECURITY.md).
- Say plainly whether a claim is **reproduced**, **read from source**, or
  **inferred**. This repository treats those as different evidence levels, and
  so should you.

## Ways to contribute

- Fix a bug or implement a scoped enhancement.
- Improve documentation, comments that explain design decisions, or test
  instructions.
- Add tests, especially regression tests that fail before a fix and pass after.
- Test the Android app on physical devices. Real hardware results are the
  project's largest evidence gap; see
  [apps/android/DEVICE_TESTING.md](apps/android/DEVICE_TESTING.md).
- Triage issues: reproduce a report, narrow it down, or confirm which component
  is responsible.

Good places to start are issues labelled
[`good first issue`](https://github.com/Gautam-aman/ResQNet/labels/good%20first%20issue).
Comment on the issue before starting so work is not duplicated.

## Repository layout

| Path | What it contains |
|---|---|
| `packages/mesh-core` | Protocol, validation, routing, durable queue interfaces, metrics |
| `packages/mesh-simulator` | Deterministic simulated radio and report generation |
| `services/backend` | Spring Boot gateway, Flyway migrations, REST and WebSocket API |
| `apps/android` | Native Android app and Nearby Connections transport |
| `apps/web` | Public site and responder command centre |
| `docs` | Architecture, threat model, benchmarks, demo and routing notes |
| `.github/workflows` | CI for Java, backend, Android, instrumented Android and web |

## Setup

- JDK 21
- Maven 3.9+
- Node 21+ (the web package declares `engines.node >= 20.19`)
- Android SDK with API 34 and build tools for the Android app
- PostgreSQL 14+ only if you want to run the gateway against a real database;
  the backend test suite uses H2

```bash
git clone https://github.com/Gautam-aman/ResQNet.git
cd ResQNet
mvn -q package
cd apps/web && npm ci && npm run build
```

Running the gateway and the web dev server, including the required
`RESQNET_JWT_SECRET` and `RESQNET_GATEWAY_TOKEN` variables, is documented in the
[README](Readme.md#run-the-gateway). Do not commit secrets, keystores, or
`local.properties`.

## Build and test

| Suite | Command |
|---|---|
| Everything Java | `mvn -B -ntp verify` |
| mesh-core | `mvn -pl packages/mesh-core test` |
| Simulator | `mvn -pl packages/mesh-simulator -am test` |
| Backend (H2) | `mvn -pl services/backend test` |
| Backend (PostgreSQL) | `mvn -pl services/backend -am test -Dspring.profiles.active=postgres -Dtest=PostgresMigrationIT -Dsurefire.failIfNoSpecifiedTests=false` |
| Android unit + Robolectric | `cd apps/android && ./gradlew testDebugUnitTest` |
| Android lint | `cd apps/android && ./gradlew lintDebug` |
| Android instrumented | `cd apps/android && ./gradlew connectedDebugAndroidTest` |
| Web lint | `cd apps/web && npm run lint` |
| Web typecheck | `cd apps/web && npm run typecheck` |
| Web tests | `cd apps/web && npm run test` |
| Web production build | `cd apps/web && npm run build` |

Seven simulator tests are tagged `slow` and are skipped by an ordinary build.
Run them explicitly with `-DexcludedTestGroups=` when your change touches
routing, retries, or the simulator. CI does this.

The PostgreSQL tests need `TEST_PG_URL`, `TEST_PG_USER` and `TEST_PG_PASSWORD`.
The Android instrumented suite needs an emulator or device; CI runs it at
320x640, and it has also been run at 1080x2400.

## Reporting a bug

Use the bug report template. A useful report includes:

1. What you expected and what happened.
2. Exact reproduction steps, with command lines, seeds, topology, device
   models, or browser versions.
3. Whether you reproduced it, read it from source, or inferred it.
4. Relevant file and line references.
5. Logs or fixtures with secrets and personal data removed.

If the issue is a vulnerability, stop and follow [SECURITY.md](SECURITY.md).

## Proposing a change

- Open an issue first for anything larger than a small fix, so the approach can
  be agreed before code is written.
- Keep pull requests focused. One logical change per pull request is easier to
  review and safer to revert.
- Add or update tests. A bug fix should normally include a test that fails
  without the fix.
- Update documentation when behaviour, a command, or a limitation changes.
- Keep the "verified / simulated / untested" distinction honest in code, tests,
  docs, and pull request descriptions.
- Do not include generated build output, keystores, credentials, or IDE files.

### Commit messages

The project uses conventional prefixes, for example:

```text
fix(android): remove lost endpoints from discovered set
test(backend): cover paginated search across a filtered window
docs: explain why the simulator uses a manual clock
```

Use the imperative mood and explain *why* in the body when the reason is not
obvious from the diff.

## Your first contribution

1. Pick an issue labelled `good first issue`, or ask on an issue where you would
   like to help.
2. Comment that you are taking it. If you change your mind, comment again so
   somebody else can pick it up.
3. Fork the repository and create a branch from `main`:
   `git switch -c fix/short-description`.
4. Reproduce the problem before changing code. For a bug, write down the
   failing command or test.
5. Make the smallest change that fixes the problem, and add a test.
6. Run the relevant commands from the table above. State exactly what you ran
   and what passed in the pull request.
7. Open a pull request using the template. Link the issue with `Closes #123`.
8. Respond to review. Maintainers may ask for evidence or for the change to be
   split; this is about keeping safety-critical claims defensible, not about
   gatekeeping.

You do not need to be an expert in mesh networking to contribute. Documentation
fixes, test improvements, and careful reproductions are genuinely valuable.

## Questions

GitHub Discussions are not enabled. Open an issue with the `question` label, or
comment on an existing issue that already covers the topic.

## Security

Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md).
Do not open a public issue, and do not include working exploit details in a
pull request or discussion.
