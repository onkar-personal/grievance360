# API overview

Base path: `/api/v1`. Interactive documentation: `/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`). Authenticate with `Authorization: Bearer <accessToken>`.

Errors share one shape:

```json
{ "timestamp": "...", "status": 409, "error": "Conflict", "code": "INVALID_TRANSITION",
  "message": "Cannot change status from SUBMITTED to RESOLVED", "path": "/api/v1/complaints/12/status",
  "fieldErrors": [ { "field": "title", "message": "..." } ] }
```

List endpoints return `{ content, page, size, totalElements, totalPages }` and accept `page`, `size` (≤ 100) and `sort=field,asc|desc` (whitelisted fields).

| Method & path | Who | Purpose |
|---|---|---|
| POST `/auth/register` | public | Citizen sign-up → token |
| POST `/auth/login` | public | Token |
| GET `/auth/me` | any | Current user |
| GET/PUT `/users/me`, POST `/users/me/password` | any | Own profile and password |
| GET `/departments`, `/categories` | public | Active departments / categories |
| GET `/public/complaints/{reference}` | public | Status-only lookup |
| GET `/complaints` | any | Scoped list. Filters: `q, status[], priority, departmentId, categoryId, officerId, unassigned, mine, overdue, escalated, from, to` |
| POST `/complaints` | citizen | Submit |
| GET `/complaints/{id}` | scoped | Detail + `actions` the caller may take |
| GET `/complaints/{id}/history` | scoped | Immutable status history |
| GET/POST `/complaints/{id}/comments` | scoped | Messages; staff may post `INTERNAL` notes |
| PATCH `/complaints/{id}/status` | officer/admin | Workflow transition |
| POST `/complaints/{id}/assignment` | admin; officer (self-claim) | Assign / reassign |
| PATCH `/complaints/{id}/priority` | officer/admin | Change priority (recomputes due time) |
| POST `/complaints/{id}/reroute` | admin | Move to another category/department |
| POST `/complaints/{id}/reopen` | citizen owner/admin | Reopen resolved complaint |
| POST `/complaints/{id}/close` | citizen owner | Confirm resolution |
| POST `/complaints/{id}/attachments` (multipart `file`) | owner / managing staff | Upload evidence |
| GET `/complaints/{id}/attachments/{attachmentId}` | scoped | Download |
| GET `/notifications`, `/notifications/unread-count`; POST `/notifications/{id}/read`, `/notifications/read-all` | any | In-app notifications |
| GET `/dashboard/citizen` | citizen | Own statistics |
| GET `/officer/dashboard` | officer | Workload & performance |
| GET `/admin/dashboard?days=` | admin | System aggregates |
| GET `/admin/reports/sla?from=&to=` | admin | SLA by category |
| GET/POST/PUT `/admin/users…`, GET `/admin/users/officers` | admin | User & role management |
| GET/POST/PUT `/admin/departments…`, `/admin/categories…` | admin | Department & category management |
| GET/PUT `/admin/settings…` | admin | Escalation / SLA factors |
| GET `/admin/audit-logs` | admin | Audit trail |
| GET `/actuator/health` | public | Liveness/readiness |
