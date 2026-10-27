# Grievance360 — Smart Public Grievance Redressal System

Citizens file public-service complaints, track them against a resolution target and talk to the responsible department. Officers work a department queue; administrators manage departments, users, assignments, escalation rules and analytics.

> Prototype built for a Smart India Hackathon demonstration. It is not affiliated with, endorsed by or integrated with any government body. All demo data is fictional.

## Stack

| Layer | Technology |
|---|---|
| Frontend | React 18, Vite 5, TypeScript, Tailwind CSS, shadcn/ui-style components (Radix), React Router, Axios, React Hook Form + Zod, Recharts, Lucide |
| Backend | Java 21, Spring Boot 3.3 (Web, Security, Data JPA, Validation, Actuator), Flyway, JJWT 0.12, springdoc-openapi |
| Database | PostgreSQL 16 |
| Infra | Docker multi-stage builds, Docker Compose, nginx (static frontend) |

## Project structure

```
backend/                       Spring Boot modular monolith
  src/main/java/com/grievance360/
    auth/        register, login, current user
    user/        profile, admin user management
    department/  departments & categories (SLA hours, default priority)
    complaint/   complaints, workflow policy, history, comments, attachments, public lookup
    dashboard/   SQL aggregates for citizen / officer / admin dashboards and SLA report
    escalation/  scheduled SLA escalation and auto-close
    notification/ audit/ settings/ storage/ security/ common/ config/ seed/
  src/main/resources/db/migration/V1__schema.sql   Flyway schema
  src/test/...                  unit + integration tests (PostgreSQL)
frontend/
  src/lib/        API client, auth context, schemas, types, formatting
  src/components/ UI kit (ui/), app shell, charts, shared states
  src/pages/      public/, citizen/, officer/, admin/, shared/
  src/test/       Vitest + Testing Library
docs/             SECURITY.md, DEPLOYMENT.md, API.md
docker-compose.yml  .env.example
```

## Quick start (Docker)

**1. Prerequisites:** Docker Desktop (or Docker Engine 24+ with Compose v2). Nothing else is needed for the Docker path. For local development without Docker you need JDK 21, Maven 3.9, Node 20+ and PostgreSQL 16.

**2. Configure environment**

```bash
cp .env.example .env
```

Edit `.env` and set at least:

- `POSTGRES_PASSWORD` — any strong value
- `JWT_SECRET` — at least 32 bytes, e.g. output of `openssl rand -base64 48`

`.env` is gitignored; never commit it.

**3. Build and start everything**

```bash
docker compose up --build
```

(add `-d` to run in the background). Startup order is enforced by health checks: PostgreSQL → backend → frontend.

**4. Migrations** run automatically: Flyway applies `backend/src/main/resources/db/migration` when the backend starts, and Hibernate validates the schema against the entities (`ddl-auto: validate`). Check with:

```bash
docker compose logs backend | grep -i flyway
```

| Service | URL |
|---|---|
| Web app | http://localhost:5173 |
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| PostgreSQL | localhost:5432 (bound to 127.0.0.1 only) |

### 5. Create a development admin safely

No admin account or default password exists by default. To bootstrap the first administrator, set these in `.env` and restart the backend:

```
ADMIN_BOOTSTRAP_EMAIL=you@example.org
ADMIN_BOOTSTRAP_PASSWORD=<at least 12 chars, letters and digits>
```

```bash
docker compose up -d backend
```

The account is created only if no active admin exists. Afterwards **remove both variables** from `.env` (the log reminds you). Use the admin UI (Users & roles) to create officers and further admins.

### 6. Optional demo data (development only)

Set `SPRING_PROFILES_ACTIVE=dev,demo` in `.env` **before the first start** (or after a reset). The `demo` profile seeds 5 departments, 11 categories, 10 officers, 4 citizens and ~48 complaints spread over 60 days with realistic histories. It only runs when the database has no departments and refuses to run alongside the `prod` profile.

All demo accounts use the password `Demo@12345`:

| Role | Email |
|---|---|
| Admin | admin@demo.grievance360.local |
| Officer (Water Supply) | rahul.deshmukh@demo.grievance360.local |
| Officer (Roads) | vikram.rao@demo.grievance360.local |
| Citizen | citizen@demo.grievance360.local |

### 7. Logs and troubleshooting

```bash
docker compose ps                    # health status of each service
docker compose logs -f backend       # follow backend logs
docker compose logs db
```

- **Backend restarting with "JWT_SECRET must be set"** — set a 32+ byte `JWT_SECRET` in `.env`.
- **Browser shows "Cannot reach the server"** — confirm `curl http://localhost:8080/actuator/health` works; if you changed ports, rebuild the frontend with the matching `VITE_API_BASE_URL` and set `CORS_ALLOWED_ORIGINS` / `API_ORIGIN`.
- **CORS error in the console** — the page origin must be listed exactly in `CORS_ALLOWED_ORIGINS` (scheme + host + port).
- **Port already in use** — change the left side of the port mapping in `docker-compose.yml`.

### 8. Stop without losing data

```bash
docker compose down
```

Database rows (`pgdata` volume) and uploaded files (`attachments` volume) persist.

### 9. Reset local data intentionally

```bash
docker compose down -v      # deletes the pgdata and attachments volumes
docker compose up --build   # fresh schema; demo data reloads if the demo profile is set
```

## Local development without Docker

