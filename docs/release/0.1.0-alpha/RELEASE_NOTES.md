# ResQNet v0.1.0-alpha

**Experimental Developer Preview**

ResQNet is an experimental offline-first emergency communication platform
exploring decentralized messaging, multi-hop routing, store-and-forward
delivery, and incident coordination.

> **Read this first.** This is an early developer preview of research
> software. It has never been run between two physical Android phones. It is
> **not** a replacement for official emergency services, and it must not be
> relied upon as one. Do not deploy it where a failure would cost lives.

---

## Included

- Java-based distributed mesh routing engine
- Deterministic network simulator
- Reproducible multi-hop routing demonstrations
- Spring Boot emergency coordination backend
- PostgreSQL persistence with versioned migrations
- React emergency command center
- Real-time gateway integration over WebSocket
- Automated testing and CI

## Technical highlights

### Peer identity

Every device holds an Ed25519 key pair generated on first launch. Its stable
identifier is `rqn1_` followed by a truncated SHA-256 fingerprint of the public
key, so a device keeps the same identity across restarts and reinstalls of
application state. Messages are signed end to end; a relay forwards an opaque
envelope it has no means to read, and an intermediate device is never shown a
message body it merely carried.

Peer trust is a human act. A key is *unverified* until a person compares a short
code on two devices, and messages from an unverified peer are refused with an
`UNTRUSTED_ORIGIN` reason rather than silently dropped.

### Controlled flooding and deduplication

Delivery is flooding, not routing: a node rebroadcasts an envelope it has not
seen before. Two bounds keep that from being quadratic. Split horizon suppresses
retransmission back toward the node the copy arrived from, and every envelope
carries a message ID that is remembered for a bounded window, so duplicates are
dropped rather than amplified.

`ClusterTopologyTest` asserts the consequence that matters: in a fully
connected cluster a message reaches a third device in **one** hop and no relay
participates, while in a chain topology the same message crosses the relay in
**two** hops without the relay displaying its body.

### Message acknowledgments

A message is only reported delivered once the origin receives an end-to-end
acknowledgment from the destination. Handing a message to a nearby device and
having it confirmed are distinct states in the UI, because the difference
between "I gave it to someone" and "someone received it" is the difference
between a working queue and a false promise.

### Offline message persistence

Messages that cannot be routed are held in a durable queue and retried on a
timer within their lifetime, rather than failed. The queue survives process
death and app restart. A message that outlives its lifetime expires as *too old
to send* — it is never marked delivered.

### Routing behavior under network partitions

`docs/benchmarks/results.md` records the same seed run twice, once partitioned
and once healed. Messages queued during the partition are delivered after it
heals. This is the property store-and-forward exists to provide, and it is
verified in simulation.

### Measured simulation performance

From `docs/benchmarks/results.md`, seed 42, 8 CPUs, JVM 21.0.7:

| Nodes | Delivered | Median | p95 | Amplification | Forwards | Dupes dropped |
|---:|---:|---:|---:|---:|---:|---:|
| 10 | 69 | 375 ms | 375 ms | 8.7× | 920 | 1 941 |
| 50 | 237 | 375 ms | 375 ms | 7.9× | 868 | 22 323 |
| 100 | 593 | 375 ms | 375 ms | 7.2× | 789 | 51 228 |
| 500 | 3 704 | 500 ms | 625 ms | 4.6× | 691 | 239 558 |

**These are simulation results, not field measurements.** They describe the
routing engine against a modelled radio. Nothing here is a measurement of
Nearby Connections, Wi-Fi Direct range, real latency through rubble, or real
battery life. The delivery figures describe a simulation whose message
lifetime and retry windows were chosen by the author.

## Known limitations

- **Physical Android networking has not been tested.** The app has never been
  run on hardware. No two phones have ever exchanged a message.
- **No Android APK is included in this release**, for that reason. The Android
  source is in the repository and the build is reproducible; binaries will follow
  once `apps/android/DEVICE_TESTING.md` has been executed and its results
  recorded.
- **Simulator results are not real-world radio measurements.** Every number
  above comes from a discrete-event model.
- **Real-world communication range and battery impact are unknown.** Nothing in
  this repository is evidence about either.
- No load, soak, or battery testing has been performed.
- **The simulator cannot publish to the backend.** `SimulatorCli --gateway`
  exists but does not work: it posts a run summary, while the `/api/v1/gateway/sync`
  endpoint expects per-message records. The endpoint is answered and validated
  correctly — the sender is what is unfinished. The dashboard's live event
  stream over WebSocket works independently of this.
- The shared gateway token is a bearer secret: revocable by rotation, but not
  attributable, and every device holding it shares one identity.
- PostgreSQL under concurrent writes and replication is untested. Migrations
  and the incident lifecycle are tested.
- Known defects found by reading this code were fixed, but fixing them is not
  evidence that the system works.

