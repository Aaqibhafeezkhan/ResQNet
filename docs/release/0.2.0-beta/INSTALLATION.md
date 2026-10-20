# Installation — ResQNet v0.2.0-beta

**Experimental pre-release.** This is the first release that includes a signed
Android APK.

## Contents

| File | Purpose |
|---|---|
| `resqnet-v0.2.0-beta-release.apk` | **Android app, release build.** Package `dev.resqnet.android`. Min SDK 26. |
| `resqnet-v0.2.0-beta-debug.apk` | Android app, debug build (`.debug` application id). For side-by-side hardware testing only. Never publish this one. |
| `resqnet-simulator-v0.2.0-beta.jar` | Self-contained simulator and demonstration. No external services. |
| `resqnet-backend-v0.2.0-beta.jar` | Spring Boot coordination gateway. Requires PostgreSQL. |
| `resqnet-web-v0.2.0-beta.zip` | Production static assets for the command centre. |
| `SHA256SUMS` | SHA-256 digest of every other file here. |
| `RELEASE_NOTES.md` | What this release contains and its limitations. |
| `INSTALLATION.md` | This file. |

## Verify the download

Do this first. Digests in `SHA256SUMS` were computed from the uploaded files.

```sh
shasum -a 256 -c SHA256SUMS   # macOS
sha256sum -c SHA256SUMS       # Linux
```

Every line must report `OK`.

For the APK specifically, this is the step that matters most: an APK is an
executable you are about to grant permissions to, and the checksum is the only
thing tying the file you downloaded to the file that was uploaded.

---

## Android app

### Requirements

| | |
|---|---|
| Minimum Android | 8.0 (API 26) |
| Permissions | Nearby devices |
| Network | None required. That is the point. |
| Signing | Release keystore, verified with `apksigner` |

### Install

1. Download `resqnet-v0.2.0-beta-release.apk` from the releases page.
2. Verify it (above).
3. When Android asks whether to install from this source, allow it **only** for
   a file you obtained from this repository's releases page. Android will warn
   because the app is not from the Play Store; that warning is correct and is
   about the distribution channel, not about this build.
4. Install.
5. Grant the **nearby devices** permission when prompted. Without it the app
   cannot discover or connect to anything, and it will say so rather than
   appearing to work.
6. **Enable mesh participation explicitly.** It is a deliberate choice, not a
   default, because a device that silently joins a mesh is a device that can be
   used as a relay without its owner understanding that.
7. Test with a second device.

### Do not install the debug APK

`resqnet-v0.2.0-beta-debug.apk` is included so that a release build and a debug
build can be installed side by side on one handset — it carries the `.debug`
application-id suffix, so installing it does not replace the release build. It
is debug-signed and is not a substitute for the release APK.

### What this build has and has not been tested on

The developer reports the application working on two physical phones. That is a
user report, not independently verified evidence, and it is **not** evidence of
multi-hop routing: two handsets in range of each other connect directly under
`P2P_CLUSTER`, with no third device to relay through.

Not verified: three-device multi-hop delivery, acknowledgement over real radios,
physical range, and battery behaviour. See `apps/android/DEVICE_TESTING.md` for
the procedure, and `RELEASE_NOTES.md` for the full list of limitations.

### Known operational caveats

- **Background restrictions.** Doze mode, battery optimisation and
  manufacturer-specific task killers can suspend the foreground service. If the
  mesh stops working on one handset, check battery optimisation before assuming
  a protocol fault.
- **Payloads are signed, not encrypted.** Signatures give authenticity and
  integrity. They do not give confidentiality, so a device in radio range can
  read a frame it receives.
- **Multi-hop needs distance.** With every phone in range of every other, a
  message goes direct and no relay is used. This is not a defect, but it will
  look like one during a tabletop demonstration.

---

## Simulator

No configuration, no database, no network.

