# ResQNet v0.2.0-beta — Android Experimental Preview

**Experimental pre-release**

This is the first release that ships a downloadable Android APK. Everything else
in it — the mesh engine, the simulator, the coordination gateway, the command
centre — has been present since v0.1.0-alpha and is unchanged in kind.

> **Read this first.** This is research software distributed as a pre-release.
> It is **not** a replacement for official emergency services and must not be
> relied upon as one. Do not deploy it where a failure would cost lives.

---

## What's new since v0.1.0-alpha

### The Android APK

The headline change. `dev.resqnet.android`, signed with a release keystore and
verified with `apksigner`, installable on any device running Android 8.0 (API
26) or newer.

Alongside it, four defects were found by auditing the Android application and
fixed. Each of them would have broken a multi-device demonstration on its own:

- **SOS processing.** The two welcome-screen buttons performed the same action,
  so the path into composing a message was not the path the label described.
- **Peer identity rebinding.** Nearby Connections issues a fresh endpoint id
  every time a link comes back up. The provisional binding made on
  reconnection was stored under a handle that never appeared in the mapping,
  while the mapping still pointed at the handle from the link that had just
  died. Directed messages addressed to a peer this device already trusted were
  sent to an endpoint that no longer existed, and nothing looked wrong: the peer
  was still listed and the trust store still held its key.
- **Retry behaviour while isolated.** A message queued on a relay with no peers
  was charged against the retry budget for every no-op pass, so a device that
  had been alone all night recorded someone's SOS as "gave up after 12
  attempts" without a single transmission having been attempted.
- **Hop accounting at a relay.** A relay that held a frame for a later pass
  stored the copy it had *intended* to send, which had already spent a hop. The
  frame was charged for a hop the radio never accepted, the retry spent the same
  hop again, and a two-hop path arrived at its recipient reported as three. A
  relay holding a frame under a tight budget could declare it hop-exhausted
  before it had left the device once.

### Instrumented Android testing in CI

`connectedDebugAndroidTest` now runs in GitHub Actions against an emulator at
320×640 @ 160 dpi: SOS flow, permission flow, identity handling, message
processing and the regression tests for the defects above. 33 tests. The same
suite has also been run locally at 1080×2400 @ 420 dpi.

### Everything else

- The mesh core now has 154 tests, the simulator 51, the gateway 60, the Android
  unit suite 56, and the web suite 46.
- The release workflow builds, verifies and publishes the signed APK. It still
  refuses to run when signing material is committed to the tree, still defaults
  to a dry run, and still cannot be triggered by a push.
- The README was restructured and four claims that no longer matched the source
  were corrected.

---

## Hardware testing

**The developer reports the Android application working on two physical
phones.**

That is a user report. It is not independently verified evidence, and it is not
evidence of multi-hop routing. With two handsets in range of each other,
Nearby's `P2P_CLUSTER` strategy connects them directly — there is no third
device to relay through, so no hop is taken and no relay participates.

**What is not verified:**

- End-to-end offline delivery between three or more physical devices.
- Cryptographic acknowledgement over real radios.
- Physical radio range or penetration.
- Battery behaviour under continuous mesh participation.

A proper multi-device run requires the phones to be out of range of one another
so that a relay actually exists. The procedure is
`apps/android/DEVICE_TESTING.md`, and no result from it is recorded.

Every other claim in this release rests on automated tests, emulator runs, and
a deterministic simulator. The simulation results in `docs/benchmarks/results.md`
describe the routing engine against a **modelled radio** and are not field
measurements.

---

## Known limitations

- **Experimental research software.** No certified reliability guarantee of any
  kind.
- **Not a substitute for emergency services.**
- **Three-device multi-hop routing is unverified on physical hardware.**
- **Real-world radio range and battery behaviour are unknown.**
- **Multi-emulator testing has not been performed.**
- **Android background restrictions may suspend the mesh.** Doze mode, battery
  optimisation and manufacturer task killers can stop the foreground service.
- **Payloads are signed but not encrypted.** Envelope signatures guarantee
  authenticity and integrity, not confidentiality. Any device in radio range
  that receives a frame can read it.
- **Peer-identity rebinding is not proven by a challenge-response.** Rebinding a
  known identity to a new radio endpoint relies on the claimant presenting a
  public key already on file. A public key is public, so a device that has met
  the victim can rebind the victim's identity to its own endpoint and black-hole
  or read traffic addressed to them. It cannot forge frames as the victim.
  Closing this needs a link-bound challenge-response, which is not implemented.
- **No `P2P_CLUSTER` connection-level authentication token.** Trust is enforced
  one layer up, by envelope signature verification against a key the owner
  confirmed in person. That is the stronger property, but an unauthenticated
  peer can still occupy a connection slot.
- **The shared gateway token is a bearer secret.** Revocable by rotation, but not
  attributable; every device holding it shares one identity.
- **PostgreSQL under concurrent writes and replication is untested.**
  Migrations and the incident lifecycle are tested.
- **Incident assignment and manual incident creation have working endpoints but
  no UI.**

---

## Installation

### Android

1. Download `resqnet-v0.2.0-beta-release.apk` from this release.
2. Verify its SHA-256 against `SHA256SUMS` before installing.
3. When Android prompts, allow installation from the source you downloaded it
   from. Only ever do this for an APK you obtained from this repository's
   releases page.
4. Install the application.
5. Grant the nearby-devices permission when asked.
6. **Enable mesh participation explicitly.** It is a deliberate choice, not a
   default.
7. Test communication with a second device.

Do not install `resqnet-v0.2.0-beta-debug.apk`. It is included for side-by-side
hardware testing only; it carries the `.debug` application-id suffix and is
debug-signed.

### Simulator, gateway and command centre

Unchanged from v0.1.0-alpha and covered in full in `INSTALLATION.md` beside this
file. Prerequisites are Java 21+, PostgreSQL 14+, and Node.js 21+ to rebuild the
web bundle.

---

## Contributing

Issues and pull requests are welcome. When you report a finding, say honestly
whether you **reproduced** it, **read** it, or **inferred** it — several defects
in this codebase were found by reading it rather than by running it, and the
difference matters to whoever triages it.

If you have tested the Android app on real hardware, that result is worth more
than anything else you could send. See `apps/android/DEVICE_TESTING.md`.

## License

MIT. See the repository root.
