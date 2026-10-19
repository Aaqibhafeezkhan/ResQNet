# ResQNet live demo

A demonstration that shows the system working and, more importantly, shows it
being honest when it cannot.

The whole point of ResQNet is a single question: **did they get it?** Every
decision in the project comes back to that. So the demo is built around showing
the answer being honest under failure, not around a happy path where everything
succeeds and nothing is proven.

Timing: eight to twelve minutes. The failure injections are what take the time,
and they are the part worth spending it on.

---

## Before you start

You need:

- A machine that can run the gateway and the web app.
- A browser.
- **Three Android phones** for the hardware section.

If you do not have three phones, the simulated demo in part 2 stands on its own
and the hardware section in part 3 is the part you cannot fake. Say so rather
than implying the radio path was exercised.

Build everything first:

```bash
mvn -q package
cd apps/web && npm ci && npm run build && cd ../..
```

Install the debug APK on all three phones:

```bash
adb install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
```

---

## Part 0 — The runnable demonstration (30 seconds, no infrastructure)

Start here. If this does not work nothing else will, and it needs no gateway,
no database and no phones.

```bash
mvn -q -pl packages/mesh-core install -DskipTests
mvn -q -pl packages/mesh-simulator -am package -DskipTests

CP=packages/mesh-simulator/target/classes:packages/mesh-core/target/classes
java -cp $CP dev.resqnet.mesh.simulator.demo.DemoScenario
```

It prints seven numbered stages and exits non-zero if any of them misbehaves.
It runs the real `MeshRouter`; only the radio is modelled.

What it demonstrates, and what each line proves:

1. **Composition needs no network.** The message is on disk before any send is
   attempted, with zero peers connected.
2. **Two hops, and the relay never reads it.** The relay's inbox stays at zero
   while the destination's reaches one.
3. **A link vanishes mid-flight.** The queued message is still on disk; the
   already-confirmed message is untouched.
4. **Recovery.** A minute later the link returns and the queued message is
   delivered.
5. **Sync carries metadata, not content.** The transcript asserts the payload
   contains no message body.
6. **`SENT_TO_PEER` and `ACKNOWLEDGED` are different states.** Both messages
   end confirmed, and the relay inbox is still zero.
7. **The responder view.** What opens in the console, including the advisory
   flag a device classification carries.

Say, unprompted: "this is a simulation. I have not run this on three phones."

---

## Part 1 — Set up the gateway (90 seconds)

```bash
export RESQNET_JWT_SECRET="demo-secret-that-is-long-enough-to-pass-32"
export RESQNET_GATEWAY_TOKEN="demo-device-token"
export RESQNET_DB_URL="jdbc:h2:file:/tmp/resqnet-demo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
export RESQNET_DB_USER=sa
export RESQNET_DB_PASSWORD=""

java -jar services/backend/target/backend-*.jar \
  --spring.profiles.active=dev &
```

Wait for it to be up:

```bash
until curl -fsS http://localhost:8080/actuator/health | grep -q UP; do sleep 1; done
echo ready
```

**Point out:** the application refused to start earlier if you had not set
`RESQNET_JWT_SECRET`. There is no default signing secret, because a default
secret is a way to lose an entire incident queue.

---

## Part 2 — The simulated demo (3 minutes)

### Seed some incidents from devices

```bash
export RESQNET_GATEWAY_TOKEN=demo-device-token
CP=packages/mesh-simulator/target/classes:packages/mesh-core/target/classes

for topology in mesh ring two-cluster; do
  java -cp $CP dev.resqnet.mesh.simulator.SimulatorCli \
    --topology $topology --nodes 12 --messages 40 --seed 7 --rounds 60 --quiet \
    --gateway http://localhost:8080/api/v1/simulations
done
```

Each prints a delivery rate. `two-cluster` will be markedly lower than `mesh`,
and **that is the point** — a partition has to be routed around, and usually
cannot be.

### Open the command centre

```bash
cd apps/web && npm run dev
```

Open `http://localhost:5173/command`, sign in as `admin` / `resqnet-dev`.

**Point out, in this order:**

1. Every run in the topology panel is labelled **simulated**. Generated traffic
   is never allowed to look like field data.
2. The counters. `Acknowledged` is not `Delivered`. Acknowledged means somebody
   confirmed receipt; delivered only means the bytes arrived somewhere.
3. The amplification figure. One SOS in a 12-node mesh costs about eight
   forwards. That is what flooding costs, and it is why the fan-out is bounded
   rather than unlimited.

### The failure injection that matters

Stop the gateway:

```bash
kill %1          # or pkill -f 'backend-.*\.jar'
```

Wait thirty seconds — one polling interval — and watch the header.

**The header says the gateway is unreachable.** It does not say "0 incidents".

This is the single most important thing to demonstrate. The naive
implementation of this dashboard catches the fetch error and renders an empty
list, which on an emergency console is indistinguishable from a quiet
afternoon. A responder who reads an empty queue as "nothing is happening" during
an outage has been actively misled.