```sh
# The seven-stage demonstration
java -cp resqnet-simulator-v0.2.0-beta.jar \
  dev.resqnet.mesh.simulator.demo.DemoScenario

# A seeded mesh
java -jar resqnet-simulator-v0.2.0-beta.jar \
  --topology mesh --nodes 12 --messages 40 --seed 7 --rounds 60

# The benchmark sweep that produced docs/benchmarks/results.md
java -cp resqnet-simulator-v0.2.0-beta.jar \
  dev.resqnet.mesh.simulator.benchmark.BenchmarkSuite --seed 42
```

`--seed` makes a run reproducible. The same seed and topology always produce the
same delivery, latency and duplicate counts.

### Publish a run into a running gateway

```sh
RESQNET_GATEWAY_TOKEN=<your device token> \
java -jar resqnet-simulator-v0.2.0-beta.jar \
  --topology mesh --nodes 14 --messages 50 --seed 11 --quiet \
  --gateway http://localhost:8080/api/v1/simulations
```

This works. Ingestion authenticates as a *device* using the shared token, and
the gateway stores the run and its events, labels every record `simulated`, and
announces it on the live stream so a dashboard showing the topology updates
without waiting for its next poll. There is no path by which generated data is
presented as field telemetry.

---

## Backend

### Requirements

| Component | Requires |
|---|---|
| Backend | Java 21+, PostgreSQL 14+ |
| Command centre | Any static file server, plus a reverse proxy |

Building from source additionally needs Maven 3.9+ and Node.js 21+. Neither is
needed to *run* the published artifacts.

### Database

```sh
createdb resqnet
psql -c "CREATE USER resqnet WITH PASSWORD 'choose-a-real-password';"
psql -c "GRANT ALL ON DATABASE resqnet TO resqnet;"
```

Flyway creates the schema on first start. Hibernate is set to `validate`, so a
missing migration is a loud startup failure rather than silent schema drift.

**No H2 driver is bundled.** H2 is a test-scope dependency only; pointing the jar
at H2 fails at startup by design, so an accidental in-memory deployment cannot
look like a working one.

### Configuration

Every value has a default. The two security secrets have deliberately **no**
default, and they fail in opposite directions:

- **`RESQNET_JWT_SECRET` fails closed.** Without it the process refuses to start
  (outside the `dev` profile), because responder sessions would otherwise be
  signed with a fixed, publicly known key.
- **`RESQNET_GATEWAY_TOKEN` fails safe.** The process starts normally, but an
  unset token matches nothing, so every device sync is refused with 401.

| Variable | Default | Notes |
|---|---|---|
| `RESQNET_DB_URL` | `jdbc:postgresql://localhost:5432/resqnet` | JDBC URL. |
| `RESQNET_DB_USER` | `resqnet` | |
| `RESQNET_DB_PASSWORD` | `resqnet` | **Change this.** A development convenience. |
| `RESQNET_JWT_SECRET` | *(empty)* | **Required.** Startup *fails* without it, unless the `dev` profile is active. |
| `RESQNET_GATEWAY_TOKEN` | *(empty)* | Not required to start. Without it **every** device sync is refused with 401. |
| `RESQNET_TOKEN_TTL_MINUTES` | `720` | Responder session lifetime. |
| `RESQNET_REQUIRE_HTTPS` | `true` | Set `false` only for local HTTP development. |
| `SERVER_PORT` | `8080` | |
| `SPRING_PROFILES_ACTIVE` | *(none)* | `dev` enables the OpenAPI schema and Swagger UI. |

```sh
export RESQNET_JWT_SECRET=$(openssl rand -base64 48)
export RESQNET_GATEWAY_TOKEN=$(openssl rand -hex 24)
```

Never commit either.

### Start

```sh
java -jar resqnet-backend-v0.2.0-beta.jar
```

Verify:

```sh
curl -fsS http://localhost:8080/actuator/health
```

Health details are never shown, so `{"status":"UP"}` is all you get. That is
intentional, so the endpoint cannot describe the deployment to an unauthenticated
caller.

### The first responder account

**There is no self-service signup, and there is no admin API to create
accounts.** `/api/v1/admin/**` is reserved and requires an `ADMIN` principal, but
no controller serves it yet.

**For local evaluation**, start with the `dev` profile. It seeds two accounts
when the database is empty:

