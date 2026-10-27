# Security design

## Authentication

- `POST /api/v1/auth/login` returns a signed JWT access token (HS256, JJWT 0.12) with `sub` = user id, `role`, `iat`, `exp`, `iss`, `jti`. Lifetime: `JWT_EXPIRY_MINUTES` (default 60).
- On every request the filter re-loads the user from the database, so deactivation or role changes take effect immediately even for unexpired tokens.
- Passwords are hashed with BCrypt (cost 12). Plaintext passwords are never stored or logged. Unknown-email logins still run a BCrypt comparison to keep response timing uniform.
- Self-registration always creates a `CITIZEN`. Roles can be changed only through `PUT /api/v1/admin/users/{id}`, which is admin-only and audited. Admins cannot demote or deactivate themselves.
- Login (per IP + email), registration (per IP) and public lookup (per IP) are rate-limited with a sliding window (`app.auth.*`). The limiter is in-memory; use a gateway limit for multi-instance deployments.
- The first admin is created from `ADMIN_BOOTSTRAP_*` only when no admin exists. No credentials are built in. Demo accounts exist only under the opt-in `demo` profile, which refuses to run with `prod`.

## Token storage and CSRF

The SPA keeps the access token in `sessionStorage` and sends it as `Authorization: Bearer …`. Rationale:

- The API is stateless (`SessionCreationPolicy.STATELESS`) and **does not read credentials from cookies**. A cross-site request cannot attach the bearer header, so classic CSRF does not apply; Spring's CSRF filter is therefore disabled for this API only, with the reason documented in `SecurityConfig`. If cookie-based auth is ever introduced, CSRF protection must be re-enabled.
- It works across separate origins (Vercel frontend, Render backend) without third-party-cookie or `SameSite` problems.
- Trade-off: a successful XSS could read the token. Mitigations: React escapes all output (no `dangerouslySetInnerHTML`), strict CSP from nginx (`script-src 'self'`, `frame-ancestors 'none'`, explicit `connect-src`), short token lifetime, and `sessionStorage` (cleared when the tab closes, not shared across tabs).

## Authorization

- URL rules: `/api/v1/admin/**` → ADMIN, `/api/v1/officer/**` → OFFICER/ADMIN; everything else requires authentication except auth, public lookup, department/category listing, health and API docs.
- Method rules: `@PreAuthorize` on controllers.
- Resource rules (`ComplaintAccessGuard`): citizens see only their own complaints; officers see complaints in their department or assigned to them; admins see all. Inaccessible complaints return **404** (not 403) so IDs cannot be probed. Listing queries apply the same scope in SQL.
- Officers can modify only complaints assigned to them, or unassigned ones in their department. Only the assigned officer can start or resolve work.
- Workflow fields (status, reference, priority, department, officer, timestamps) are never accepted from citizen input; request DTOs are explicit records, so mass assignment is not possible.
- Internal notes are filtered out of citizen responses server-side; citizen comments are always forced to `PUBLIC`.

## Input and files

- Bean Validation on every request DTO; consistent JSON error bodies (`code`, `message`, `fieldErrors`).
- Sorting uses a whitelist; page size is capped at 100.
- Attachments: max 5 MB each, 5 per complaint. The type is detected from the file's magic bytes (JPEG, PNG, WEBP, PDF), not the client's `Content-Type`. Files are stored under random UUID keys with a path-traversal check; original names are sanitized and served with `Content-Disposition: attachment` and `nosniff`.

## Transport, headers, CORS

- CORS: explicit origins from `CORS_ALLOWED_ORIGINS`, preflight `OPTIONS` permitted, credentials disabled, only needed headers allowed.
- Backend headers: `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`. Frontend nginx adds CSP and `Permissions-Policy`.
- Production must use HTTPS end to end (Render and Vercel terminate TLS). `server.forward-headers-strategy=framework` makes the app honour the proxy's `X-Forwarded-*` headers.

## Auditing and logging

- `audit_logs` records registrations, password changes and resets, user creation/role/active changes, department/category/setting changes, assignments, reassignments, priority changes and reroutes, with actor, IP and time. The entity is `@Immutable`; there is no update or delete endpoint.
- `complaint_status_history` is immutable and records every status change and assignment.
- Logs never include passwords, tokens or request bodies. Error responses never include stack traces.
