-- E5.7 part 2: user groups. A user inherits the roles of all enabled groups they belong to (plus direct roles).
-- external_ref is reserved for the name of an LDAP / Active Directory group (mapping is built later).

CREATE TABLE user_group (
  id          BIGINT       NOT NULL,
  site_id     BIGINT       NOT NULL,
  code        VARCHAR(50)  NOT NULL,
  name        VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  external_ref VARCHAR(255),
  enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
  version     BIGINT       NOT NULL DEFAULT 0,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by  VARCHAR(100) NOT NULL,
  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_by  VARCHAR(100) NOT NULL,
  CONSTRAINT pk_user_group PRIMARY KEY (id),
  CONSTRAINT uq_user_group_site_code UNIQUE (site_id, code),
  CONSTRAINT fk_user_group_site FOREIGN KEY (site_id) REFERENCES site (id)
);

CREATE TABLE user_group_member (
  user_group_id BIGINT NOT NULL,
  user_id       BIGINT NOT NULL,
  CONSTRAINT pk_user_group_member PRIMARY KEY (user_group_id, user_id),
  CONSTRAINT fk_ugm_group FOREIGN KEY (user_group_id) REFERENCES user_group (id),
  CONSTRAINT fk_ugm_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);
CREATE INDEX ix_ugm_user ON user_group_member (user_id);

CREATE TABLE user_group_role (
  user_group_id BIGINT NOT NULL,
  role_id       BIGINT NOT NULL,
  CONSTRAINT pk_user_group_role PRIMARY KEY (user_group_id, role_id),
  CONSTRAINT fk_ugr_group FOREIGN KEY (user_group_id) REFERENCES user_group (id),
  CONSTRAINT fk_ugr_role FOREIGN KEY (role_id) REFERENCES app_role (id)
);
CREATE INDEX ix_ugr_role ON user_group_role (role_id);

CREATE SEQUENCE user_group_seq START WITH 1000 INCREMENT BY 1;