```sh
java -jar resqnet-backend-v0.2.0-beta.jar --spring.profiles.active=dev
```

| Username | Password | Role |
|---|---|---|
| `responder` | `resqnet-dev` | RESPONDER |
| `admin` | `resqnet-dev` | ADMIN |

**Never deploy with this profile active.** The password is in the source, and the
profile also publishes the OpenAPI schema and Swagger UI.

**For anything else**, create the account yourself — it is a deliberate database
write:

```sh
HASH=$(python3 -c "import bcrypt;print(bcrypt.hashpw(b'your-real-password',bcrypt.gensalt(rounds=12)).decode())")

psql -c "INSERT INTO responder_accounts
         (username, display_name, password_hash, role)
         VALUES ('responder', 'First Responder', '$HASH', 'RESPONDER');"
```

`role` is one of `OBSERVER`, `RESPONDER`, `ADMIN`. Roles are assigned here, by a
human, and nowhere else.

### Peer identity

Every device holds an **EC P-256** key pair generated on first launch, and
signs envelopes with `SHA256withECDSA`. Its stable identifier is `rqn1_`
followed by a SHA-256 fingerprint of the public key, so a device keeps the same
identity across restarts and reinstalls of application state.

Peer trust is a human act. A key is `UNVERIFIED` until a person compares a short
code on two devices in person. Messages from an origin whose key is not on file
are refused with `UNTRUSTED_ORIGIN` rather than silently dropped.

### Security defaults

- OpenAPI and Swagger UI are **off** by default.
- Actuator exposes only `health`, `info`, `metrics`. Everything else is denied.
- Stack traces and exception messages are never returned to a caller.
- Unmatched paths are denied outright (`anyRequest().denyAll()`).
- `/api/v1/admin/**` requires the `ADMIN` role.
- Gateway and simulator write endpoints authenticate in the controller, because
  a device presents a shared token rather than a responder session.

---

## Web command centre

```sh
unzip resqnet-web-v0.2.0-beta.zip -d resqnet-web
```

The build talks to `/api/v1/...` and `/ws/incidents` as **same-origin relative
URLs**. There is nothing to configure in the shipped bundle: put it behind
something that forwards those two paths to the backend.

`VITE_GATEWAY_WS` can override the WebSocket URL, but it is read at **build**
time, so changing it requires rebuilding rather than editing the bundle.

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

The `try_files` line matters: without it, deep links such as `/incidents/42`
return 404 on refresh.

Use HTTPS in any real deployment. `RESQNET_REQUIRE_HTTPS` defaults to `true`.

The WebSocket is authenticated through the subprotocol channel rather than a
header, because a browser cannot set an `Authorization` header on a socket:

```
Sec-WebSocket-Protocol: resqnet.v1, bearer.<jwt>
```

A handshake without it completes and is then closed before the client can
subscribe to anything.

---

## Reproducing this build from source

```sh
git clone https://github.com/Gautam-aman/ResQNet.git
cd ResQNet
scripts/build_release_artifacts.sh
```

Omit `--exclude-android` (the default) to include the APKs. Release signing
reads four properties from your own Gradle home and is never read from the
repository:

```properties
# ~/.gradle/gradle.properties — never the project file, never committed
RESQNET_KEYSTORE_FILE=/absolute/path/to/resqnet-release.jks
RESQNET_KEYSTORE_PASSWORD=...
RESQNET_KEY_ALIAS=resqnet
RESQNET_KEY_PASSWORD=...
```

With any of the four missing, the script produces
`resqnet-v0.2.0-beta-release-UNSIGNED.apk`, warns loudly, and records it as
unsigned in the manifest. It does not fall back to the debug key, because a
debug-signed APK is installable and looks exactly like a release until somebody
tries to ship it.

The jars are byte-reproducible: two builds of the same commit yield identical
digests. The web zip and the APK are not, because they record build timestamps.

Run the tests before trusting any of it:

```sh
mvn clean verify                                    # mesh-core, simulator, backend
npm --prefix apps/web ci                            # then lint, tsc, vitest
cd apps/android && ./gradlew testDebugUnitTest lintDebug
```
