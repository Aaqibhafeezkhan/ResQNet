# Android device testing

Automated tests cover the routing engine, the durable queue and the payload
codec. **None of them prove that two real phones can find each other and pass a
message.** That requires radios, real devices and a physical space, so this
document is the procedure for doing it.

**Status: not executed.** No step in this document has been run on hardware as
part of any build, because CI has no radios and no desk. Every checkbox below is
unverified. Fill them in as you go, with the phone model, the Android version
and what you actually observed. A test plan that has been read but not executed
is a plan, not evidence, and this file says so at the top on purpose.

Fill this in as you go:

```
Tester:            ____________________
Date:              ____________________
Phones:            ____________________
Android versions:  ____________________
Outcome:           not started / partial / complete
```

---

## 1. What automation already verified

So you do not repeat it on hardware.

| Area | How | Status |
|---|---|---|
| Protocol encode/decode and validation | 141 JVM tests in `mesh-core` | Verified |
| Multi-hop routing, dedup, hop and expiry limits | JVM tests over real `MeshRouter` instances | Verified |
| Store-and-forward, retry, expiry, acknowledgement | JVM tests | Verified |
| Adversarial cases: replay, tampering, origin substitution, flooding | JVM tests | Verified |
| Simulator reproducibility across JVMs | forked-process test | Verified |
| SQLite queue, peer store, audit trail | Robolectric against real SQL | Verified |
| API 26 compatibility of mesh-core | app compiles mesh-core from source | Verified |
| APK assembles | `:app:assembleDebug` | Verified |

API 26 compatibility deserves a note: mesh-core is compiled *from source* by
the Android build, so an API the handset does not have is a compile error rather
than a crash discovered in a collapsed building.

---

## 2. Before you start

### Hardware

- Three Android phones, API 26 or newer. Two is the minimum for the relay
  test; three is needed to prove an actual A→B→C hop.
- A USB cable for at least one phone, for `adb`.
- A physical space you can move between. A room with a door, and ideally a
  second space across it.

### Install

```bash
mvn -q package
adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
```

Do this on all three phones.

### Permissions, and why they are required

Android will not let the app run without these, and the reasons are worth
knowing because the failure modes are confusing:

| Permission | Why Android requires it | What breaks without it |
|---|---|---|
| Nearby devices | API 33+ gate over the Nearby Connections APIs | Peer discovery returns nothing. No error, just silence. |
| Location (precise) | Peer discovery exposes a derived location | No peers discovered. Android's requirement, not ResQNet's choice. |
| Bluetooth | The radio | Discovery fails |
| Notifications (API 33+) | The foreground service must show one | The mesh service may be killed immediately |

The first-run flow requests these. If you dismissed it, go to
Settings → Apps → ResQNet → Permissions.

### Test topology

Label the three phones **A**, **B** and **C**. A is the sender, B is the relay
you will take away, C is the destination.

```
  [ A ] -------- [ B ] -------- [ C ]     two metres apart, door closed
```

**The door matters.** Android radios see through plasterboard. If the phones can
hear each other directly then "B relayed it" proves nothing, because C may have
received A's transmission itself. After each placement, confirm on A's peer
list that **C does not appear** before you send anything.

---

## 3. Discovery

| # | Step | Expected | Result |
|---|---|---|---|
| 3.1 | Start the mesh service on all three | Service notification visible on each | |
| 3.2 | On A, open the peer list | B appears within ~10 s | |
| 3.3 | On A, open the peer list again | C does **not** appear | |
| 3.4 | On C, open the peer list | A does **not** appear | |
| 3.5 | Move B next to A | B disappears from A's list within ~30 s | |
| 3.6 | Move B back | B reappears within ~10 s | |

**If nothing is discovered at all**, work through section 8 before anything
else. A silent empty peer list is the single most common failure and it is
almost always a permission, not a bug.

---

## 4. The A→B→C relay

This is the test the project exists for.

| # | Step | Expected | Result |
|---|---|---|---|
| 4.1 | On A, send an SOS addressed to C | A shows **Handed to a nearby device** | |
| 4.2 | Watch A's state for 10 s | Changes to **Confirmed received**, not before | |
| 4.3 | On C, open the message | Message body is intact | |
| 4.4 | On C, check the hop count | `2` — it travelled A → B → C | |
| 4.5 | Confirm B's screen | B never showed the message body | |

**Step 4.5 matters as much as 4.4.** B relayed a message it had no way to read.
If B displays the body, the envelope is being decoded and displayed on
intermediate hops, which is a privacy defect.

**Step 4.2 matters more than either.** If A shows "delivered" before C confirms,
the distinction between `SENT_TO_PEER` and `ACKNOWLEDGED` has been lost, and
every claim this project makes about honest delivery reporting is void.

---

## 5. Offline operation

