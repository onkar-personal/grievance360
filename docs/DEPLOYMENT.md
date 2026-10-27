# Production deployment (Vercel + Render + managed PostgreSQL)

Not verified against live accounts. These steps describe the intended configuration.

## 1. Database

Create a managed PostgreSQL 16 instance (Render PostgreSQL, Neon, Supabase, RDS, …). Note the host, database, user and password. Require TLS: use a JDBC URL like

```
jdbc:postgresql://<host>:5432/<db>?sslmode=require
```

Flyway creates the schema on the backend's first start.

## 2. Backend on Render

1. New → **Web Service** → connect the repository, root directory `backend`, runtime **Docker** (uses `backend/Dockerfile`).
2. Health check path: `/actuator/health/readiness`.
3. Environment variables (Render → Environment; never commit them):

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://<host>:5432/<db>?sslmode=require` |
| `DB_USERNAME` / `DB_PASSWORD` | from the database |
| `JWT_SECRET` | `openssl rand -base64 48` output |
| `CORS_ALLOWED_ORIGINS` | `https://<your-app>.vercel.app` (plus any custom domain) |
| `STORAGE_DIR` | `/var/data/attachments` |
| `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` | first deploy only, then delete |
| `SWAGGER_ENABLED` | `false` to hide API docs in production (optional) |

Render sets `PORT`; the app binds to it.

4. Attachments: add a **Persistent Disk** mounted at `/var/data` (Render's filesystem is otherwise ephemeral). Without a disk, uploaded files are lost on redeploy. A cloud object-storage `StorageService` implementation is the long-term fix.
5. After the first successful start, confirm the log line "Bootstrap administrator created" and remove `ADMIN_BOOTSTRAP_PASSWORD`.

## 3. Frontend on Vercel

1. New Project → import the repository → **Root Directory** `frontend`. Framework preset: Vite. Build `npm run build`, output `dist`.
2. Environment variable (Production): `VITE_API_BASE_URL=https://<your-backend>.onrender.com/api/v1`. It is public and baked in at build time. It must never be `localhost`.
3. `frontend/vercel.json` provides the SPA rewrite and security headers. Redeploy after changing the variable.

## 4. Checklist

- [ ] HTTPS on both URLs; `VITE_API_BASE_URL` uses `https://`.
- [ ] `CORS_ALLOWED_ORIGINS` lists the exact Vercel origin(s); no wildcards.
- [ ] `demo` profile **not** enabled.
- [ ] `JWT_SECRET` unique per environment and stored only in hosting settings.
- [ ] Bootstrap password removed after first admin creation.
- [ ] Persistent disk attached for attachments.
- [ ] Database backups enabled at the provider.

## Running the Docker images elsewhere

```bash
docker build -t g360-backend ./backend
docker build -t g360-frontend --build-arg VITE_API_BASE_URL=https://api.example.org/api/v1 ./frontend
docker run -e API_ORIGIN=https://api.example.org -p 80:80 g360-frontend
```
