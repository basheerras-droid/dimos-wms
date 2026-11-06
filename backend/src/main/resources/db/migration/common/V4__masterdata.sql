-- E1: master data - areas, locations, links, equipment, handling-unit types (portable SQL)
-- Ids below 1000 are reserved for seed data; application sequences start at 1000.
-- Codes are unique per site. Duplicate location links are blocked in the backend and reported by the
-- configuration validator (E4), not by a database unique rule.

-- Areas ---------------------------------------------------------------------------------------------
CREATE TABLE area (
  id          BIGINT       NOT NULL,
  site_id     BIGINT       NOT NULL,
  code        VARCHAR(30)  NOT NULL,
  name        VARCHAR(100) NOT NULL,
  area_type   VARCHAR(20)  NOT NULL,
  version     BIGINT       NOT NULL DEFAULT 0,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by  VARCHAR(100) NOT NULL,
  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_by  VARCHAR(100) NOT NULL,
  CONSTRAINT pk_area PRIMARY KEY (id),
  CONSTRAINT uq_area_site_code UNIQUE (site_id, code),
  CONSTRAINT fk_area_site FOREIGN KEY (site_id) REFERENCES site (id),
  CONSTRAINT ck_area_type CHECK (area_type IN ('INBOUND', 'STORAGE', 'OUTBOUND', 'BUFFER', 'WORKSTATION', 'TRANSPORT'))
);

-- Location types ------------------------------------------------------------------------------------
CREATE TABLE location_type (
  id            BIGINT       NOT NULL,
  site_id       BIGINT       NOT NULL,
  code          VARCHAR(30)  NOT NULL,
  name          VARCHAR(100) NOT NULL,
  is_storage    BOOLEAN      NOT NULL DEFAULT FALSE,
  is_transfer   BOOLEAN      NOT NULL DEFAULT FALSE,
  is_workstation BOOLEAN     NOT NULL DEFAULT FALSE,
  capacity_hu   INT          NOT NULL DEFAULT 1,
  version       BIGINT       NOT NULL DEFAULT 0,
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by    VARCHAR(100) NOT NULL,
  updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_by    VARCHAR(100) NOT NULL,
  CONSTRAINT pk_location_type PRIMARY KEY (id),
  CONSTRAINT uq_location_type_site_code UNIQUE (site_id, code),
  CONSTRAINT fk_location_type_site FOREIGN KEY (site_id) REFERENCES site (id),
  CONSTRAINT ck_location_type_capacity CHECK (capacity_hu >= 0)
);

-- Handling-unit types -------------------------------------------------------------------------------
CREATE TABLE hu_type (
  id            BIGINT        NOT NULL,
  site_id       BIGINT        NOT NULL,
  code          VARCHAR(30)   NOT NULL,
  name          VARCHAR(100)  NOT NULL,
  length_mm     INT,
  width_mm      INT,
  height_mm     INT,
  tare_kg       DECIMAL(10,3),
  max_gross_kg  DECIMAL(10,3),
  is_container  BOOLEAN       NOT NULL DEFAULT FALSE,
  version       BIGINT        NOT NULL DEFAULT 0,
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by    VARCHAR(100)  NOT NULL,
  updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_by    VARCHAR(100)  NOT NULL,
  CONSTRAINT pk_hu_type PRIMARY KEY (id),
  CONSTRAINT uq_hu_type_site_code UNIQUE (site_id, code),
  CONSTRAINT fk_hu_type_site FOREIGN KEY (site_id) REFERENCES site (id)
);

-- Locations -----------------------------------------------------------------------------------------
CREATE TABLE location (
  id               BIGINT        NOT NULL,
  site_id          BIGINT        NOT NULL,
  code             VARCHAR(50)   NOT NULL,
  name             VARCHAR(100),
  area_id          BIGINT        NOT NULL,
  location_type_id BIGINT        NOT NULL,
  x_mm             INT,
  y_mm             INT,
  z_mm             INT,
  level_no         INT           NOT NULL DEFAULT 0,
  aisle            VARCHAR(20),
  side             VARCHAR(10),
  column_no        INT,
  row_no           INT,
  max_weight_kg    DECIMAL(10,3),
  enabled          BOOLEAN       NOT NULL DEFAULT TRUE,
  blocked_in       BOOLEAN       NOT NULL DEFAULT FALSE,
  blocked_out      BOOLEAN       NOT NULL DEFAULT FALSE,
  block_reason     VARCHAR(255),
  external_ref     VARCHAR(100),
  ext_attributes   VARCHAR(4000),
  version          BIGINT        NOT NULL DEFAULT 0,
  created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by       VARCHAR(100)  NOT NULL,
  updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_by       VARCHAR(100)  NOT NULL,
  CONSTRAINT pk_location PRIMARY KEY (id),
  CONSTRAINT uq_location_site_code UNIQUE (site_id, code),
  CONSTRAINT fk_location_site FOREIGN KEY (site_id) REFERENCES site (id),
  CONSTRAINT fk_location_area FOREIGN KEY (area_id) REFERENCES area (id),
  CONSTRAINT fk_location_type FOREIGN KEY (location_type_id) REFERENCES location_type (id)
);
CREATE INDEX ix_location_site_level ON location (site_id, level_no);
CREATE INDEX ix_location_area ON location (area_id);
CREATE INDEX ix_location_type ON location (location_type_id);

