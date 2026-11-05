-- D1: site, users, roles, permissions, audit log (portable SQL)
-- Ids below 1000 are reserved for seed data; application sequences start at 1000.

CREATE TABLE site (
  id          BIGINT       NOT NULL,
  code        VARCHAR(30)  NOT NULL,
  name        VARCHAR(100) NOT NULL,
  version     BIGINT       NOT NULL DEFAULT 0,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by  VARCHAR(100) NOT NULL,
  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_by  VARCHAR(100) NOT NULL,
  CONSTRAINT pk_site PRIMARY KEY (id),
  CONSTRAINT uq_site_code UNIQUE (code)
);

CREATE TABLE app_role (
  id          BIGINT       NOT NULL,
  code        VARCHAR(50)  NOT NULL,
  name        VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  version     BIGINT       NOT NULL DEFAULT 0,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by  VARCHAR(100) NOT NULL,
  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_by  VARCHAR(100) NOT NULL,
  CONSTRAINT pk_app_role PRIMARY KEY (id),
  CONSTRAINT uq_app_role_code UNIQUE (code)
);

CREATE TABLE app_permission (
  id          BIGINT       NOT NULL,
  code        VARCHAR(80)  NOT NULL,
  description VARCHAR(255),
  version     BIGINT       NOT NULL DEFAULT 0,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by  VARCHAR(100) NOT NULL,
  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_by  VARCHAR(100) NOT NULL,
  CONSTRAINT pk_app_permission PRIMARY KEY (id),
  CONSTRAINT uq_app_permission_code UNIQUE (code)
);

CREATE TABLE app_user (
  id                   BIGINT       NOT NULL,
  site_id              BIGINT       NOT NULL,
  username             VARCHAR(100) NOT NULL,
  display_name         VARCHAR(150) NOT NULL,
  email                VARCHAR(255),
  password_hash        VARCHAR(255),
  must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,
  enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
  failed_attempts      INT          NOT NULL DEFAULT 0,
  locked_until         TIMESTAMP WITH TIME ZONE,
  last_login_at        TIMESTAMP WITH TIME ZONE,
  version              BIGINT       NOT NULL DEFAULT 0,
  created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by           VARCHAR(100) NOT NULL,
  updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_by           VARCHAR(100) NOT NULL,
  CONSTRAINT pk_app_user PRIMARY KEY (id),
  CONSTRAINT uq_app_user_username UNIQUE (username),
  CONSTRAINT fk_app_user_site FOREIGN KEY (site_id) REFERENCES site (id)
);

CREATE TABLE user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES app_role (id)
);

CREATE TABLE role_permission (
  role_id       BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  CONSTRAINT pk_role_permission PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES app_role (id),
  CONSTRAINT fk_role_permission_perm FOREIGN KEY (permission_id) REFERENCES app_permission (id)
);

-- Audit log: append-only (the application only inserts). Not a business table, so no version column.
CREATE TABLE audit_log (
  id            BIGINT        NOT NULL,
  site_id       BIGINT,
  occurred_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  actor         VARCHAR(100)  NOT NULL,
  action        VARCHAR(80)   NOT NULL,
  entity_type   VARCHAR(80),
  entity_id     VARCHAR(100),
  before_value  VARCHAR(4000),
  after_value   VARCHAR(4000),
  detail        VARCHAR(1000),
  remote_addr   VARCHAR(64),
  correlation_id VARCHAR(64),
  CONSTRAINT pk_audit_log PRIMARY KEY (id),
  CONSTRAINT fk_audit_log_site FOREIGN KEY (site_id) REFERENCES site (id)
);
CREATE INDEX ix_audit_log_occurred_at ON audit_log (occurred_at);
CREATE INDEX ix_audit_log_actor ON audit_log (actor);
CREATE INDEX ix_audit_log_action ON audit_log (action);

CREATE SEQUENCE site_seq START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE app_role_seq START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE app_permission_seq START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE app_user_seq START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE audit_log_seq START WITH 1000 INCREMENT BY 1;

-- Seed data ---------------------------------------------------------------------------------------
INSERT INTO site (id, code, name, version, created_at, created_by, updated_at, updated_by)
VALUES (1, 'DXB-TEST', 'DIMOS Test Site', 0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system');

INSERT INTO app_role (id, code, name, description, version, created_at, created_by, updated_at, updated_by) VALUES
  (1, 'SUPER_ADMIN', 'Super administrator', 'Full access including user and system administration', 0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
  (2, 'ADMIN',       'Administrator',       'Administers users and master data',                    0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
  (3, 'OPERATOR',    'Operator',            'Runs day-to-day warehouse operations',                 0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
  (4, 'VIEWER',      'Viewer',              'Read-only access',                                     0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system');

INSERT INTO app_permission (id, code, description, version, created_at, created_by, updated_at, updated_by) VALUES
  (1, 'DASHBOARD_VIEW', 'View the dashboard',          0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
  (2, 'USER_MANAGE',    'Create and change users',     0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
  (3, 'AUDIT_VIEW',     'View the audit log',          0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system');

INSERT INTO role_permission (role_id, permission_id) VALUES
  (1, 1), (1, 2), (1, 3),
  (2, 1), (2, 2), (2, 3),
  (3, 1),
  (4, 1);

-- password_hash stays NULL; it is set at startup from WMS_INIT_PASSWORD_<USERNAME> (step D2).
INSERT INTO app_user (id, site_id, username, display_name, email, password_hash, must_change_password, enabled,
                      failed_attempts, version, created_at, created_by, updated_at, updated_by) VALUES
  (1, 1, 'admin',   'Administrator',   NULL,                      NULL, TRUE, TRUE, 0, 0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
  (2, 1, 'rasheed', 'Abdul Rasheed',   'a.rasheed@dimos-me.com',  NULL, TRUE, TRUE, 0, 0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system');

INSERT INTO user_role (user_id, role_id) VALUES (1, 1), (2, 1);
