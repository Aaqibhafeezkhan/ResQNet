# Infrastructure

Local orchestration for the optional coordination stack.

| File | Purpose |
| --- | --- |
| `docker/docker-compose.yml` | PostgreSQL plus the gateway |
| `docker/backend.Dockerfile` | Multi-stage image for the gateway |
| `docker/.env.example` | Template for the required secrets |

## Running

```bash
cd infrastructure/docker
cp .env.example .env          # then fill in both secrets
docker compose up --build
```

## What this is not

The emergency network does not need any of it. With every container stopped,
handsets still discover each other, still relay, and still deliver. The
coordination stack exists so that several devices' reports can be looked at on
one screen once somewhere has connectivity again.

That separation is the reason the compose file is optional rather than part of
the core install: a backend outage must never become a communications outage.
