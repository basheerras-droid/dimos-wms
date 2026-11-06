# Step E - Master data and administration

Goal: the WMS knows the physical plant - sites, areas, locations and how they connect, equipment, and handling-unit
types - and administrators can manage users and this data from the browser, by hand or by spreadsheet import.
This is the foundation for Step F (plant overview + simulator) and Step G (transport orders + routing).

Read `CLAUDE.md` first. Before writing code for E1, show the owner the proposed tables (names, key columns,
relations) in a short table and wait for "OK". Then work part by part; build, test, commit and push after each part.
Explain each part in 2-3 simple sentences.

Design lessons to apply (from analysing legacy systems - use the ideas only, never copy their code, SQL or screens):
- Locations form a graph: each link says "from location A you can move to location B", optionally only for
  certain handling-unit types, with a cost. Routing in Step G searches this graph.
- Which handling-unit type fits which location type must be configurable.
- Handling units nest (carton on pallet, pallet in ULD, bin on cart).
- Customer projects are configuration, not code: the same product loads a different data set per project.
- Most go-live failures come from bad configuration, so a configuration validator is part of the product.

## E1 - Data model (Flyway V4+, portable SQL in `common/`)
All business tables: `site_id`, `version`, `created_at/by`, `updated_at/by`. Codes unique per site.
- `area` - zone of a site (code, name, type e.g. INBOUND, STORAGE, OUTBOUND, BUFFER, WORKSTATION, TRANSPORT).
- `location_type` - (code, name, is_storage, is_transfer, is_workstation, capacity_hu).
- `location` - (code, name, area, location_type, x_mm, y_mm, z_mm, level, aisle, side, column_no, row_no,
  max_weight_kg, enabled, blocked_in, blocked_out, block_reason, external_ref for PLC/host address).
- `location_link` - (from_location, to_location, hu_type nullable = all types, cost, enabled, equipment nullable).
- `equipment_type` - seed: CONVEYOR, TRANSFER, HOIST, LIFT, STACKER_CRANE, SHUTTLE, AGV, ROBOT, STATION,
  SCANNER, SCALE, PRINTER, RACK.
- `equipment` - (code, name, equipment_type, area, parent equipment nullable, plc_connection nullable for later,
  enabled, external_ref).
- `equipment_location` - which locations belong to which equipment.
- `hu_type` - handling-unit type (code, name, length_mm, width_mm, height_mm, tare_kg, max_gross_kg, is_container).
- `location_type_hu_type` - allowed combinations.
- `hu_type_nesting` - which HU type may be placed in/on which (e.g. CARTON in PALLET, PALLET in ULD, BIN on CART).
- Optional project-specific extra fields: an `ext_attributes` TEXT column (JSON string) on location and equipment,
  so projects can add fields without schema changes. Never use vendor JSON types in `common/`.
- Permissions: MASTERDATA_VIEW, MASTERDATA_EDIT, USER_ADMIN; give them to SUPER_ADMIN and ADMIN as appropriate.

## E2 - Backend (Spring Modulith module `masterdata`)
- REST CRUD for every entity: paging, sorting, search by code/name, filters (area, type, enabled, blocked).
- Bean validation with clear messages; references checked (no link to an unknown location, etc.).
- Optimistic locking: a stale update returns HTTP 409 with "This record was changed by someone else - reload".
- Bulk actions on locations: block/unblock in/out with reason, enable/disable.
- Every create/update/delete written to `audit_log` with before/after values.
- Module boundary test (Spring Modulith `ApplicationModules.verify()`) must pass.

## E3 - Import and export (spreadsheets)
- Import per entity from CSV and XLSX (Apache POI, Apache 2.0 licence). Two steps:
  1. Dry run: parse and validate, show per-row results (OK / error with reason), change nothing.
  2. Apply: write all rows in one transaction, or nothing if any row fails.
- Download a template per entity with header row and one example row.
- Export current data to CSV and XLSX.
- Import order documented: areas -> location types -> HU types -> locations -> links -> equipment -> assignments.

## E4 - Configuration validator
Endpoint and screen "Check configuration" that reports PASS / WARNING / ERROR per rule, with links to the records:
- links pointing to missing or disabled locations; duplicate links (same from + to + HU type + equipment)
- WARNING when the same from/to pair has both an all-types link (HU type empty) and a type-specific link
  (owner's addition, 2026-11-06)
- locations with no incoming or no outgoing links (except allowed dead ends such as storage bins)
- storage locations unreachable from any inbound location, or with no path to any outbound location
- location types that accept no HU type; HU types accepted nowhere
- equipment with no locations; locations claimed by two pieces of equipment of the same type
- two locations at the same coordinates on the same level
The validator must run in under 5 seconds for 20 000 locations.

## E5 - Frontend
Menu changes (all text through i18n, brand rules from CLAUDE.md):
- New group **Master data**: Areas, Locations, Location links, Equipment, HU types, Check configuration.
- **Administration**: Users (list; create; edit display name, email, roles; enable/disable; reset password ->
  must change at next login), Audit log (read-only, filter by user/date/entity).
- Grids: MUI DataGrid (community, free) with search, filters, column chooser, CSV export button, paging from server.
- Edit forms in a side drawer; required fields marked; server errors shown next to the field.
- Locations: bulk select -> block/unblock/enable/disable.
- **Layout preview** (first version of the plant view): a 2D SVG plot of locations by x/y per level, coloured by
  area, links drawn as lines, blocked locations marked, zoom and pan, click a location to open its record.
  Keep it simple; Step F turns it into the live overview.
- Import screen: choose entity -> download template -> upload -> dry-run result table -> Apply.

## E6 - Demo project data (product/project separation)
- Create folder `projects/demo/masterdata/` with CSV files for a small generic demo plant (no customer data):
  1 site, areas INBOUND / STORAGE / OUTBOUND, an inbound conveyor line of 6 locations, one hoist serving 3 levels,
  a small rack (2 aisles x 2 sides x 5 columns x 3 levels), 2 outbound stations, an AGV path joining them,
  HU types PALLET, ULD, CART, BIN with sensible nesting.
- A documented way to load a project's data set (admin screen button "Load project data" or a command), so later
  `projects/ekfc/` can be loaded the same way. Customer-owned material (PLC programs, drawings) never goes in Git.
- The configuration validator must report PASS (warnings allowed) for the demo data.

## E7 - Tests, docs and acceptance (record results in docs/test-evidence/STEP-E.md)
1. Unit and integration tests for services, import (good file, bad file) and validator rules.
2. Load the demo data on the laptop; validator shows PASS; layout preview shows the demo plant on each level.
3. Create, edit and delete a location from the UI; a second browser editing the same record gets the 409 message.
4. Import a CSV with one bad row: dry run shows the row error; Apply is refused; nothing is written.
5. As `admin`, create a user with role OPERATOR; that user cannot open Master data edit screens.
6. Audit log shows the changes made in tests 3-5.
7. Update docs/CHANGELOG.md and write docs/guides/master-data-import.md (column meanings, examples, import order).
