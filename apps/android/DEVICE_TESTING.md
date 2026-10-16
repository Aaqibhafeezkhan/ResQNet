# Android hardware testing

Automated tests cover the routing engine, the durable queue and the payload
codec. **None of them prove that two real phones can find each other and pass a
message.** That requires radios, real devices and a physical space, so this
document is the procedure for doing it.

**Status: not executed.** No step below has been run on hardware as part of any
build, because CI has no radios and this has not been done on a desk. Every
checkbox is unverified.

Eleven defects found by reading this code would each have prevented the
three-phone demo on their own. They are fixed. **None of them is evidence that
the radio path works**, and this file exists so that the distinction stays
visible rather than being quietly lost.

Fill this in as you go:

```
Tester:            ____________________
Date:              ____________________
Phones:            ____________________
Android versions:  ____________________
Play services:     ____________________
Outcome:           not started / partial / complete
```

---

## 1. What automation already verified

| Area | How | Status |
|---|---|---|
| Protocol encode/decode and validation | 141 JVM tests in `mesh-core` | Verified |
| Multi-hop routing, dedup, hop and expiry limits | JVM tests over real `MeshRouter` instances | Verified |
| Both cluster topologies (full mesh and chain) | `ClusterTopologyTest` | Verified |
| Router reaches the graph's structural ceiling | `RoutingEfficiencyTest` | Verified |
| Store-and-forward, retry, expiry, acknowledgement | JVM tests | Verified |
| Adversarial cases: replay, tampering, origin substitution, flooding | JVM tests | Verified |
| Simulator reproducibility across JVMs | forked-process test | Verified |
| SQLite queue, peer store, audit trail | 55 Robolectric tests | Verified |
| Endpoint to identity binding and promotion | `PeerBindingsTest` | Verified |
| API 26 compatibility of mesh-core | app compiles mesh-core from source | Verified |
| APK assembles, debug and release | `:app:assembleDebug assembleRelease` | Verified |

API 26 compatibility deserves a note: mesh-core is compiled *from source* by
the Android build, so an API the handset does not have is a compile error rather
than a crash discovered in a collapsed building.

## 2. What requires hardware, and what to expect

| Area | Status |
|---|---|
| Two phones discovering each other | **Unverified** |
| A peer being resolvable by device identity | **Unverified** |
| A message traversing A → B → C | **Unverified** |
| Delivery with no network at all | **Unverified** |
| Queued messages surviving a partition | **Unverified** |
| Battery life over hours | **Unverified** |
| Range through walls and rubble | **Unverified** |

### The one thing that will fool you

`P2P_CLUSTER` is an **M-to-N** strategy. Google's documentation says each device
"can both initiate outgoing connections to M other devices and accept incoming
connections from N other devices". That is a **full mesh** within radio range,
which is documented at roughly 100 m.

So if you put three phones side by side, **A to C is one hop and no relay is
involved.** The demo will appear to work and will prove nothing about relaying.
It will also look broken to anyone expecting a hop count of two.

Put the phones out of range of each other, and confirm it on screen before
sending anything. Section 5 is the step that matters.

---

## 3. Before you start

### Hardware

- Three Android phones, **API 26 or newer**. Android 12 (API 31) and 13+ are
  the interesting cases because the permission model changed there. Testing one
  old and two new is more informative than three of the same.
- A USB cable for at least one, for `adb`.
- Two rooms with a door, or a corridor. You need to be able to separate devices
  by more than radio range and then bring them back.

### Install

```bash
mvn -q package
cd apps/web && npm ci && npm run build && cd ../..
adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
```

Repeat for all three.

> The debug APK is signed with the Android SDK's public debug key. It is for
> internal testing only and must never be distributed as a release.

### Permissions

Android will not let the app run without these. The reasons are worth knowing,
because the failure modes are confusing.

