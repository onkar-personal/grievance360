-- Grievance360 core schema

CREATE TABLE departments (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(500),
    contact_email   VARCHAR(160),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_departments_code UNIQUE (code),
    CONSTRAINT uq_departments_name UNIQUE (name)
);

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(120) NOT NULL,
    email           VARCHAR(160) NOT NULL,
    phone           VARCHAR(20),
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    department_id   BIGINT       REFERENCES departments(id) ON DELETE RESTRICT,
    address         VARCHAR(300),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_role CHECK (role IN ('CITIZEN', 'OFFICER', 'ADMIN'))
);
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));
CREATE INDEX ix_users_role ON users (role);
CREATE INDEX ix_users_department ON users (department_id);

CREATE TABLE complaint_categories (
    id                BIGSERIAL PRIMARY KEY,
    department_id     BIGINT       NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    name              VARCHAR(120) NOT NULL,
    description       VARCHAR(500),
    sla_hours         INTEGER      NOT NULL DEFAULT 72,
    default_priority  VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_categories_dept_name UNIQUE (department_id, name),
    CONSTRAINT ck_categories_sla CHECK (sla_hours BETWEEN 1 AND 2160),
    CONSTRAINT ck_categories_priority CHECK (default_priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE SEQUENCE complaint_ref_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE complaints (
    id                   BIGSERIAL PRIMARY KEY,
    reference            VARCHAR(24)   NOT NULL,
    title                VARCHAR(160)  NOT NULL,
    description          VARCHAR(5000) NOT NULL,
    category_id          BIGINT        NOT NULL REFERENCES complaint_categories(id) ON DELETE RESTRICT,
    department_id        BIGINT        NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    citizen_id           BIGINT        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    assigned_officer_id  BIGINT        REFERENCES users(id) ON DELETE RESTRICT,
    status               VARCHAR(20)   NOT NULL,
    priority             VARCHAR(10)   NOT NULL,
    location_address     VARCHAR(300),
    location_ward        VARCHAR(80),
    location_city        VARCHAR(80),
    location_pincode     VARCHAR(10),
    latitude             NUMERIC(9, 6),
    longitude            NUMERIC(9, 6),
    resolution_notes     VARCHAR(3000),
    reopen_count         INTEGER       NOT NULL DEFAULT 0,
    escalation_level     INTEGER       NOT NULL DEFAULT 0,
    escalated_at         TIMESTAMPTZ,
    due_at               TIMESTAMPTZ   NOT NULL,
    assigned_at          TIMESTAMPTZ,
    resolved_at          TIMESTAMPTZ,
    closed_at            TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_complaints_reference UNIQUE (reference),
    CONSTRAINT ck_complaints_status CHECK (status IN ('SUBMITTED', 'ACKNOWLEDGED', 'ASSIGNED', 'IN_PROGRESS',
        'NEEDS_INFORMATION', 'RESOLVED', 'CLOSED', 'REJECTED')),
    CONSTRAINT ck_complaints_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_complaints_lat CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_complaints_lng CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);
CREATE INDEX ix_complaints_status ON complaints (status);
CREATE INDEX ix_complaints_department ON complaints (department_id);
CREATE INDEX ix_complaints_officer ON complaints (assigned_officer_id);
CREATE INDEX ix_complaints_citizen ON complaints (citizen_id);
CREATE INDEX ix_complaints_created ON complaints (created_at DESC);
CREATE INDEX ix_complaints_due ON complaints (due_at);
CREATE INDEX ix_complaints_category ON complaints (category_id);

-- Immutable audit trail of status changes. Application never updates or deletes rows.
CREATE TABLE complaint_status_history (
    id              BIGSERIAL PRIMARY KEY,
    complaint_id    BIGINT       NOT NULL REFERENCES complaints(id) ON DELETE RESTRICT,
    from_status     VARCHAR(20),
    to_status       VARCHAR(20)  NOT NULL,
    remark          VARCHAR(2000),
    actor_id        BIGINT       REFERENCES users(id) ON DELETE RESTRICT,
    actor_role      VARCHAR(20),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_status_history_complaint ON complaint_status_history (complaint_id, created_at);

CREATE TABLE complaint_comments (
    id              BIGSERIAL PRIMARY KEY,
    complaint_id    BIGINT        NOT NULL REFERENCES complaints(id) ON DELETE RESTRICT,
    author_id       BIGINT        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    body            VARCHAR(3000) NOT NULL,
    visibility      VARCHAR(10)   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_comments_visibility CHECK (visibility IN ('PUBLIC', 'INTERNAL'))
);
CREATE INDEX ix_comments_complaint ON complaint_comments (complaint_id, created_at);

CREATE TABLE complaint_attachments (
    id              BIGSERIAL PRIMARY KEY,
    complaint_id    BIGINT       NOT NULL REFERENCES complaints(id) ON DELETE RESTRICT,
    uploaded_by_id  BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    original_name   VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    storage_key     VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_attachments_size CHECK (size_bytes > 0)
);
CREATE INDEX ix_attachments_complaint ON complaint_attachments (complaint_id);

CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    complaint_id    BIGINT       REFERENCES complaints(id) ON DELETE SET NULL,
    type            VARCHAR(30)  NOT NULL,
    title           VARCHAR(160) NOT NULL,
    message         VARCHAR(500) NOT NULL,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    actor_email     VARCHAR(160),
    action          VARCHAR(60)  NOT NULL,
    entity_type     VARCHAR(40)  NOT NULL,
    entity_id       VARCHAR(40),
    details         VARCHAR(1000),
    ip_address      VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_created ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_actor ON audit_logs (actor_id);

-- Key/value system settings (escalation thresholds etc.)
CREATE TABLE system_settings (
    setting_key     VARCHAR(60)  PRIMARY KEY,
    setting_value   VARCHAR(200) NOT NULL,
    description     VARCHAR(300),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO system_settings (setting_key, setting_value, description) VALUES
 ('escalation.enabled', 'true', 'Automatically escalate complaints past their SLA due time'),
 ('escalation.repeat_hours', '24', 'Hours after an escalation before the next level is raised'),
 ('escalation.max_level', '3', 'Highest escalation level'),
 ('sla.critical_factor_percent', '50', 'CRITICAL priority SLA as a percentage of the category SLA'),
 ('sla.high_factor_percent', '75', 'HIGH priority SLA as a percentage of the category SLA');
