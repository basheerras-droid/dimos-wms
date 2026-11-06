# E1 - Master data tables (approved design)

Implemented as `backend/src/main/resources/db/migration/common/V4__masterdata.sql` (portable SQL).

## General rules
- All tables except `equipment_type` and the three link tables (`equipment_location`, `location_type_hu_type`,
  `hu_type_nesting`) get: `id` BIGINT from a sequence starting at 1000, `site_id`, `version`,
  `created_at/by`, `updated_at/by`.
- Codes are unique per site.
- Indexes on every foreign key, and on `location (site_id, level_no)`.

## Tables
| Table | Columns | References |
|---|---|---|
| area | code, name, area_type (INBOUND, STORAGE, OUTBOUND, BUFFER, WORKSTATION, TRANSPORT; CHECK constraint) | site |
| location_type | code, name, is_storage, is_transfer, is_workstation, capacity_hu | site |
| hu_type | code, name, length_mm, width_mm, height_mm, tare_kg, max_gross_kg, is_container | site |
| location | code, name, x_mm, y_mm, z_mm, level_no, aisle, side, column_no, row_no, max_weight_kg, enabled, blocked_in, blocked_out, block_reason, external_ref, ext_attributes | area, location_type |
| location_link | cost, enabled | from_location, to_location, hu_type (empty = all types), equipment (optional) |
| equipment_type | code, name, is_seeded; shared list for all sites, seeded with 13 types in V4 and 4 more in V5 (ETV, MONORAIL, TURNTABLE, WASHER) | - |
| equipment | code, name, plc_connection, enabled, external_ref, ext_attributes | equipment_type, area (optional), parent equipment (optional) |
| equipment_location | key (equipment, location) | equipment, location |
| location_type_hu_type | key (location_type, hu_type) | location_type, hu_type |
| hu_type_nesting | key (inner, outer), max_qty (optional) | hu_type x2 |

## Approved changes
1. `ext_attributes` is VARCHAR(4000), not TEXT.
2. `level_no` instead of `level`.
3. Duplicate links are blocked in the backend and reported by the validator (E4), not by a DB unique rule.
4. Permissions: add MASTERDATA_VIEW (all 4 roles) and MASTERDATA_EDIT (SUPER_ADMIN, ADMIN); keep USER_MANAGE.

## Addition
`equipment_type` has `is_seeded`. SUPER_ADMIN can add new types in the UI. Seeded types cannot have their code
changed or be deleted - enforced in E2 (backend) and E5 (UI).

## Implementation notes (small additions beyond the proposal)
- `equipment_type` has an `id` (seed ids 1-17, sequence from 1000) so equipment can reference it by foreign key.
- CHECK constraints: capacity >= 0, link cost >= 0, a link cannot point to itself, a HU type cannot nest in itself,
  `max_qty` > 0 when given.
