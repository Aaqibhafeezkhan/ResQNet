# ResQNet

Peer-to-peer emergency messaging that keeps working when the network does not.

When cell towers lose power, so does the internet. Bluetooth and Wi-Fi Direct do
not: the radios are in the phones, the batteries are in the phones, and the
people who need to talk are holding the phones. ResQNet is the layer that makes
those radios useful to each other.

A handset sends an SOS. It is handed to whichever nearby devices are connected.
They hand it on. The message keeps moving until it reaches the device that owns
the responder, or until its lifetime runs out. No server is involved at any
point. The coordination gateway is strictly optional and is never on the
emergency path.

## Status: v0.1.0-alpha, experimental

[![Release](https://img.shields.io/badge/release-v0.1.0--alpha-blue)](https://github.com/Gautam-aman/ResQNet/releases/tag/v0.1.0-alpha)
[![Java](https://img.shields.io/badge/java-21%2B-0f7b6d)](https://adoptium.net/)

The first public release is
**[v0.1.0-alpha — Experimental Developer Preview](https://github.com/Gautam-aman/ResQNet/releases/tag/v0.1.0-alpha)**,
intended for developers, hackathon reviewers, and contributors.

It ships three artifacts: a self-contained simulator JAR, a Spring Boot backend
JAR, and a static web command centre. **No Android APK is included**, because
physical-device networking has never been tested — see
[Unfinished work](#unfinished-work) before you rely on any of this.

> This is research software. It is not a replacement for official emergency
> services, and it must not be relied upon as one.

## Technology stack

| Layer | Technology |
|---|---|
| Mesh core | Java 21, no framework dependencies, runs unchanged on Android |
| Simulator | Java 21, discrete-event, deterministic from a seed |
| Gateway | Java 21, Spring Boot 3, PostgreSQL, Flyway, JPA/Hibernate |
| Android | Java, Android SDK, Nearby Connections `P2P_CLUSTER`, SQLite, Robolectric |
| Web | React 19, TypeScript, Vite, Zustand, WebSocket with polling fallback |
| CI | GitHub Actions: Java, backend (H2 + PostgreSQL), Android, web |

---

## What is actually implemented

This is a description of the code in this repository, not a roadmap. Anything
not listed here does not exist.

### Mesh core (`packages/mesh-core`, pure Java 21, zero dependencies)

The engine that runs on the handset. It has no third-party runtime
dependencies and compiles against nothing newer than the Android API 26
surface, because the Android app compiles it *from source* rather than
consuming a jar.

- Controlled flooding with split horizon, bounded fan-out and hop limits.
- Duplicate suppression keyed on message id, bounded on three axes
  (entries, bytes, age) so a long-running handset cannot grow without limit.
- Durable store-and-forward. A message queued on a relay is persisted before
  the relay acknowledges it, and survives the process dying.
- Signed envelopes. Every hop carries a signature; the identity is derived from
  the device's X.509 EC public key, so there is no trust-on-first-use and no
  account to lose.
- Offline triage. A keyword classifier runs on the handset with no network, so
  a category and a severity exist before anything is transmitted.
- Application-level acknowledgements, which are the only thing that counts as
  proof of delivery.

### Simulator (`packages/mesh-simulator`)

A discrete-event model of the same routing code. Not a mock: it runs the real
`MeshRouter` with a modelled radio.

- Line, ring, star, mesh, two-cluster and random topologies.
- Link loss, churn and partition modelling.
- Reproducible from a seed. See [Reproducibility](#reproducibility) for exactly
  what is and is not byte-stable.
- Publishes reports straight to a running gateway.

### Coordination gateway (`services/backend`, Spring Boot 3)

Optional. A handset may synchronise with it when it has connectivity, and a
responder console reads from it. **Nothing on the emergency path depends on it
existing.** Message bodies are never sent to it, only delivery metadata.

- Flyway migrations, JPA entities, HMAC-signed JWTs.
- Separate credentials for people and for devices. A device may report; only a
  person may act. The two are not interchangeable.
- Incident lifecycle with an audit trail. Device actions are recorded as
  `DEVICE`, never as a responder role, because the audit trail is the record an
  investigator is guaranteed to read.
- Redis-less, single-node by design. No queue, no broker, no cluster.

### Android app (`apps/android`)

- Nearby Connections via the current `ConnectionsClient` API, `P2P_CLUSTER`.
- Foreground service so the mesh survives the screen turning off.
- SQLite-backed durable queue.
- Compiles mesh-core from source, so a mesh-core change that breaks Android is
  caught by `apps/android/app/build.gradle` rather than at release time.

### Web (`apps/web`, React 18 + Vite)

- A landing page that makes one claim and supports it.
- A responder command centre: queue, detail, live counters, topology.
- The client distinguishes *the gateway is unreachable* from *there are no
  incidents*. Conflating them would let a responder act on a dashboard that is
  quietly reporting an empty queue because it could not reach the server.

---

## Architecture

```
   Android handset A                Android handset C
  ┌───────────────────┐           ┌───────────────────┐
  │ UI / MeshService  │           │ UI / MeshService  │
  │        ↓          │           │        ↑          │
  │  MeshRouter       │  Bluetooth │  MeshRouter      │
  │  (mesh-core)      │ ─────────► │  (mesh-core)     │
  │        ↓          │  / Wi-Fi   │        ↑          │
  │ SQLite queue      │   Direct   │ SQLite queue      │
  └───────────────────┘           └───────────────────┘
            │  (only when a connection happens to exist)
            ▼
   ┌────────────────────────────┐
   │  Coordination gateway      │   Spring Boot
   │  incidents · audit · sync  │   optional, never required
   └────────────────────────────┘
            │
            ▼
   ┌────────────────────────────┐
   │  Responder command centre  │   React
   └────────────────────────────┘
```

The full design, including the reasoning behind each trade-off, is in
[docs/architecture/README.md](docs/architecture/README.md).

### The distinction the whole system rests on

`SENT_TO_PEER` and `ACKNOWLEDGED` are different states and are never collapsed.

A successful Nearby Connections payload transfer proves that one radio hop
succeeded. It does not prove that a person received anything. The UI renders
`SENT_TO_PEER` as "Handed to a nearby device", never as delivered, because a
responder deciding whether to hold a position needs to know the difference.

---

## Running it locally

Requires JDK 21, Maven 3.9+, Node 21+.

```bash
git clone https://github.com/Gautam-aman/ResQNet.git
cd ResQNet

# Java modules: mesh core, simulator, gateway
mvn -q package

# Web
cd apps/web && npm ci && npm run build
```

### Run a simulation

```bash
CP=packages/mesh-simulator/target/classes:packages/mesh-core/target/classes

java -cp $CP dev.resqnet.mesh.simulator.SimulatorCli \
  --topology mesh --nodes 12 --messages 40 --seed 7 --rounds 60 \
  --out /tmp/report.json
```

Useful flags: `--drop 0.2` to lose 20% of frames, `--churn 6` to cut and
restore a link every six rounds, `--partitioned` to start with the busiest
relay offline.

### Run the gateway

```bash
java -jar services/backend/target/backend-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev
```

Requires `RESQNET_JWT_SECRET` (32+ characters) and, to let any device
synchronise, `RESQNET_GATEWAY_TOKEN`. Without them the application refuses to
start, which is deliberate.

Dev seeds two accounts with the password `resqnet-dev`: `responder` and
`admin`. They exist only under the `dev` profile.

To use PostgreSQL rather than H2:

```bash
RESQNET_DB_URL=jdbc:postgresql://localhost:5432/resqnet \
RESQNET_DB_USER=resqnet RESQNET_DB_PASSWORD=resqnet \
java -jar services/backend/target/backend-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev
```

### Build the Android app

```bash
cd apps/android
./gradlew assembleDebug          # installable, debug-signed, internal testing only
./gradlew assembleRelease        # unsigned unless a keystore is configured
```

**Install on a phone:**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds carry the `.debug` application-id suffix, so a debug and a
release build install side by side without replacing each other.

**Release signing.** No keystore is in this repository and none is committed.
Supply one through your own Gradle home:

```properties
# ~/.gradle/gradle.properties — never the project file, never committed
RESQNET_KEYSTORE_FILE=/absolute/path/to/resqnet-release.jks
RESQNET_KEYSTORE_PASSWORD=...
RESQNET_KEY_ALIAS=resqnet
RESQNET_KEY_PASSWORD=...
```

With any of the four missing, `./gradlew assembleRelease` produces an
**unsigned** APK. It does not fall back to the debug key, because a
debug-signed APK is installable and looks exactly like a release until
somebody tries to ship it.

CI takes the same four values from repository secrets.

### Run the web app

```bash
cd apps/web && npm run dev      # http://localhost:5173
```

The command centre is at `/command`. The dev server proxies `/api` and `/ws`
to `localhost:8080`.

### Publish simulator data into a running gateway

```bash
RESQNET_GATEWAY_TOKEN=dev-device-token \
java -cp $CP dev.resqnet.mesh.simulator.SimulatorCli \
  --topology mesh --nodes 14 --messages 50 --seed 11 --quiet \
  --gateway http://localhost:8080/api/v1/simulations
```

Ingestion authenticates as a *device*, using the shared token. This is
deliberate: a simulator is a generator, not a handset, and giving it a human
session would imply an accountability model that does not exist.

---

## Release artifacts

Download the published pre-release rather than building it, if you only want to
look: **[v0.1.0-alpha](https://github.com/Gautam-aman/ResQNet/releases/tag/v0.1.0-alpha)**.

To build the same thing yourself:

```bash
scripts/build_release_artifacts.sh --exclude-android
```

```
dist/
  resqnet-simulator-v<version>.jar   self-contained simulator + demonstration
  resqnet-backend-v<version>.jar     Spring Boot gateway, needs PostgreSQL
  resqnet-web-v<version>.zip         static command centre + serving notes
  SHA256SUMS                         digest of every file above
  RELEASE_NOTES.md  INSTALLATION.md  ARTIFACTS.md
```

`dist/` is deliberately flat, because GitHub release assets are. One layout
means the checksum file names paths that exist wherever you downloaded it.

Without `--exclude-android` the script also assembles APKs into `dist/` for
local hardware testing. They are **not** published: physical-device networking
has never been tested.

```bash
cd dist && shasum -a 256 -c SHA256SUMS   # macOS
sha256sum -c SHA256SUMS                 # Linux
```

The jars are byte-reproducible: two builds of the same commit produce
the same digest. The web zip is not, because zip records its own modification
times. APK digests are not reproducible either, because the Android toolchain
embeds build timestamps.

Run the demonstration without any infrastructure:

```bash
java -cp dist/resqnet-simulator-v*.jar \
  dev.resqnet.mesh.simulator.demo.DemoScenario
```

The backend jar has no bundled H2 driver, because H2 is test scope.
Pointing it at H2 fails at startup by design; it targets PostgreSQL.

Releases are published by a manually dispatched workflow that cannot be
triggered by a push. It verifies tests, digests and the absence of signing
material, and defaults to a dry run.

---

## Tests

| Suite | Count | Command |
|---|---:|---|
| mesh-core | 145 | `mvn -pl packages/mesh-core test` |
| simulator | 45 | `mvn -pl packages/mesh-simulator -am test` |
| backend (H2) | 58 | `mvn -pl services/backend test` |
| backend (PostgreSQL) | 7 | see below, runs in CI against `postgres:16-alpine` |
| Android | 55 | `cd apps/android && ./gradlew testDebugUnitTest` |
| Web | 46 | `cd apps/web && npm run test` |

Seven of these are tagged `slow` because they run whole simulated meshes
rather than methods. They are excluded from an ordinary build so a push stays
quick, and CI asks for them explicitly with `-DexcludedTestGroups=`, so
nothing is silently skipped.

The PostgreSQL integration tests run against a real server and are **not** run
by default, because they need one:

```bash
mvn -pl services/backend -am test \
  -Dspring.profiles.active=postgres \
  -Dtest=PostgresMigrationIT \
  -Dsurefire.failIfNoSpecifiedTests=false
```

with `TEST_PG_URL`, `TEST_PG_USER` and `TEST_PG_PASSWORD` pointing at a live
instance. The `postgres` job in CI does exactly this against a
`postgres:16-alpine` service container, and a separate job boots the whole
application against PostgreSQL and drives a real incident through it.

Those tests cover: every Flyway migration applying to a clean PostgreSQL
database, Hibernate validating the mappings against the schema those
migrations produced, a millisecond timestamp surviving a `timestamptz` round
trip, the message-id uniqueness constraint and the severity CHECK constraint
being enforced by the database rather than only by application code, the
incident lifecycle, and device actions being audited as `DEVICE`.

### The demonstration

```bash
CP=packages/mesh-simulator/target/classes:packages/mesh-core/target/classes
java -cp $CP dev.resqnet.mesh.simulator.demo.DemoScenario
```

Seven stages over the real `MeshRouter` with a modelled radio: composition
with no network, two-hop delivery, a relay vanishing mid-flight, recovery,
gateway sync without content, end-to-end confirmation, and the responder
view. It exits non-zero if any stage misbehaves, and three tests assert on
that result.

### What the tests do not cover

Being explicit about this is more useful than a coverage number.

- **Physical-device radio behaviour is unverified.** Nearby Connections has not
  been run between real handsets in CI or on a desk, because CI has no radios
  and this has not been run on hardware. The procedure for doing so is in
  [apps/android/DEVICE_TESTING.md](apps/android/DEVICE_TESTING.md), and it is
  written as a procedure rather than a result because the result does not exist
  yet.
- **Real-world range and battery life are unknown.** The benchmarks measure the
  routing engine against a modelled radio. Nothing here is a claim about how
  far a message travels through a collapsed building.
- **PostgreSQL under concurrency is untested.** Migrations and lifecycle are
  verified; write contention and replication are not.
- **The WebSocket event stream is not wired to any producer.** See
  [Unfinished work](#unfinished-work).

---

## Reproducibility

`SimulationReport.volatileJsonFields()` names exactly three things that cannot
be byte-stable across runs of the same seed, and everything else is
reproducible:

- `timing.wallClockMillis` and `timing.throughputPerSecond` are measurements.
- `nodes[].deviceId` and `nodes[].keyFingerprint` come from freshly generated
  keys.

Every routing decision, counter, latency and hop distribution is a function of
the seed. This is enforced by a test that forks the CLI in a separate JVM,
because the failure mode it guards against is invisible in-process: the JDK
randomises its immutable collection iteration order once per JVM, so two runs
in one process agree with each other and disagree with every other process.

Verify it yourself:

```bash
python3 scripts/compare_reports.py /tmp/a.json /tmp/b.json
```

---

## Benchmarks

**Delivery figures are computed over directed messages only.** A broadcast has
no destination, so nothing can acknowledge it, and counting broadcasts in the
denominator capped the reported rate below 100% however well the mesh
performed. Both counts are reported separately.

**What limits delivery is the graph, not the router.** `TopologyDiagnostics`
measures the shapes the simulator builds. `MESH` is a circulant band of
diameter n/4, so at 500 nodes only a quarter of node pairs are inside a
32-hop budget; `RANDOM` at the same size has diameter 6. Across hop budgets
at 100 nodes the router reaches 98–100% of what the graph permits, and at
500 nodes the measured 21.6% sits directly under the 25.7% structural
ceiling. The full argument, including seven causes that were tested and
ruled out, is in
[docs/routing-analysis.md](docs/routing-analysis.md), and
`RoutingEfficiencyTest` asserts it so a future regression fails the build
rather than quietly reappearing as a benchmark number.

Full numbers, with the host they were measured on, are in
[docs/benchmarks/results.md](docs/benchmarks/results.md), regenerable with:

```bash
java -cp $CP dev.resqnet.mesh.simulator.benchmark.BenchmarkSuite --seed 42 \
  --markdown docs/benchmarks/results.md --json docs/benchmarks/results.json
```

These are simulation results, not field measurements.

The headline finding is that **hop budget, not mesh size, decides whether a
message crosses**. A flood travels one hop per relay, so a mesh wider than the
hop budget cannot be crossed no matter how long the sender waits. Mesh
topology, one seed, same messages at every size:

| Nodes | 4 hops | 8 hops | 16 hops | 32 hops |
|---:|---:|---:|---:|---:|
| 10 | 90.0% | 90.0% | 90.0% | 90.0% |
| 50 | 15.0% | 50.0% | 90.0% | 90.0% |
| 100 | 24.0% | 44.0% | 76.0% | 88.0% |
| 500 | 2.4% | 4.0% | 10.4% | 21.6% |

At 10 nodes the budget does not matter at all: everything is one or two hops
away. By 500 nodes, even the maximum budget only reaches a fifth of messages,
and the cost of trying is visible in the heap column of the same table.

Other things the sweep shows:

- **Partition is worse than size.** `two-cluster` at 10 nodes acknowledges 42%
  where `mesh` at 10 acknowledges 79%. A partition costs more than ten times
  the node count.
- **Amplification falls as the mesh grows** (8.7x at 10 nodes, 4.6x at 500),
  because the fixed cost of reaching a handful of nodes is amortised over more
  of them. It never approaches 1x, which is the honest price of flooding.
- **Memory scales with the mesh.** 1.7 MiB at 10 nodes to 180 MiB at 500, with
  per-node queues holding relays for messages that have not yet been confirmed.

The full table, with the host, is in
[docs/benchmarks/results.md](docs/benchmarks/results.md).

---

## Security posture

Threat model in [docs/security/threat-model.md](docs/security/threat-model.md).
The parts that are easy to get wrong:

- **No trust on first use.** Identity is a key. A device you have never met is
  `UNVERIFIED`, not trusted.
- **Devices cannot act.** The gateway token authorises reporting only. It can
  never open, assign or close an incident.
- **People and devices use different credentials.** A device cannot obtain a
  responder session, and a responder token cannot push device data.
- **Message bodies never leave the mesh.** Synchronisation sends delivery
  metadata: ids, states, timestamps, attempt counts.
- **The audit trail does not overstate authority.** A gateway-synchronised
  incident is recorded as `DEVICE`, because no human triaged it.

Known limitations are listed in the threat model rather than left implicit. The
most important one: the shared gateway token is a bearer secret, so it is
revocable by rotation but not attributable, and every device holding it shares
one identity.

---

## Unfinished work

Stated plainly so it is not discovered by a reviewer.

- **The simulator cannot publish to the backend.** `SimulatorCli --gateway`
  exists but is unfinished: it posts a run summary, while
  `/api/v1/gateway/sync` expects per-message records, so the gateway rejects it
  with `VALIDATION_FAILED`. The endpoint is correct; the sender is missing. The
  dashboard's own live event stream over WebSocket works independently of this
  and was verified against a running jar.
- **Assignment has no UI.** `POST /incidents/{reference}/assign` exists, is
  audited and is tested, but the command centre has no control for it.
- **Manual incident creation has no UI.** The endpoint works.
- **The physical-device test has not been run.** This is the largest gap in
  the project and it is listed first in the test section above because it is the
  one that matters. Several defects found by auditing the app would each have
  prevented the three-phone demo on their own; they are fixed, but nothing
  proves the radio path works.
- **Multi-hop cannot be observed when the phones are in range of each other.**
  Not a defect, but it will look like one. `P2P_CLUSTER` is an M-to-N strategy:
  every device within radio range (~100 m) connects to every other, so with
  three phones side by side, A to C is *one* hop and no relay is involved. A
  demonstration has to put the phones out of range of each other for the
  relay to exist at all, which is why `DEVICE_TESTING.md` insists on a closed
  door and on confirming C does not appear in A's peer list before sending.
- **No P2P_CLUSTER authentication token is used.** Google's API makes it
  optional and connection-level authentication is not required for the relay to
  work. Trust is enforced one layer up, by envelope signature verification
  against a key the owner confirmed in person, which is the stronger property
  but also means an unauthenticated peer can occupy a connection slot.

---

## Demo

A step-by-step demonstration script, including the exact commands and the
failure injection, is in [docs/demo.md](docs/demo.md).

The short version: cut phone C's connection to the rest of the mesh, send an SOS
from phone A, and watch the command centre keep reporting what it actually
knows rather than what would be convenient.

---

## Contributing

Issues and pull requests are welcome, and responsible bug reports are especially
valuable — particularly about routing correctness, message integrity, or
security.

Read [docs/security/threat-model.md](docs/security/threat-model.md) before
proposing a change to the trust or transport model. When you report a finding,
say honestly whether you reproduced it, read it, or inferred it. Several defects
here were found by reading the code rather than by running it, and the
difference between a reproduced and an inferred report matters a great deal to
whoever triages it.

If you have tested the Android app on real hardware, that result is worth more
than anything else you could send. The procedure is in
[apps/android/DEVICE_TESTING.md](apps/android/DEVICE_TESTING.md), and no
hardware run is recorded yet.

Please keep the "what is verified / what is simulated / what is untested"
distinction intact in any change. It is the most valuable thing in this
repository, and it is easy to erode by accident.

## License

MIT. See [LICENSE](LICENSE).