-- Equipment types (shared list for all sites; seeded types are protected) --------------------------
CREATE TABLE equipment_type (
  id         BIGINT       NOT NULL,
  code       VARCHAR(30)  NOT NULL,
  name       VARCHAR(100) NOT NULL,
  is_seeded  BOOLEAN      NOT NULL DEFAULT FALSE,
  CONSTRAINT pk_equipment_type PRIMARY KEY (id),
  CONSTRAINT uq_equipment_type_code UNIQUE (code)
);

-- Equipment -----------------------------------------------------------------------------------------
CREATE TABLE equipment (
  id                BIGINT        NOT NULL,
  site_id           BIGINT        NOT NULL,
  code              VARCHAR(50)   NOT NULL,
  name              VARCHAR(100)  NOT NULL,
  equipment_type_id BIGINT        NOT NULL,
  area_id           BIGINT,
  parent_id         BIGINT,
  plc_connection    VARCHAR(255),
  enabled           BOOLEAN       NOT NULL DEFAULT TRUE,
  external_ref      VARCHAR(100),
  ext_attributes    VARCHAR(4000),
  version           BIGINT        NOT NULL DEFAULT 0,
  created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by        VARCHAR(100)  NOT NULL,
  updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_by        VARCHAR(100)  NOT NULL,
  CONSTRAINT pk_equipment PRIMARY KEY (id),
  CONSTRAINT uq_equipment_site_code UNIQUE (site_id, code),
  CONSTRAINT fk_equipment_site FOREIGN KEY (site_id) REFERENCES site (id),
  CONSTRAINT fk_equipment_type FOREIGN KEY (equipment_type_id) REFERENCES equipment_type (id),
  CONSTRAINT fk_equipment_area FOREIGN KEY (area_id) REFERENCES area (id),
  CONSTRAINT fk_equipment_parent FOREIGN KEY (parent_id) REFERENCES equipment (id)
);
CREATE INDEX ix_equipment_type ON equipment (equipment_type_id);
CREATE INDEX ix_equipment_area ON equipment (area_id);
CREATE INDEX ix_equipment_parent ON equipment (parent_id);

-- Location links: "from location A a handling unit can move to location B" ------------------------
-- hu_type_id empty = all handling-unit types. Duplicates are checked in the backend / validator.
CREATE TABLE location_link (
  id               BIGINT       NOT NULL,
  site_id          BIGINT       NOT NULL,
  from_location_id BIGINT       NOT NULL,
  to_location_id   BIGINT       NOT NULL,
  hu_type_id       BIGINT,
  equipment_id     BIGINT,
  cost             INT          NOT NULL DEFAULT 1,
  enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
  version          BIGINT       NOT NULL DEFAULT 0,
  created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
  created_by       VARCHAR(100) NOT NULL,
  updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_by       VARCHAR(100) NOT NULL,
  CONSTRAINT pk_location_link PRIMARY KEY (id),
  CONSTRAINT fk_location_link_site FOREIGN KEY (site_id) REFERENCES site (id),
  CONSTRAINT fk_location_link_from FOREIGN KEY (from_location_id) REFERENCES location (id),
  CONSTRAINT fk_location_link_to FOREIGN KEY (to_location_id) REFERENCES location (id),
  CONSTRAINT fk_location_link_hu_type FOREIGN KEY (hu_type_id) REFERENCES hu_type (id),
  CONSTRAINT fk_location_link_equipment FOREIGN KEY (equipment_id) REFERENCES equipment (id),
  CONSTRAINT ck_location_link_cost CHECK (cost >= 0),
  CONSTRAINT ck_location_link_not_self CHECK (from_location_id <> to_location_id)
);
CREATE INDEX ix_location_link_site ON location_link (site_id);
CREATE INDEX ix_location_link_from ON location_link (from_location_id);
CREATE INDEX ix_location_link_to ON location_link (to_location_id);
CREATE INDEX ix_location_link_hu_type ON location_link (hu_type_id);
CREATE INDEX ix_location_link_equipment ON location_link (equipment_id);

