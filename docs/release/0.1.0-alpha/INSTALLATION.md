# Installation — ResQNet v0.1.0-alpha

**Experimental developer preview.** No Android binary is published in this
release because physical-device networking has never been tested.

## Contents

| File | Purpose |
|---|---|
| `resqnet-simulator-v0.1.0-alpha.jar` | Self-contained simulator and demonstration. No external services. |
| `resqnet-backend-v0.1.0-alpha.jar` | Spring Boot coordination gateway. Requires PostgreSQL. |
| `resqnet-web-v0.1.0-alpha.zip` | Production static assets for the command centre. |
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

## Requirements

| Component | Requires |
|---|---|
| Simulator | Java 21+ |
| Backend | Java 21+, PostgreSQL 14+ |
| Web dashboard | Any static file server, plus a reverse proxy |

Building from source additionally needs Maven 3.9+ and Node.js 21+. Neither is
needed to *run* the published jars.

The Android app is not part of this release. To build it from source, see the
repository `Readme.md`; to test it, see `apps/android/DEVICE_TESTING.md`.

---

## Simulator

No configuration, no database, no network.

```sh
# The seven-stage demonstration
java -cp resqnet-simulator-v0.1.0-alpha.jar \
  dev.resqnet.mesh.simulator.demo.DemoScenario

# A seeded mesh
java -jar resqnet-simulator-v0.1.0-alpha.jar \
  --topology mesh --nodes 12 --messages 40 --seed 7 --rounds 60

# The benchmark sweep that produced docs/benchmarks/results.md
java -cp resqnet-simulator-v0.1.0-alpha.jar \
  dev.resqnet.mesh.simulator.benchmark.BenchmarkSuite --seed 42
```

`--seed` makes a run byte-for-byte reproducible. The same seed and topology
always produce the same delivery, latency, and duplicate counts.

---

## Backend

### Database

```sh
createdb resqnet
psql -c "CREATE USER resqnet WITH PASSWORD 'choose-a-real-password';"
psql -c "GRANT ALL ON DATABASE resqnet TO resqnet;"
```

Flyway creates the schema on first start from
`services/backend/src/main/resources/db/migration/V1__initial_schema.sql`.
Hibernate is set to `validate`, so a missing migration is a loud startup
failure rather than silent schema drift.

**No H2 driver is bundled.** H2 is a test-scope dependency only; pointing the
jar at H2 fails at startup by design, so an accidental in-memory deployment
cannot look like a working one.

### Configuration

Every value has a default. The two security secrets have deliberately **no**
default, and they fail in opposite directions:

- **`RESQNET_JWT_SECRET` fails closed.** Without it the process refuses to start
  (outside the `dev` profile), because responder sessions would otherwise be
  signed with a fixed, publicly known key.
- **`RESQNET_GATEWAY_TOKEN` fails safe.** The process starts normally, but an
  unset token matches nothing, so every device sync is refused with 401. No
  device can report until you set it. Verified: with no token configured, a
  request with no `Authorization` header, an empty bearer, and a guessed bearer
  all return 401.

| Variable | Default | Notes |
|---|---|---|
| `RESQNET_DB_URL` | `jdbc:postgresql://localhost:5432/resqnet` | JDBC URL. |
| `RESQNET_DB_USER` | `resqnet` | |
| `RESQNET_DB_PASSWORD` | `resqnet` | **Change this.** It is a development convenience. |
| `RESQNET_JWT_SECRET` | *(empty)* | **Required.** Startup *fails* without it, unless the `dev` profile is active. |
| `RESQNET_GATEWAY_TOKEN` | *(empty)* | Not required to start. Without it **every** device sync is refused with 401, because an unset token can never match. Devices then simply cannot report. |
| `RESQNET_TOKEN_TTL_MINUTES` | `720` | Responder session lifetime. |
| `RESQNET_REQUIRE_HTTPS` | `true` | Secure cookies and redirect require HTTPS. Set `false` only for local HTTP development. |
| `SERVER_PORT` | `8080` | |
| `SPRING_PROFILES_ACTIVE` | *(none)* | `dev` enables the OpenAPI schema and Swagger UI. |

Generate the two mandatory secrets:

```sh
export RESQNET_JWT_SECRET=$(openssl rand -base64 48)
export RESQNET_GATEWAY_TOKEN=$(openssl rand -hex 24)
```

Never commit either. Both are read from the environment; nothing in the
repository contains a usable secret.

The database password does default to `resqnet`, matching a localhost
development PostgreSQL. That is a convenience, not a safe default for a
deployed system — set it explicitly.

