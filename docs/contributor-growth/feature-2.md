## Problem, benefit and current behavior

Help testers include an exact message identifier in reproducible reports. MessageDetailActivity reads the messageId intent extra and shows the record and audit trail, but has no explicit copy-ID action.

**Evidence:** source-confirmed on published main at [4986e28](https://github.com/Gautam-aman/ResQNet/commit/4986e284f8c4f1add60cb1e2568d0cdeadc29831); proposed enhancement, not a reproduced runtime defect. Start from current GitHub main; no unpublished local commits are required.

## Scope and verified starting points

- [apps/android/app/src/main/java/dev/resqnet/android/ui/MessageDetailActivity.java](https://github.com/Gautam-aman/ResQNet/blob/main/apps/android/app/src/main/java/dev/resqnet/android/ui/MessageDetailActivity.java)
- [apps/android/app/src/main/res/layout/activity_message_detail.xml](https://github.com/Gautam-aman/ResQNet/blob/main/apps/android/app/src/main/res/layout/activity_message_detail.xml)
- [apps/android/app/src/main/res/values/strings.xml](https://github.com/Gautam-aman/ResQNet/blob/main/apps/android/app/src/main/res/values/strings.xml)
- [apps/android/app/src/test/java/dev/resqnet/android/service/SettingsDisplayTest.java](https://github.com/Gautam-aman/ResQNet/blob/main/apps/android/app/src/test/java/dev/resqnet/android/service/SettingsDisplayTest.java)

## Acceptance criteria

- [ ] Add an accessible Copy Message ID button with a string resource.
- [ ] Copy only the identifier of the displayed message and provide confirmation feedback.
- [ ] Handle null, blank or unavailable identifiers gracefully, without copying stale data; preserve missing-record navigation.
- [ ] Never copy message contents, encryption keys, audit details or sensitive metadata.
- [ ] Add a Robolectric test activating the control and checking ClipboardManager primary clip, feedback and unavailable-ID handling.
- [ ] Preserve retry, rendering and audit behavior.

## Tests and commands

Use the existing Robolectric setup as a reference; add a focused UI test in app/src/test/java/dev/resqnet/android/ui.

```bash
cd apps/android
./gradlew testDebugUnitTest lintDebug
```

## Non-goals

No diagnostics export (#74), fingerprint action (#78), transport or cryptography changes.

## Difficulty and contributor guidance

**Beginner** — Java, Android views, Robolectric. Check assignees, comment `/assign` and wait for confirmation before starting. Ask here for setup or test guidance; use `/unassign` to release the issue. Report exact commands, results and anything untested.

Read the [contributor guide](https://github.com/Gautam-aman/ResQNet/blob/main/CONTRIBUTING.md), [PR evidence template](https://github.com/Gautam-aman/ResQNet/blob/main/.github/PULL_REQUEST_TEMPLATE.md) and [security policy](https://github.com/Gautam-aman/ResQNet/blob/main/SECURITY.md). Guardian readiness is advisory; maintainers review and decide.