| Permission | Why Android requires it | What breaks without it |
|---|---|---|
| Nearby devices | API 33+ gate over the Nearby Connections APIs | No peers found, and no error |
| Bluetooth scan | Finding advertisers | No peers found |
| Bluetooth connect | Opening the pipe | Discovery works, connections fail |
| Bluetooth advertise | Required for `P2P_CLUSTER` | Advertising throws; **nothing is discoverable** |
| Location (precise) | Android requires it for peer discovery | No peers found on API 30 and below |
| Notifications (API 33+) | The foreground service must show one | Service may be killed silently |

Verify on each phone, not once:

```bash
adb shell dumpsys package dev.resqnet.android.debug | grep -A 20 "runtime permissions"
```

All of `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` and
`ACCESS_FINE_LOCATION` should read `granted=true` where applicable.

### Radio settings that will silently ruin the test

These are checked on **every** phone:

- **Bluetooth on.** Obvious, and frequently off on a device used only for
  testing.
- **Location services enabled system-wide**, not just the app permission. The
  quick-settings location toggle governs this. Android requires it for peer
  discovery and the app cannot grant it.
- **Aeroplane mode off.**
- **Wi-Fi**: turn it off on all three for the test. `P2P_CLUSTER` does not use
  Wi-Fi, and a phone churning between bands is a confounder you do not need.
- **Battery optimisation off** for ResQNet on all three. Android will kill a
  foreground service that it decides is not important, and the symptom looks
  exactly like a routing failure.
- **Data saver off** if the phone has it; it can restrict background work.
- Screen on for the first test. Test with the screen off separately, in 7.4.

---

## 4. Diagnostics first

Every phone: **Home → Diagnostics**.

This screen exists because there was previously no way to see what the mesh was
doing. Check it before anything else and it will tell you which of the later
stages is even applicable.

You are looking for:

| Line | Means |
|---|---|
| `radio STARTED`, `ready yes` | The radio is up. If it says `STOPPED`, nothing else matters. |
| `discovered N` | N peers found by the radio. Zero means advertising or permissions. |
| `discovered N bound M` with `*` marks | `*` marks a **connected** peer. Discovered-but-not-connected is a different fault from never-discovered. |
| `ERROR ...` | The operation and why. `advertising: Missing permission` is the classic. |

Everything on that screen is selectable, so select all and paste it into a
report. The ids in it are what make a mesh problem reproducible; a screenshot
loses them.

---

## 5. Discovery

Label the phones **A**, **B** and **C**.

```
  [ A ] ------ 2m ------ [ B ] ------ 2m ------ [ C ]      door CLOSED
```

**The door is the point.** Step 5.3 proves it.

| # | Step | Expected | Result |
|---|---|---|---|
| 5.1 | All three: Home → Diagnostics. Start the mesh service. | `radio STARTED`, `ready yes` on each | |
| 5.2 | Wait 30 s. On A's Diagnostics, read `discovered`. | B and C both appear | |
| 5.3 | **Confirm C does NOT appear as connected on A.** | `bound` is 1, marked `*` | |
| 5.4 | Walk B out of A's range and back. | A's bound count drops to 0 and returns | |
| 5.5 | Move B next to A. | B reconnects; C still absent | |
| 5.6 | Put all three in the same room, no door. | **A now binds 2 peers and C appears** | |

5.3 and 5.6 together prove you understand the topology you are testing, and
5.6 confirms that when the range *is* shared, the cluster forms the full mesh
Google documents.

If nothing is discovered at all, go to section 9 first. A silent empty peer
list is the most common failure and it is almost always a permission.

---

## 6. A → B → C relaying

The test the project exists for. Complete section 5 first.

