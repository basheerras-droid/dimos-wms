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
- **Layout preview, second view: rack face** (owner's note, 2026-11-06; needed by the INL reference project - pallet AS/RS,
  about 74 columns x 12 levels per aisle side). Choose an aisle and a side; show a grid with `column_no` on the x axis and
  `level_no` on the y axis, one cell per location, coloured by state (enabled / disabled / blocked in / blocked out / both),
  a fill-level figure for that aisle side, and a click on a cell opens the location record.
  Implementation notes:
  - Needs a small backend call for it (one aisle side = about 900 cells, more than the 200-row list limit): the list of
    aisles/sides of the site, and the compact cells of one aisle side (id, code, column_no, level_no, state flags).
  - The fill-level figure needs stock data, which comes with the inventory step. Until then show the share of usable
    locations (enabled and not blocked) and mark "occupancy: available with inventory"; keep the field ready for stock.
  - Possible extra validator rule (E4 follow-up): two locations with the same aisle + side + column_no + level_no.
- Import screen: choose entity -> download template -> upload -> dry-run result table -> Apply.

## E5.7 - Administration extended (owner's request, 2026-11-07)
Status: DONE 2026-11-07 in four parts (Flyway V6-V9); design in `docs/design/E5.7-administration-tables.md` (approved).
Owner's extra rules: (A) a workstation-group restriction never removes WORKSTATION_MANAGE from SUPER_ADMIN, and a SUPER_ADMIN
can always "unregister this browser" from the device (back to office mode); (B) security bounds enforced on the server and
fixed in the parameter definitions: password minimum length >= 8, lockout attempts 3-10, lockout minutes 5-1440, session timeout
5 minutes - 12 hours. The workstation cookie is long-lived (10 years); a browser that lost it shows "not registered" and an
administrator can register it again.
Common rules: every new business table has `site_id`, `version`, audit columns; portable SQL in `common/`; every change
is written to the audit log.
1. **Permissions**: catalogue fixed in code (code + module + description), read-only screen grouped by module.
   New permissions: ROLE_MANAGE, WORKSTATION_MANAGE, PARAMETER_VIEW, PARAMETER_EDIT.
2. **Roles**: built-in roles locked; custom roles can be created, copied, edited and deleted; screen = tick matrix
   (roles x permissions). Only SUPER_ADMIN edits roles.
3. **User groups**: members + roles; users inherit the roles of their groups; a user may be in several groups and also
   have direct roles; effective rights = union. The user drawer shows the effective permissions. Prepare (do not build)
   the mapping to LDAP / AD groups.
4. **Workstations**: code, name, type (PC, HANDHELD, HMI, KIOSK), location (master-data location), workstation group,
   optional host name / IP, default printer (text for now), enabled. Browser registration: an administrator registers
   a browser as a workstation once (random token kept in the browser, only a hash in the database); optional IP check.
   An unregistered browser = office mode. The current workstation is shown in the top bar.
5. **Workstation groups**: optional list of allowed permissions; effective rights = user rights AND the workstation
   group's allowed list (empty list = no restriction). Enforced on the server.
6. **Parameters**: `parameter_definition` seeded by Flyway (code, type INT / BOOL / TEXT / CHOICE / DURATION, default,
   min, max, allowed scopes, restart needed, description) and `parameter_value` per scope GLOBAL / SITE / WS_GROUP /
   WORKSTATION; the most specific value wins. Screen: list with the effective value and where it comes from; edit with
   validation; every change audited. Seed: session timeout, password minimum length, lockout attempts, lockout
   minutes, audit retention days. The existing lockout and session settings read these parameters.
Tests: inheritance via group; a workstation restriction blocks MASTERDATA_EDIT at a shop-floor workstation;
parameter override order; a built-in role cannot be changed.

## E6 - Demo project data (product/project separation)
- Create folder `projects/demo/masterdata/` with CSV files for a small generic demo plant (no customer data):
  1 site, areas INBOUND / STORAGE / OUTBOUND, an inbound conveyor line of 6 locations, one hoist serving 3 levels,
  a small rack (2 aisles x 2 sides x 5 columns x 3 levels), 2 outbound stations, an AGV path joining them,
  HU types PALLET, ULD, CART, BIN with sensible nesting.
- A documented way to load a project's data set (admin screen button "Load project data" or a command), so later
  `projects/ekfc/` can be loaded the same way. Customer-owned material (PLC programs, drawings) never goes in Git.
- The configuration validator must report PASS (warnings allowed) for the demo data.
- Also generate one rack of the INL size (74 columns x 12 levels x 2 sides per aisle, a few aisles) for the rack-face view
  and the performance measurements.
- Also measure import time (dry run and apply) with a larger generated set - e.g. 5,000 locations and 10,000 links -
  because the first real project data (DFC) will be that size. Record the times in `docs/test-evidence/STEP-E.md`
  and optimise the importer if it is slow (owner's request, 2026-11-06).

## E7 - Tests, docs and acceptance (record results in docs/test-evidence/STEP-E.md)
1. Unit and integration tests for services, import (good file, bad file) and validator rules.
2. Load the demo data on the laptop; validator shows PASS; layout preview shows the demo plant on each level.
3. Create, edit and delete a location from the UI; a second browser editing the same record gets the 409 message.
4. Import a CSV with one bad row: dry run shows the row error; Apply is refused; nothing is written.
5. As `admin`, create a user with role OPERATOR; that user cannot open Master data edit screens.
6. Audit log shows the changes made in tests 3-5.
7. Update docs/CHANGELOG.md and write docs/guides/master-data-import.md (column meanings, examples, import order).