### Start

```sh
java -jar resqnet-backend-v0.1.0-alpha.jar
```

Verify:

```sh
curl -fsS http://localhost:8080/actuator/health
```

Health details are never shown, so a response of `{"status":"UP"}` is all you
get — that is intentional, so the endpoint cannot describe the deployment to an
unauthenticated caller.

### The first responder account

**There is no self-service signup, and there is no admin API to create
accounts** in this release. `/api/v1/admin/**` is reserved and requires an
`ADMIN` principal, but no controller serves it yet.

So a first account has to be created deliberately. Two options:

**For local evaluation**, start with the `dev` profile. It seeds two accounts
when the database is empty:

```sh
java -jar resqnet-backend-v0.1.0-alpha.jar --spring.profiles.active=dev
```

| Username | Password | Role |
|---|---|---|
| `responder` | `resqnet-dev` | RESPONDER |
| `admin` | `resqnet-dev` | ADMIN |

**Never deploy with this profile active.** The password is in the source, and
the profile also publishes the OpenAPI schema and Swagger UI.

**For anything else**, create the account yourself. There is no API for this, so
it is a deliberate database write. This exact statement was verified against a
running jar with no `dev` profile:

```sh
HASH=$(python3 -c "import bcrypt;print(bcrypt.hashpw(b'your-real-password',bcrypt.gensalt(rounds=12)).decode())")

psql -c "INSERT INTO responder_accounts
         (username, display_name, password_hash, role)
         VALUES ('responder', 'First Responder', '$HASH', 'RESPONDER');"
```

`role` is one of `OBSERVER`, `RESPONDER`, `ADMIN`. Roles are assigned here, by a
human, and nowhere else — no client request can create or elevate one.

### Creating an incident

For anyone writing a client against the API, the exact working shape is:

```sh
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"responder","password":"resqnet-dev"}' | jq -r .token)

curl -s -X POST http://localhost:8080/api/v1/incidents \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Street flooded","category":"FLOODING","severity":3}'
```

`category` must be one of the exact enum constants — `MEDICAL`, `FLOODING`,
`FIRE`, `TRAPPED`, `MISSING_PERSON`, `SUPPLIES`, `GENERAL`, `UNKNOWN`. A
misspelling is rejected with `MALFORMED_BODY` and the message "The request body
could not be read as JSON", which is misleading: the JSON parsed fine, the field
value did not. Check the spelling if you get that error.

The live event stream authenticates through the subprotocol channel, because a
browser cannot set an `Authorization` header on a WebSocket. A client must
negotiate `resqnet.v1` and pass the session token as a second protocol token
prefixed `bearer.`:

```
Sec-WebSocket-Protocol: resqnet.v1, bearer.<jwt>
```

A handshake without it completes and is then closed before the client can
subscribe to anything.

### Security defaults

- OpenAPI and Swagger UI are **off** by default. An exposed schema is a
  complete map of the API for an attacker.
- Actuator exposes only `health`, `info`, `metrics`. Everything else is denied.
- Stack traces and exception messages are never returned to a caller.
- Unmatched paths are denied outright (`anyRequest().denyAll()`).
- `/api/v1/admin/**` requires the `ADMIN` role.
- Gateway and simulator write endpoints do their own authentication in the
  controller, because a device presents a shared token rather than a responder
  session.

---

## Web command centre

```sh
unzip resqnet-web-v0.1.0-alpha.zip -d resqnet-web
```

The build talks to `/api/v1/...` and `/ws/incidents` as **same-origin relative
URLs**. There is nothing to configure in the shipped bundle: put it behind
something that forwards those two paths to the backend.

`VITE_GATEWAY_WS` can override the WebSocket URL, but it is read at **build**
time, so changing it requires rebuilding rather than editing the bundle.

### nginx

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

### Any static server

`python3 -m http.server` will serve the files but will not proxy `/api` or
`/ws`, so the dashboard will load and then fail to reach the backend. Use a
real reverse proxy.

---

## Reproducing this build from source

```sh
git clone https://github.com/Gautam-aman/ResQNet.git
cd ResQNet
scripts/build_release_artifacts.sh --exclude-android
```

The jars are byte-reproducible: two builds of the same commit yield identical
digests. `resqnet-web-v0.1.0-alpha.zip` is not, because zip stores its own
modification timestamps.

Run the tests before trusting any of it:

```sh
mvn clean verify                        # 248 tests
npm --prefix apps/web ci                # then lint, tsc, vitest
```