| # | Step | Expected | Result |
|---|---|---|---|
| 6.1 | On A: send an SOS addressed to C. | A shows **Handed to a nearby device** | |
| 6.2 | Watch A for 10 s. | Changes to **Confirmed received** only after C acks | |
| 6.3 | On C: open the message. | Body intact | |
| 6.4 | On C: check the hop count. | **2** — A → B → C | |
| 6.5 | On B's Diagnostics, read the event log. | A `RELAY` line naming B, and B's inbox count of received messages is 0 | |
| 6.6 | On B: confirm no message body is displayed anywhere. | Nothing to read | |

**6.4 is the whole test.** If it says 1, the phones were in range of each
other and nothing was relayed. Go back to section 5.

**6.5 and 6.6 matter as much.** A relay must carry a message it has no way to
read. If B shows the body, the envelope is being decoded and displayed on
intermediate hops, which is a privacy defect and not a cosmetic one.

**6.2 matters most of all.** If A shows "delivered" before C confirms, the
distinction between handed-over and confirmed has been lost, and every claim
this project makes about honest delivery reporting is void.

---

## 7. Offline operation

| # | Step | Expected | Result |
|---|---|---|---|
| 7.1 | Put all three in aeroplane mode. | | |
| 7.2 | Wait 30 s for connections to drop. | Peer lists empty | |
| 7.3 | On A: send an SOS to C. | Relays and confirms, as in 6.2 | |
| 7.4 | Repeat with all three on the same Wi-Fi, router unplugged. | Same | |

Nearby Connections is peer-to-peer and routes through no infrastructure, so
aeroplane mode should make no difference at all. **If it does, stop and report
it**: it means something in the app is reaching for a network the radio does
not need.

## 8. Queue recovery

This is what store-and-forward is for.

| # | Step | Expected | Result |
|---|---|---|---|
| 8.1 | Move B into another room. | A and C each lose B | |
| 8.2 | On A: send an SOS addressed to C. | Queued. Not failed, not delivered | |
| 8.3 | Wait 60 s. Read A's Diagnostics. | `STATE ... QUEUED` and then `SENT_TO_PEER`, never `FAILED` | |
| 8.4 | Bring B back. | Joins within ~10 s | |
| 8.5 | Within 15 s. | The queued message is delivered and confirmed | |

8.3 is where a broken implementation shows. A router that gives up and marks
the message `FAILED` has destroyed an emergency message because the radio was
briefly busy. Inside its lifetime it should be kept.

Now let it expire, once, to see the other behaviour:

| # | Step | Expected | Result |
|---|---|---|---|
| 8.6 | Repeat 8.1–8.2, wait past the message lifetime (30 min default) | State becomes **Too old to send**, never "delivered" | |

---

## 9. Failure injection

| # | Step | Expected | Result |
|---|---|---|---|
| 9.1 | Revoke Nearby Devices permission on B while B relays | B stops relaying; A and C stay up; A's queue intact | |
| 9.2 | Turn B's screen off mid-relay | Message still relays; the foreground service is doing its job | |
| 9.3 | Force-stop B mid-relay | A re-queues and retries; no silent loss | |
| 9.4 | Send the same SOS from A to C twice | C receives it once; the duplicate is suppressed | |
| 9.5 | Kill B's app and restart it | B rejoins; **its device id is unchanged** | |
| 9.6 | Send an SOS, then revoke B's permission, then restore it | After restore, B is trusted again without re-verification | |

9.5 depends on the device key being stored, so a restart must not present as a
new, untrusted device. 9.6 depends on the trust store surviving a permission
change; if B comes back as `UNTRUSTED` and every message from it is refused,
that is a defect worth reporting.

### Peer verification

The first time a peer appears, the app learns its key as **unverified**. Trust
is a human act:

| # | Step | Expected | Result |
|---|---|---|---|
| 9.7 | On A: Nearby devices → the peer → Verify | A shows the peer's short code | |
| 9.8 | Confirm on B that the codes match | | |
| 9.9 | After verifying, send a message | Accepted | |
| 9.10 | Without verifying, check A's Diagnostics for a `REJECTED` line | `UNTRUSTED_ORIGIN` | |