| # | Step | Expected | Result |
|---|---|---|---|
| 5.1 | Put all three in aeroplane mode | | |
| 5.2 | On A, send an SOS to C | Relays and is confirmed, as in 4.2 | |
| 5.3 | Repeat with all three on Wi-Fi but with the router unplugged | Same | |

Nearby Connections is P2P and does not route through infrastructure, so
aeroplane mode should not affect it at all. **If it does, stop and report it**:
it means something in the app is reaching for a network the radio does not
need.

---

## 6. Partition and reconnection

The store-and-forward claim.

| # | Step | Expected | Result |
|---|---|---|---|
| 6.1 | Move B to another room | A's peer list drops B | |
| 6.2 | On A, send an SOS to C, addressed to a device A cannot reach | Queued, not lost | |
| 6.3 | Wait 60 s | A still shows it queued, not failed, not delivered | |
| 6.4 | Bring B back | B rejoins within ~10 s | |
| 6.5 | Within 15 s | The queued message is delivered and confirmed | |

Step 6.3 is where a broken implementation shows: a router that gives up and
marks the message `FAILED` has thrown away an emergency message because the
radio was briefly busy. A message inside its lifetime should be kept.

Let the message expire instead, once, to see the other behaviour:

| # | Step | Expected | Result |
|---|---|---|---|
| 6.6 | Repeat 6.1–6.2, wait past the message lifetime | State becomes **Too old to send**, never "delivered" | |

---

## 7. Failure injection

What happens when someone interferes.

| # | Step | Expected | Result |
|---|---|---|---|
| 7.1 | Revoke Nearby Devices permission on B while B is relaying | B stops relaying; A and C stay up; A's queue is intact | |
| 7.2 | Turn B's screen off during a relay | Message still relays; the foreground service is doing its job | |
| 7.3 | Force-stop B mid-relay | A re-queues and retries; no silent loss | |
| 7.4 | Send the same SOS from A to C twice | C receives once; the duplicate is suppressed | |
| 7.5 | Kill the app on B and restart it | B rejoins; its identity is unchanged | |

7.4 is worth watching on C's screen: the duplicate should never render. 7.5
depends on the device key being stored, so a restart must not present as a new,
untrusted device.

---

## 8. Troubleshooting

Work top to bottom. In order, this is what actually goes wrong.

**No peers discovered at all**

1. Nearby Devices permission granted? (API 33+)
2. Location permission granted, and set to **Precise/Approximate**, not denied?
3. Bluetooth on and discoverable?
4. Location services enabled **system-wide** (the toggle in quick settings)?
   Nearby Connections needs this; the app cannot grant it.
5. Airplane mode off on every phone?
6. Are both phones on the same Wi-Fi band? Nearby Connections does not need
   Wi-Fi, but a phone stuck on a distant band can behave oddly. Turning Wi-Fi
   off entirely is a useful diagnostic.
7. Restart the app, then reboot the phone. Android's Nearby stack occasionally
   wedges after a permission change and a reboot is the honest fix.
8. Confirm the foreground service is running, not just the app.

**Peers discovered but no message passes**

1. Watch the message state on A. `SENT_TO_PEER` with no confirmation means the
   hop succeeded and the acknowledgement did not come back.
2. On B, check the peer list contains C. If B cannot see C it cannot relay to
   it, and A will correctly keep retrying.
3. Check the message has not expired.
4. Look at the logs:

```bash
adb logcat -s ResQNet:V MeshRouter:V ResQNetDatabase:V
```

**Everything is delivered but nothing is confirmed**

The acknowledgement path specifically. The relay's own copy is fine; the
confirmation is not coming back. Check that C shows the message and that A is
still holding the message as unconfirmed rather than having marked it failed.

**Devices discovered but nothing renders in the UI**

Check the foreground service notification. A service that is not foregrounded
is killed by the OS within minutes, which looks exactly like a routing failure
and is not one.

---

## 9. Recording results

For each test that fails, capture:

- Phone model and Android version for **every** device involved.
- Whether the failure is reproducible or intermittent. An intermittent failure
  is usually a timing or retry bug; a consistent one is usually a logic bug.
- The full `adb logcat` from app start to failure.
- The distance and the number of walls between the phones.

That is enough to reproduce it. Without those five things a radio bug report is
usually not actionable, and it is unfair to the person who has to fix it.

---

## 10. What this does not cover

Even a complete pass does not prove:

- **Range.** Every test here is at two metres. Real range through rubble is a
  different and much harder question, and nothing in this repository is
  evidence about it.
- **Battery.** A phone in a pocket with the screen off may not keep the mesh
  alive for as long as one on a desk on mains power. Measuring that means hours,
  not minutes.
- **Interference.** No congested 2.4 GHz environment was used.
- **Many peers.** Three phones is the minimum for a relay proof. Flood routing
  at 100 nodes is simulated, never simulated *and* radio-tested.
- **Android's own reliability.** Nearby Connections has known issues across
  vendors. A failure here may be a platform bug, and telling those two apart
  needs the `adb logcat` capture from section 9.