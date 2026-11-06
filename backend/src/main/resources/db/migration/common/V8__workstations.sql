-- E5.7 part 3: workstation groups and workstations.
-- A workstation group may carry a list of allowed permissions (empty list = no restriction): at a workstation of that
-- group a user only has the permissions that are in the list. A workstation is a registered browser; only the SHA-256
-- hash of its random token is stored (the token itself lives in an HttpOnly cookie in the browser).

CREATE TABLE workstation_group (
  id          BIGINT       NOT NULL,
  site_id     BIGINT       NOT NULL,
  code        VARCHAR(50)  NOT NULL,
  name        VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
  version     BIGINT       NOT NULL DEFAULT 0,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by  VARCHAR(100) NOT NULL,
  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_by  VARCHAR(100) NOT NULL,
  CONSTRAINT pk_workstation_group PRIMARY KEY (id),
  CONSTRAINT uq_workstation_group_site_code UNIQUE (site_id, code),
  CONSTRAINT fk_workstation_group_site FOREIGN KEY (site_id) REFERENCES site (id)
);

CREATE TABLE workstation_group_permission (
  workstation_group_id BIGINT NOT NULL,
  permission_id        BIGINT NOT NULL,
  CONSTRAINT pk_workstation_group_permission PRIMARY KEY (workstation_group_id, permission_id),
  CONSTRAINT fk_wgp_group FOREIGN KEY (workstation_group_id) REFERENCES workstation_group (id),
  CONSTRAINT fk_wgp_permission FOREIGN KEY (permission_id) REFERENCES app_permission (id)
);
CREATE INDEX ix_wgp_permission ON workstation_group_permission (permission_id);

CREATE TABLE workstation (
  id                   BIGINT       NOT NULL,
  site_id              BIGINT       NOT NULL,
  code                 VARCHAR(50)  NOT NULL,
  name                 VARCHAR(100) NOT NULL,
  workstation_type     VARCHAR(20)  NOT NULL,
  location_id          BIGINT,
  workstation_group_id BIGINT,
  host_name            VARCHAR(255),
  ip_address           VARCHAR(64),
  check_ip             BOOLEAN      NOT NULL DEFAULT FALSE,
  default_printer      VARCHAR(255),
  enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
  token_hash           VARCHAR(64),
  registered_at        TIMESTAMP WITH TIME ZONE,
  registered_by        VARCHAR(100),
  version              BIGINT       NOT NULL DEFAULT 0,
  created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by           VARCHAR(100) NOT NULL,
  updated_at           TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_by           VARCHAR(100) NOT NULL,
  CONSTRAINT pk_workstation PRIMARY KEY (id),
  CONSTRAINT uq_workstation_site_code UNIQUE (site_id, code),
  CONSTRAINT fk_workstation_site FOREIGN KEY (site_id) REFERENCES site (id),
  CONSTRAINT fk_workstation_location FOREIGN KEY (location_id) REFERENCES location (id),
  CONSTRAINT fk_workstation_group FOREIGN KEY (workstation_group_id) REFERENCES workstation_group (id),
  CONSTRAINT ck_workstation_type CHECK (workstation_type IN ('PC', 'HANDHELD', 'HMI', 'KIOSK'))
);
-- No unique rule on token_hash (a unique index treats empty values differently on PostgreSQL and SQL Server):
-- the application guarantees one browser per workstation and looks the hash up by this index.
CREATE INDEX ix_workstation_token ON workstation (token_hash);
CREATE INDEX ix_workstation_location ON workstation (location_id);
CREATE INDEX ix_workstation_group ON workstation (workstation_group_id);

CREATE SEQUENCE workstation_group_seq START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE workstation_seq START WITH 1000 INCREMENT BY 1;