```bash
# database only
docker compose up -d db

# backend (JDK 21 + Maven)
cd backend
export DB_URL=jdbc:postgresql://localhost:5432/grievance360 DB_USERNAME=grievance360 DB_PASSWORD=<from .env>
export JWT_SECRET=<32+ bytes> SPRING_PROFILES_ACTIVE=dev,demo
mvn spring-boot:run

# frontend (Node 20+)
cd frontend
npm install
npm run dev        # http://localhost:5173, uses VITE_API_BASE_URL or http://localhost:8080/api/v1
```

## Tests

**Backend** — 30 tests (JUnit 5, Spring Boot Test, MockMvc) against a real PostgreSQL:

```bash
cd backend
mvn test     # starts PostgreSQL via Testcontainers (needs a Docker socket)
```

If Testcontainers cannot reach Docker, point the tests at any empty PostgreSQL database instead:

```bash
TEST_DB_URL=jdbc:postgresql://localhost:5433/g360test TEST_DB_USER=postgres TEST_DB_PASSWORD=postgres mvn test
```

Without a host JDK, run them inside Docker:

```bash
docker network create g360-test
docker run -d --rm --name g360-testdb --network g360-test -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=g360test postgres:16-alpine
docker run --rm --network g360-test -v "$PWD/backend:/app" -v g360-m2:/root/.m2 -w /app -e TEST_DB_URL=jdbc:postgresql://g360-testdb:5432/g360test maven:3.9-eclipse-temurin-21 mvn -B test
```

Coverage: registration/login/validation, JSON 401/403, CORS preflight, role escalation attempts, complaint ownership (404 for other citizens and out-of-department officers), server-generated references, valid/invalid transitions, required remarks and resolution notes, assignment rules, reopen/close, immutable history contents, internal-note visibility, attachment content sniffing, public lookup privacy, dashboards, sort whitelisting, admin role changes with audit records.

**Frontend** — Vitest + Testing Library:

```bash
cd frontend
npm test -- --run
npm run build
```

Covers schema rules, login (validation, server error, token storage + redirect), registration (no role sent, duplicate email), the submit-grievance form (validation → POST → navigation) and the role guard.

## Complaint lifecycle

```
SUBMITTED → ACKNOWLEDGED → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED
     ↘ REJECTED      ↘ NEEDS_INFORMATION ↺ (citizen reply resumes)   ↘ reopened → IN_PROGRESS
```

- Transitions are enforced by `StatusTransitionPolicy` on the server; every change writes an immutable `complaint_status_history` row with actor and role.
- `ASSIGNED` is entered only through assignment. Admins assign/reassign; officers may claim unassigned complaints in their own department.
- Only the assigned officer can move a complaint to In progress or Resolved. Resolving requires resolution notes; Rejected and Needs information require a remark.
- Citizens confirm (→ Closed) or reopen with a reason within 14 days of resolution (max 3 times). Resolved complaints auto-close after 7 days.
- References (`G360-YYYY-NNNNNN`) come from a PostgreSQL sequence.

## Metric definitions

| Metric | Definition |
|---|---|
| Open | status in Submitted, Acknowledged, Assigned, In progress, Needs information |
| Overdue | open **and** now > `due_at` |
| `due_at` | `created_at` + category SLA hours × priority factor (Critical 50%, High 75%, others 100% — configurable) |
| Resolved | `resolved_at` is set (Resolved, or Closed after resolution) |
| Resolution time | `resolved_at − created_at` |
| SLA compliance | resolved with `resolved_at ≤ due_at` ÷ resolved; shown as “—” when nothing is resolved |
| Escalation | scheduler (every 15 min) raises `escalation_level` for overdue open complaints, at most once per configured interval, up to the max level |

All dashboard numbers are SQL aggregates over the live tables; charts show an empty state when there is no data.

## Environment variables

| Variable | Where | Purpose |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | backend | JDBC connection (Compose sets them from `POSTGRES_*`) |
| `JWT_SECRET` | backend | HMAC key, ≥ 32 bytes. **Required** |
| `JWT_EXPIRY_MINUTES` | backend | Access-token lifetime (default 60) |
| `CORS_ALLOWED_ORIGINS` | backend | Comma-separated exact origins |
| `SPRING_PROFILES_ACTIVE` | backend | `dev`, `prod`, plus optional `demo` (dev only) |
| `STORAGE_DIR` | backend | Attachment directory (Compose: `/app/storage` volume) |
| `ADMIN_BOOTSTRAP_EMAIL/PASSWORD/NAME` | backend | One-time first admin |
| `PORT` | backend | HTTP port (default 8080; Render sets it) |
| `POSTGRES_DB/USER/PASSWORD` | compose | Database container |
| `VITE_API_BASE_URL` | frontend build | Public API base URL, e.g. `https://api.example.org/api/v1` |
| `API_ORIGIN` | frontend container | Allowed `connect-src` in the nginx CSP |

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for Vercel + Render + managed PostgreSQL, [docs/SECURITY.md](docs/SECURITY.md) for the security design, and [docs/API.md](docs/API.md) for the endpoint overview.

## Known limitations

- Notifications are in-app only (no email/SMS gateway).
- Rate limiting is in-memory per backend instance; use a gateway or shared store for multiple replicas.
- Attachments use local disk storage behind a `StorageService` interface; an object-storage adapter (S3/GCS) is not implemented yet, so ephemeral hosts need a persistent disk.
- No refresh tokens: users sign in again after the access token expires (default 60 minutes).
- No map picker; location is captured as text fields (latitude/longitude columns exist in the API).
- Testcontainers may fail on Docker Engine 29+ from inside a container; use `TEST_DB_URL` as documented above.
