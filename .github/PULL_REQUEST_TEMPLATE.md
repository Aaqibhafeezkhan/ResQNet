## Summary

<!-- What changed, and why? One logical change per pull request. -->

## Related issue

<!-- Closes #123, or explain why there is no issue. -->

## Verification

State exactly what you ran and what passed. Do not claim a test or hardware run
that you did not perform.

- [ ] Java: `mvn -B -ntp verify`
- [ ] Backend PostgreSQL tests (if the change touches persistence or migrations)
- [ ] Android unit: `cd apps/android && ./gradlew testDebugUnitTest`
- [ ] Android instrumented (if the change touches transport or UI)
- [ ] Web: `cd apps/web && npm run lint && npm run typecheck && npm run test && npm run build`
- [ ] Manual or simulator reproduction described below

Commands and results:

```text

```

## Evidence

<!-- Logs, screenshots, report files, or before/after output. Remove secrets and personal data. -->

## Checklist

- [ ] The change is scoped to one problem.
- [ ] A regression test fails without the fix and passes with it (for bug fixes).
- [ ] Documentation is updated when behaviour, commands, or limitations change.
- [ ] No secrets, credentials, keystores, or personal data are included.
- [ ] The verified / simulated / untested distinction remains accurate.
- [ ] UI changes preserve keyboard access and reduced-motion behaviour.
