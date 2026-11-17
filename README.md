# Orbit

Distributed job scheduler and worker platform with a web dashboard, user roles and built-in observability. Jobs are stored in PostgreSQL and picked up by workers over gRPC.

## Setup

```bash
cp .env.example .env
docker compose up --build -d
```

Open http://localhost:8080 and sign in with `ORBIT_ADMIN_USERNAME` and `ORBIT_ADMIN_PASSWORD` from `.env`. Prometheus runs on :9091 and Grafana on :3000. Set `ORBIT_PROMETHEUS_URL` and `ORBIT_GRAFANA_URL` when they are not on localhost, or leave them empty to hide the links.

The values in `.env.example` are for local development only. For production, set `ORBIT_ENV=production` with unique random secrets (32+ characters), put the certificates in `tls/` and start with the TLS override:

```bash
docker compose -f docker-compose.yml -f docker-compose.production.yml up -d
```

## Usage

Submit a job from the dashboard, or with the API:

```bash
set -a; source .env; set +a

curl -sS http://localhost:8080/api/v1/jobs \
  -H "Authorization: Bearer $ORBIT_API_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"demo-team","taskType":"echo","payload":{"message":"hello"},"priority":5}'
```

Task types: `echo`, `sleep`, `sum`.

## Tech Stack

- Java 21, Spring Boot
- PostgreSQL, Redis, gRPC
- React, Vite
- Prometheus, Grafana, OpenTelemetry

## Development

```bash
./mvnw test

cd ui
npm ci
npm run dev
```

## License

MIT. See [LICENSE](LICENSE).
