# Android device testing

Automated tests cover the routing engine, the durable queue and the payload
codec. **None of them prove that two real phones can find each other and pass a
message.** That requires radios, two devices, and a physical space, so this
document is the test plan for it.

Nothing in this file has been executed as part of an automated build. Treat
every checkbox as unverified until someone runs it on hardware and records the
result.

---

## 1. What automation did verify

| Area | How | Status |
| --- | --- | --- |
| Protocol encode/decode and validation | 141 JVM unit tests in `mesh-core` | Verified |
| Multi-hop routing, deduplication, hop and expiry limits | JVM tests over real `MeshRouter` instances | Verified |
| Store-and-forward, retry, expiry, acknowledgement | JVM tests over real routers and stores | Verified |
| Adversarial cases: replay, tampering, origin substitution, flooding | JVM tests | Verified |
| Simulator reproducibility | Deterministic discrete-event runs | Verified |
| SQLite queue, audit trail, peer store | Robolectric against real SQL | Verified |
| APK compiles and assembles | `:app:assembleDebug` | Verified |

## 2. What requires hardware

| Area | Status |
| --- | --- |
| Two phones discovering each other over Nearby Connections | **Unverified** |
| One-hop message delivery on a real radio | **Unverified** |
| Multi-hop delivery through an intermediate phone | **Unverified** |
| End-to-end acknowledgement over real hardware | **Unverified** |
| Behaviour when a phone's screen is locked | **Unverified** |
| Behaviour when the OS kills and restores the service | **Unverified** |
| Android 12+ split permission flow | **Unverified** |
| Behaviour on a device with no Google Play services | **Unverified** |

## 3. Prerequisites

- Two or more Android handsets, Android 8.0 (API 26) or newer.
- Google Play services present and up to date. Without it Nearby Connections
  cannot start, and the app says so on the Home screen rather than pretending.
- All devices on the same Wi-Fi network **and** with Bluetooth and Wi-Fi
  location services switched on. Nearby Connections uses whichever radio the OS
  decides; being on the same network is not required for P2P cluster but makes
  the first run easier to reason about.
- Physical access to every device at the same time, for the verification steps.

```bash
cd apps/android
./gradlew :app:installDebug        # installs to every connected device
```

Install with one device attached at a time if several are connected:

```bash
./gradlew :app:installDebug -Pandroid.injected.device.serial=ABC123
```

## 4. Test cases

### T1 — First run and permissions
1. Install on a fresh device.
2. Launch. The welcome screen must explain both permissions before asking.
3. Grant Nearby devices. Deny Location.
4. Expect: the mesh starts, the SOS button is reachable, and Location being
   denied does not prevent anything except attaching coordinates.
5. **Pass condition:** the app is usable with Location permanently denied.

### T2 — Identity is stable
1. On Home → Settings, note the verification code and fingerprint.
2. Force-stop the app. Relaunch.
3. **Pass condition:** identical code and fingerprint.
4. Reboot the device, relaunch, and check again.
5. **Pass condition:** still identical. The keystore key must survive a reboot.

### T3 — One-hop delivery
1. Devices A and B: complete the welcome flow on both.
2. On both, open Nearby devices and tap the refresh control.
3. Expect: each sees the other **as unverified, in range**.
4. Compare the codes on both screens. They will not match yet; that is correct.
5. On B, tap Verify on A's row, read A's code on A's screen, confirm, then
   **Verify** on B.
6. Repeat from A so both sides hold each other's key.
7. On A, send an SOS addressed to everyone in range.
8. **Pass condition:** B receives it, and its state reads *Confirmed received*,
   not merely *Received*.

### T4 — Multi-hop delivery
1. Three devices A, B, C.
2. Verify A↔B and B↔C as in T3. Leave A↔C **unverified** and physically
   separated beyond direct radio range but still within the same room.
3. Confirm on B's Nearby screen that B sees both A and C.
4. On A, send an SOS addressed to everyone in range.
5. **Pass condition:** C receives the message, and the detail screen reports it
   travelled at least two hops. C's inbox must contain it even though A and C
   have never verified each other, because B relayed it.

### T5 — Relay works when the destination is absent
1. Devices A and B verified. C present but off.
2. From A, send a message addressed to C's identity (choose One device).
3. Expect: Home shows a queued message, and the detail screen says it is
   waiting for a nearby device.
4. Power on C and verify A↔C.
5. **Pass condition:** the queued message leaves without the user pressing
   anything, and A's state becomes *Confirmed received*.

### T6 — Store-and-forward survives a kill
1. From A, queue a message to an unreachable C.
2. Immediately force-stop A (`adb shell am force-stop dev.resqnet.android.debug`).
3. Relaunch A.
4. **Pass condition:** the message is still listed and still queued.
5. Bring C into range and verified.
6. **Pass condition:** it is delivered and confirmed without user action.

### T7 — Locked screen
1. Lock A and B.
2. Send an SOS from B.
3. **Pass condition:** A still receives it. Nearby Connections continues while
   the screen is locked; the foreground service is what keeps the process alive.
4. Record the battery cost over ten minutes. If the radio is clearly saturating,
   that is a finding, not a pass.

### T8 — Degradation is honest
1. Turn Bluetooth off on B.
2. From A, send an SOS.
3. **Pass condition:** A reports the message as queued or failed. It must never
   display *Confirmed received*.
4. Turn Bluetooth back on. Expect automatic delivery on reconnection.

### T9 — Blocked peer
1. On A, block B.
2. From B, send an SOS.
3. **Pass condition:** A shows nothing in its inbox, and B's message ends
   *Expired* or *Could not be sent*, never acknowledged.

### T10 — Malformed traffic
1. Install a build with a debug hook that transmits a truncated envelope.
2. **Pass condition:** the receiving device stays responsive and shows nothing.
   The app must not crash on hostile input.

### T11 — No Play services
1. On a device without Google Play services, or with it disabled, launch.
2. **Pass condition:** the app starts, explains that the radio is unavailable,
   and does not claim to be connected.

## 5. Battery and thermal observation

Run a 30 minute soak with three devices in a static mesh, sending one SOS every
two minutes.

Record:
- battery percentage change per device;
- whether any device's foreground service is killed;
- whether any message is duplicated in a recipient's inbox.

Duplicate delivery into the inbox is a defect. A duplicate being *suppressed*
after being seen is correct.

## 6. Recording results

For each test, record:

| Field | Value |
| --- | --- |
| Test id | e.g. T4 |
| Date | |
| Devices and OS versions | |
| Result | pass / fail / blocked |
| Evidence | screenshot, logcat excerpt |
| Notes | |

Suggested log capture:

```bash
adb logcat -s ResQNetTransport:V ResQNetEngine:V ResQNetStore:V AndroidRuntime:E
```

## 7. Known gaps

- **No automated radio test.** Robolectric and the simulator both replace the
  radio. A bug that only appears on a real radio, such as a payload that Nearby
  fragments at exactly 16 KiB, would not be caught by CI.
- **No long-duration soak.** Ten minutes of steady traffic is not a flood.
- **No OEM coverage.** Battery optimisation on Samsung, Xiaomi and OnePlus
  behaves differently from stock Android and is the most likely source of a
  real-world failure.
- **No location accuracy validation.** Location is attached when the user asks
  and is not tested against a real fix here.