**This software is experimental. It must not be relied upon as a replacement
for official emergency communication services.**

## Getting started

Prerequisites: **Java 21 or newer**, **Maven 3.9+**, **PostgreSQL 14+**, and
**Node.js 21+** for the web dashboard.

### 1. Run the simulator

No infrastructure required.

```sh
java -cp resqnet-simulator-v0.1.0-alpha.jar \
  dev.resqnet.mesh.simulator.demo.DemoScenario
```

A seven-stage demonstration. It prints a warning that it is a simulated radio,
runs each stage, and exits non-zero if any stage misbehaves.

A single seeded simulation:

```sh
java -jar resqnet-simulator-v0.1.0-alpha.jar \
  --topology mesh --nodes 12 --messages 40 --seed 7 --rounds 60 --quiet
```

### 2. Start PostgreSQL

```sh
createdb resqnet
psql -c "CREATE USER resqnet WITH PASSWORD 'choose-a-real-password';"
psql -c "GRANT ALL ON DATABASE resqnet TO resqnet;"
```

### 3. Launch the backend

`RESQNET_JWT_SECRET` is mandatory: without it the backend **refuses to start**,
because responder sessions would otherwise be signed with a publicly known key.
`RESQNET_GATEWAY_TOKEN` is not needed to start, but without it every device sync
is refused with 401, so devices cannot report. Set both, and do not reuse the
values shown.

```sh
export RESQNET_DB_URL=jdbc:postgresql://localhost:5432/resqnet
export RESQNET_DB_USER=resqnet
export RESQNET_DB_PASSWORD='choose-a-real-password'
export RESQNET_JWT_SECRET=$(openssl rand -base64 48)
export RESQNET_GATEWAY_TOKEN=$(openssl rand -hex 24)

java -jar resqnet-backend-v0.1.0-alpha.jar --spring.profiles.active=dev
```

The `dev` profile seeds two demonstration accounts and switches on the API
documentation. It is for local evaluation. **Never deploy with it active**: the
seeded accounts use a password printed in the source, and the schema becomes
publicly readable. A production database gets its first account from a migration
run by a human, because there is no self-service signup and that is
deliberate.

It listens on port 8080. Flyway applies the schema migration on first start;
Hibernate validates rather than mutates the schema. There is no bundled H2
driver — H2 is a test-scope dependency, and pointing the jar at H2 fails at
startup by design.

Check it:

```sh
curl -fsS http://localhost:8080/actuator/health   # {"status":"UP"}
```

The OpenAPI schema and Swagger UI are disabled by default and are published
only under the `dev` profile, because an exposed schema tells an attacker what
exists without any probing.

### 4. Serve the web dashboard

The dashboard uses same-origin relative URLs (`/api/v1/...` and
`/ws/incidents`), so it needs a reverse proxy that forwards those two paths to
the backend.

```sh
unzip resqnet-web-v0.1.0-alpha.zip -d resqnet-web
```

```nginx
server {
    listen 80;
    server_name localhost;

    root /srv/resqnet-web;
    index index.html;
    try_files $uri $uri/ /index.html;   # client-side routing

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
```

Serve this over HTTPS in any real deployment: the backend defaults
`RESQNET_REQUIRE_HTTPS=true`.

The WebSocket is authenticated through the subprotocol channel rather than a
header, because a browser cannot set an `Authorization` header on a socket. The
client negotiates `resqnet.v1` and carries the session token as a second
protocol token prefixed `bearer.`. A connection without it is closed during the
handshake, before it can subscribe.

### 5. Open the command center

Browse to `http://localhost/` and sign in as `responder` / `resqnet-dev`. The
dashboard then live-updates as incidents change.

### 6. Run the demonstration

The seven-stage demonstration needs nothing running. It composes an SOS with no
network, delivers it through one relay, drops the relay mid-flight, requeues and
delivers on recovery, shows that a relay never displays a body it carried,
distinguishes end-to-end acknowledgement from one successful hop, and drives a
responder triage. It prints its own warning that the radio is simulated, and
exits non-zero if any stage misbehaves.

```sh
java -cp resqnet-simulator-v0.1.0-alpha.jar \
  dev.resqnet.mesh.simulator.demo.DemoScenario
```

## Contributing

Issues and pull requests are welcome, and responsible bug reports are
especially valuable — particularly anything about routing correctness, message
integrity, or security.

Please read `docs/security/threat-model.md` before proposing a change to the
trust or transport model, and state honestly whether a finding was reproduced,
read, or inferred. Several defects in this codebase were found by reading it
rather than by running it, and the difference between a reproduced and an
inferred report is worth a lot to whoever triages it.

If you have tested the Android app on real hardware, that result is worth more
than anything else you could send. See `apps/android/DEVICE_TESTING.md`.

## License

MIT. See the repository root.