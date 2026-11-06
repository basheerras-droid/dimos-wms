-- E5.7 part 4: system parameters.
-- parameter_definition is global and seeded here: it says what a parameter is, its default, and its security bounds
-- (min / max). The bounds can only be changed by a new migration - never from the screen.
-- parameter_value holds the values that differ from the default, per scope; the most specific scope wins:
-- WORKSTATION, then WS_GROUP, then SITE, then GLOBAL, then the default of the definition.
-- scope_ref_id: 0 for GLOBAL, the site id for SITE, the workstation group id for WS_GROUP, the workstation id for WORKSTATION.

CREATE TABLE parameter_definition (
  id            BIGINT       NOT NULL,
  code          VARCHAR(80)  NOT NULL,
  module        VARCHAR(50)  NOT NULL,
  name          VARCHAR(150) NOT NULL,
  description   VARCHAR(500),
  value_type    VARCHAR(20)  NOT NULL,
  unit          VARCHAR(30),
  default_value VARCHAR(255) NOT NULL,
  min_value     VARCHAR(50),
  max_value     VARCHAR(50),
  choices       VARCHAR(500),
  allowed_scopes VARCHAR(100) NOT NULL,
  restart_needed BOOLEAN     NOT NULL DEFAULT FALSE,
  CONSTRAINT pk_parameter_definition PRIMARY KEY (id),
  CONSTRAINT uq_parameter_definition_code UNIQUE (code),
  CONSTRAINT ck_parameter_definition_type CHECK (value_type IN ('INT', 'BOOL', 'TEXT', 'CHOICE', 'DURATION'))
);

CREATE TABLE parameter_value (
  id                      BIGINT       NOT NULL,
  site_id                 BIGINT,
  parameter_definition_id BIGINT       NOT NULL,
  scope                   VARCHAR(20)  NOT NULL,
  scope_ref_id            BIGINT       NOT NULL,
  value                   VARCHAR(255) NOT NULL,
  version                 BIGINT       NOT NULL DEFAULT 0,
  created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by              VARCHAR(100) NOT NULL,
  updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_by              VARCHAR(100) NOT NULL,
  CONSTRAINT pk_parameter_value PRIMARY KEY (id),
  CONSTRAINT uq_parameter_value UNIQUE (parameter_definition_id, scope, scope_ref_id),
  CONSTRAINT fk_parameter_value_def FOREIGN KEY (parameter_definition_id) REFERENCES parameter_definition (id),
  CONSTRAINT fk_parameter_value_site FOREIGN KEY (site_id) REFERENCES site (id),
  CONSTRAINT ck_parameter_value_scope CHECK (scope IN ('GLOBAL', 'SITE', 'WS_GROUP', 'WORKSTATION'))
);
CREATE INDEX ix_parameter_value_site ON parameter_value (site_id);

CREATE SEQUENCE parameter_value_seq START WITH 1000 INCREMENT BY 1;

-- Seed. Security bounds (agreed with the owner): password length >= 8; lockout attempts 3-10; lockout minutes 5-1440;
-- session timeout 5 minutes to 12 hours.
INSERT INTO parameter_definition
  (id, code, module, name, description, value_type, unit, default_value, min_value, max_value, choices, allowed_scopes, restart_needed) VALUES
  (1, 'security.session_timeout', 'security', 'Session timeout',
      'How long a signed-in browser may stay idle before the user must sign in again. Applies from the next sign-in.',
      'DURATION', NULL, 'PT30M', 'PT5M', 'PT12H', NULL, 'GLOBAL,SITE,WS_GROUP,WORKSTATION', FALSE),
  (2, 'security.password_min_length', 'security', 'Minimum password length',
      'The shortest password a user may choose when changing the password.',
      'INT', 'characters', '8', '8', '128', NULL, 'GLOBAL,SITE', FALSE),
  (3, 'security.lockout_attempts', 'security', 'Failed sign-ins before lockout',
      'How many wrong passwords in a row lock an account temporarily.',
      'INT', 'attempts', '5', '3', '10', NULL, 'GLOBAL,SITE', FALSE),
  (4, 'security.lockout_minutes', 'security', 'Lockout time',
      'How long an account stays locked after too many wrong passwords (an administrator can unlock it earlier).',
      'INT', 'minutes', '15', '5', '1440', NULL, 'GLOBAL,SITE', FALSE),
  (5, 'audit.retention_days', 'audit', 'Audit log retention',
      'How many days of the audit log to keep. Recorded for the future clean-up job; nothing is deleted automatically yet.',
      'INT', 'days', '365', '30', '3650', NULL, 'GLOBAL,SITE', FALSE);