Press Cmd+Period to kill the process, restart it, and the queue comes back.

### Publish a real incident and walk the lifecycle

```bash
curl -X POST http://localhost:8080/api/v1/gateway/sync \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer demo-device-token' \
  -d '{
    "source": "android",
    "deviceId": "rqn1_demo_gateway",
    "records": [{
      "messageId": "demo-001", "state": "ACKNOWLEDGED", "priority": "SOS",
      "type": "EMERGENCY_REPORT", "category": "medical", "advisoryConfidence": 0.72,
      "origin": "rqn1_demo_origin", "destination": "command",
      "createdAt": 1760000000000, "expiresAt": 1760000900000,
      "attempts": 2, "detail": "demo incident"
    }]
  }'
```

The incident appears in the queue marked **Advisory**, because a keyword model
on a handset is a suggestion, not a triage. Triaging it by hand is what promotes
it, and the audit trail records that a person did so.

Click **Triaged**. The incident moves. Now try to close an incident that is
already resolved — the button is disabled, and the reason is the one in the
threat model: the state machine is not a suggestion.

**Point out:** the detail panel shows the device that originated the message
and the device that carried it. That distinction is the entire gateway
authority model.

---

## Part 3 — The hardware demo (4 minutes, three phones)

This is the part that cannot be faked. Have
[apps/android/DEVICE_TESTING.md](../apps/android/DEVICE_TESTING.md) open.

### The A→B→C relay

Label the phones A, B and C. Put them in a line, about two metres apart each,
with the doors of the room closed. Android's radios do not reliably see through
walls, and a demo where the phones can hear each other directly proves nothing
about relaying.

1. On all three: open ResQNet, grant the Nearby Devices permission.
2. On B and C: start the mesh service and leave them alone.
3. On A: start the mesh service, wait until the peer list shows B **and** C.
4. On A: send an SOS addressed to C.

**Point out on A's screen:** the state moves to `SENT_TO_PEER` — "Handed to a
nearby device". It is not "delivered". Only when C acknowledges does A show
confirmed receipt.

**Point out on C's screen:** the message arrives with a hop count. If it says
`hopCount: 2`, it went A → B → C and B relayed it without any internet
connection and without knowing who C was.

### The offline proof

1. Put all three phones in aeroplane mode.
2. Send another SOS from A to C.
3. Walk the message through: it still relays. Nothing about it needs a network.

**Say explicitly:** "This is the point of the whole system, and it is now
demonstrated on real hardware."

### Reconnection

1. Take phone B out of A's and C's range — a different room, or a Faraday
   pouch if one is available.
2. Send an SOS from A to C within the message lifetime.
3. Bring B back.

**Point out:** the message is delivered after the link returns, not before. That
is store-and-forward, and it is the reason a relay persists a message before
it acknowledges one.

### What to say if something does not work

Do not improvise. The test plan in `DEVICE_TESTING.md` has a troubleshooting
section, and the honest move is to say which step failed and read the
counterpart.

If the phones cannot see each other at all, check in this order: Nearby
Devices permission granted; Location permission granted (Android requires it for
peer discovery); both on the same Wi-Fi band or not on Wi-Fi at all; Bluetooth
and Location services enabled system-wide.

---

## Part 4 — Reproducibility, if there is time (60 seconds)

Two runs of the same seed must produce the same report:

```bash
java -cp $CP dev.resqnet.mesh.simulator.SimulatorCli \
  --topology mesh --nodes 12 --messages 30 --seed 42 --rounds 40 --quiet \
  --out /tmp/a.json

java -cp $CP dev.resqnet.mesh.simulator.SimulatorCli \
  --topology mesh --nodes 12 --messages 30 --seed 42 --rounds 40 --quiet \
  --out /tmp/b.json

python3 scripts/compare_reports.py /tmp/a.json /tmp/b.json
```

Output: `reports are reproducible`.

Then change one number:

```bash
sed -i.bak 's/"seed": 42/"seed": 43/' /tmp/a.json
python3 scripts/compare_reports.py /tmp/a.json /tmp/b.json
```

Now it reports differences. The comparison is real in both directions.

**Point out:** the only things that are not byte-identical are the wall-clock
timing and the freshly generated device keys, and those are named explicitly in
`SimulationReport.volatileJsonFields()` rather than quietly tolerated.

---

## Closing — the honest summary

Three sentences, and do not soften them:

1. The routing engine, the codec, the protocol and the gateway are tested and
   reproducible. 260+ automated tests run in CI.
2. The system has **not** been run between physical devices yet.
   `DEVICE_TESTING.md` is written as a procedure because that work has not been
   done. Part 3 above is only valid if you have actually done it.
3. The benchmark numbers describe a modelled radio. Nothing in this repository
   is evidence about how far a message travels through rubble.