9.10 is not a failure, it is the design: a peer's key is self-asserted until a
person confirms it, and messages from an unconfirmed peer are refused with a
reason rather than silently dropped.

---

## 10. Direct messaging

With the phones in the same room (the full-mesh case):

| # | Step | Expected | Result |
|---|---|---|---|
| 10.1 | On A: send a message addressed directly to C | C receives it | |
| 10.2 | On A's Diagnostics, read `forwards` | 1 | |
| 10.3 | Confirm B's inbox did not receive it | 0 | |

A directed message in a full cluster goes straight to its addressee rather
than to everyone. If B receives it, the addressing is wrong.

---

## 11. Troubleshooting

Work top to bottom; in order, this is what actually goes wrong.

**No peers discovered at all**

1. Bluetooth on, on every phone?
2. All three of Scan, Connect and **Advertise** granted?
3. Location services enabled **system-wise**?
4. **Is advertising actually running?** The Diagnostics screen says so
   explicitly. Without Advertise, `startAdvertising` throws and nothing is
   discoverable while discovery still appears healthy.
5. All three on the same Wi-Fi band, or all three off Wi-Fi?
6. Battery optimisation disabled for ResQNet?
7. Screen on for now, retry, then test screen-off separately.
8. Reboot one phone. Android's Nearby stack sometimes wedges after a permission
   change and a reboot is the honest fix.

**Discovered but never connected**

- The cluster strategy must match on both sides. All three must advertise
  `P2P_CLUSTER`; a phone left on a different strategy will never be discovered
  at all.
- Check that the endpoint is not in a failed state. Read the Diagnostics event
  log for a `transport-error`.

**Peers connected but no message passes**

1. A's Diagnostics: what state is the message in? `QUEUED` with a bound peer
   means it is waiting for a retry window.
2. Is the destination actually bound? A directed message to a device the
   sender cannot see is queued, not lost.
3. Check for `REJECTED` lines and the reason. `UNTRUSTED_ORIGIN` means peer
   verification, section 9.7.

```bash
adb logcat -s ResQNet:V ResQNetTransport:V ResQNetMesh:V AndroidRuntime:E
```

**Everything delivered but nothing confirmed**

The acknowledgement path specifically. The relay's copy is fine and the
confirmation is not coming back. On the destination, confirm the message is
present, and on the origin confirm the message is still unconfirmed rather than
having been marked failed.

**Devices visible but the UI is empty**

Check the foreground service notification. A service that is not foregrounded
is killed by Android within minutes, which looks exactly like a routing failure
and is not one.

---

## 12. Recording results

For every failure, capture:

- Model and **Android version** for every device involved, and the Google Play
  services version.
- Whether it is reproducible or intermittent. Intermittent usually means
  timing or retry; consistent usually means logic.
- Whether the phones were within radio range of each other, and which hop
  count was reported. This single fact explains more failures than any other.
- The full text of the Diagnostics screen from every phone involved.
- `adb logcat` from app start to failure.
- Distance and number of walls between the phones.

Those six things are enough to reproduce it. Without them a radio bug report
is usually not actionable, and it is unfair to whoever has to fix it.

---

## 13. What a complete pass still does not prove

- **Range.** Every test here is at two metres. Real range through rubble is a
  different and much harder question, and nothing in this repository is
  evidence about it.
- **Battery.** A phone in a pocket on battery is not a phone on mains. That
  takes hours, not minutes.
- **Interference.** No congested 2.4 GHz environment was used.
- **Scale.** Three phones is the minimum that proves a relay. Flood routing at
  100 nodes is simulated and never radio-tested.
- **Android's own reliability.** Nearby Connections has known vendor-specific
  issues. A failure here may be a platform bug, and telling those apart needs
  the log capture from section 12.
- **Security against a hostile peer.** Signature verification and trust are
  unit tested. Nobody has actually tried to forge a message over the air.