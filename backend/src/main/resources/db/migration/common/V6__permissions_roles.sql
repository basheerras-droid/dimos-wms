-- E5.7 part 1: permission catalogue (module), locked built-in roles, four new permissions.
-- Roles and permissions stay global (no site_id), as before. Seed ids stay below 1000.

ALTER TABLE app_permission ADD module VARCHAR(50) NOT NULL DEFAULT 'general';
UPDATE app_permission SET module = 'dashboard'      WHERE code = 'DASHBOARD_VIEW';
UPDATE app_permission SET module = 'administration' WHERE code IN ('USER_MANAGE', 'AUDIT_VIEW');
UPDATE app_permission SET module = 'masterdata'     WHERE code IN ('MASTERDATA_VIEW', 'MASTERDATA_EDIT');

-- The four roles that ship with the product are locked: they cannot be edited or deleted.
ALTER TABLE app_role ADD is_builtin BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE app_role SET is_builtin = TRUE WHERE id IN (1, 2, 3, 4);

INSERT INTO app_permission (id, code, description, module, version, created_at, created_by, updated_at, updated_by) VALUES
  (6, 'ROLE_MANAGE',        'Create, change and delete custom roles',                    'administration', 0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
  (7, 'WORKSTATION_MANAGE', 'Manage workstations and workstation groups',                'administration', 0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
  (8, 'PARAMETER_VIEW',     'See the system parameters and their effective values',      'administration', 0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system'),
  (9, 'PARAMETER_EDIT',     'Change system parameter values',                            'administration', 0, CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 'system');

-- SUPER_ADMIN (1): all four. ADMIN (2): workstations and viewing parameters.
INSERT INTO role_permission (role_id, permission_id) VALUES
  (1, 6), (1, 7), (1, 8), (1, 9),
  (2, 7), (2, 8);
