-- E: four more built-in equipment types, needed by the reference projects (EKFC monorail, DFC ULD/PMC with ETVs and
-- turntables, INL pallet conveyors / stacker cranes / high-bay). Seed ids stay below 1000.
INSERT INTO equipment_type (id, code, name, is_seeded) VALUES
  (14, 'ETV',       'Elevating transfer vehicle', TRUE),
  (15, 'MONORAIL',  'Monorail',                   TRUE),
  (16, 'TURNTABLE', 'Turntable',                  TRUE),
  (17, 'WASHER',    'Washer',                     TRUE);