-- Link tables (no audit columns; changes are audited through the owning record) ---------------------
CREATE TABLE equipment_location (
  equipment_id BIGINT NOT NULL,
  location_id  BIGINT NOT NULL,
  CONSTRAINT pk_equipment_location PRIMARY KEY (equipment_id, location_id),
  CONSTRAINT fk_equipment_location_eq FOREIGN KEY (equipment_id) REFERENCES equipment (id),
  CONSTRAINT fk_equipment_location_loc FOREIGN KEY (location_id) REFERENCES location (id)
);
CREATE INDEX ix_equipment_location_loc ON equipment_location (location_id);

CREATE TABLE location_type_hu_type (
  location_type_id BIGINT NOT NULL,
  hu_type_id       BIGINT NOT NULL,
  CONSTRAINT pk_location_type_hu_type PRIMARY KEY (location_type_id, hu_type_id),
  CONSTRAINT fk_lthu_location_type FOREIGN KEY (location_type_id) REFERENCES location_type (id),
  CONSTRAINT fk_lthu_hu_type FOREIGN KEY (hu_type_id) REFERENCES hu_type (id)
);
CREATE INDEX ix_lthu_hu_type ON location_type_hu_type (hu_type_id);

CREATE TABLE hu_type_nesting (
  inner_hu_type_id BIGINT NOT NULL,
  outer_hu_type_id BIGINT NOT NULL,
  max_qty          INT,
  CONSTRAINT pk_hu_type_nesting PRIMARY KEY (inner_hu_type_id, outer_hu_type_id),
  CONSTRAINT fk_hu_nesting_inner FOREIGN KEY (inner_hu_type_id) REFERENCES hu_type (id),
  CONSTRAINT fk_hu_nesting_outer FOREIGN KEY (outer_hu_type_id) REFERENCES hu_type (id),
  CONSTRAINT ck_hu_nesting_not_self CHECK (inner_hu_type_id <> outer_hu_type_id),
  CONSTRAINT ck_hu_nesting_qty CHECK (max_qty IS NULL OR max_qty > 0)
);
CREATE INDEX ix_hu_nesting_outer ON hu_type_nesting (outer_hu_type_id);

-- Sequences -----------------------------------------------------------------------------------------
CREATE SEQUENCE area_seq START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE location_type_seq START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE hu_type_seq START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE location_seq START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE location_link_seq START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE equipment_type_seq START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE equipment_seq START WITH 1000 INCREMENT BY 1;

-- Seed data -----------------------------------------------------------------------------------------
INSERT INTO equipment_type (id, code, name, is_seeded) VALUES
  (1,  'CONVEYOR',      'Conveyor',      TRUE),
  (2,  'TRANSFER',      'Transfer unit', TRUE),
  (3,  'HOIST',         'Hoist',         TRUE),
  (4,  'LIFT',          'Lift',          TRUE),
  (5,  'STACKER_CRANE', 'Stacker crane', TRUE),
  (6,  'SHUTTLE',       'Shuttle',       TRUE),
  (7,  'AGV',           'AGV',           TRUE),
  (8,  'ROBOT',         'Robot',         TRUE),
  (9,  'STATION',       'Work station',  TRUE),
  (10, 'SCANNER',       'Scanner',       TRUE),
  (11, 'SCALE',         'Scale',         TRUE),
  (12, 'PRINTER',       'Printer',       TRUE),
  (13, 'RACK',          'Rack',          TRUE);

-- Permissions: MASTERDATA_VIEW for all four roles, MASTERDATA_EDIT for SUPER_ADMIN and ADMIN.
-- USER_MANAGE (id 2) is kept as it is.
INSERT INTO app_permission (id, code, description, version, created_at, created_by, updated_at, updated_by) VALUES
  (4, 'MASTERDATA_VIEW', 'View master data (areas, locations, equipment, HU types)',   0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
  (5, 'MASTERDATA_EDIT', 'Create, change, delete and import master data',             0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system');

INSERT INTO role_permission (role_id, permission_id) VALUES
  (1, 4), (2, 4), (3, 4), (4, 4),
  (1, 5), (2, 5);
