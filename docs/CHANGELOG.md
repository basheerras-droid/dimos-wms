# Changelog

## 2026-11-06
- `deploy/backup-wms.ps1`: daily pg_dump (custom format) of `wms_dev` with a pgpass file, dated folders, readability check,
  retention (`-KeepDays`, default 14), log, exit code 1 on failure. `deploy/README.md` section 6: Task Scheduler setup on
  WMS-TEST-SRV (daily 02:00, SYSTEM), manual run on the laptop, test restore into `wms_restore_test`.
- Task description for Step E (master data) added: `docs/tasks/STEP-E-master-data.md`.
- Step E1: Flyway V4 adds master-data tables (`area`, `location_type`, `hu_type`, `location`, `location_link`,
  `equipment_type`, `equipment`, `equipment_location`, `location_type_hu_type`, `hu_type_nesting`), the 13 seeded
  equipment types (`is_seeded`) and permissions MASTERDATA_VIEW (all roles) / MASTERDATA_EDIT (SUPER_ADMIN, ADMIN).
  Approved design saved in `docs/design/E1-masterdata-tables.md`.
- `deploy/README.md` 6c: added the pgpass line for `wms_restore_test`.
- Step E2: new Spring Modulith module `masterdata` with REST CRUD under `/api/masterdata/` for areas, location-types,
  hu-types, locations, location-links, equipment and equipment-types (paging max 200, sorting on a safe list, search by
  code/name, filters). Stale update -> HTTP 409 "This record was changed by someone else - reload"; unknown references,
  duplicate codes, duplicate links, nesting loops and equipment loops are refused with field messages; records still in use
  cannot be deleted (409 IN_USE). Bulk block/unblock/enable/disable on locations (`POST /locations/bulk`). Assignments:
  `/location-types/{id}/hu-types`, `/equipment/{id}/locations`, `/hu-types/{id}/placed-in`. Equipment types: only
  SUPER_ADMIN may add/rename/delete; the 13 built-in types keep their code and cannot be deleted. Every create/update/delete
  is written to `audit_log` with before/after JSON. Login session now carries permissions (`/api/auth/me` returns
  `permissions`); endpoints use MASTERDATA_VIEW / MASTERDATA_EDIT.
- Step E3: spreadsheet import/export for 9 tables (areas, location-types, hu-types, locations, location-links, equipment,
  equipment-locations, location-type-hu-types, hu-type-nesting) as CSV (comma/semicolon/tab, UTF-8 or Windows-1252) or XLSX
  (Apache POI 5.5.1, commons-csv 1.14.1, both Apache 2.0). `POST /api/masterdata/import/{type}?dryRun=true|false`:
  dry run runs the real import in a transaction that is always rolled back; Apply writes all rows or nothing (HTTP 422 if any
  row is wrong) and writes the audit rows only after a successful commit. A row whose code exists updates it (unchanged rows
  are left alone: no new version, no audit); a column left out of the file keeps the stored value. Templates:
  `GET /import/{type}/template`, exports: `GET /export/{type}`, table list: `GET /import/types`. Limits: 10 MB, 50 000 rows.
  CSV exports defuse spreadsheet formulas (leading ' on =, +, @). An update with no real change no longer creates a new version.
- Step E4: configuration validator, `GET /api/masterdata/validation` (MASTERDATA_VIEW). 14 rules, each PASS / WARNING / ERROR with
  the records concerned (entity type + id, max 200 per rule, full count): enabled links to disabled locations or equipment,
  duplicate links, same from/to pair with an all-types link AND a type-specific link (WARNING), locations with no incoming /
  outgoing link (storage bins, workstations, inbound/outbound ends exempt), storage not reachable from an inbound location or
  with no path to an outbound location, location types accepting no HU type (ERROR when used), HU types accepted nowhere,
  equipment without locations, a location claimed by two equipment of the same type, locations at the same position/level,
  locations without x/y. Measured: 20 001 locations + 39 998 links in 215 ms.
- Step E5.1 (front end): collapsible "Master data" menu group (shown only with MASTERDATA_VIEW, route guard too);
  generic list page with MUI DataGrid community 9.17.0 (MIT): server paging/sorting/search/filters, column chooser, CSV/XLSX
  export, side drawer for create/edit/view with required marks, server errors next to the field, the 409 "changed by someone
  else - reload" message with a Reload button, delete with confirmation. Screens: Areas, Location types, HU types. Edit buttons
  appear only with MASTERDATA_EDIT. `/api/auth/me` permissions are used by the UI (`useCan`).
- Fix: Master data -> Location types answered HTTP 500 when the search box was empty (the controller passed "no filter" as
  null and combining a query with null failed). The service now accepts "no filter" and the controller was aligned with the
  others. New test `ListEndpointsTests` opens the list of EVERY master-data table with default sort, paging, search text, every
  sort field (asc and desc) and the web screens' filters, and checks that every table is covered.
- Build version: pom version is now 0.1.0 (jar `wms-backend-0.1.0.jar`; deploy README picks the newest jar). Spring Boot
  build-info + git-commit-id plugin 9.2.0 (only branch, short commit, commit time, dirty flag - no names or e-mails). The real
  build time is used (the Spring Boot parent would pin it to 1980-02-01). `/actuator/info` is open without login and shows only
  build version/time and git commit (env, os, java, process are off). Footer and login page show e.g.
  "DIMOS WMS 0.1.0 - build 2026-11-06 19:05 - commit 36232c2" (plus "(with uncommitted changes)" for a dirty working tree).
- UI: side navigation panel 240 px wide, labels on one line (ellipsis + title if too long), sub-items 13 px; the hamburger still
  collapses it to icons.
- Step E5.7.2 (Flyway V7): **User groups and effective rights**. Administration -> **User groups**: code, name, description, roles,
  members (searchable picker), enabled, and `external_ref` reserved for an Active Directory / LDAP group name (not used yet).
  Rights of a user = direct roles + roles of every enabled group the user is in; permissions = union (`RightsService`); this is what
  the login now puts in the session. The user drawer shows the groups and the **effective permissions** by module with the reason
  (role, or role through group). Rules: only a SUPER_ADMIN may give a group the SUPER_ADMIN role or change such a group; a user who
  is super administrator through a group is protected like one with the direct role; saving or deleting a group signs its members
  out; roles show how many groups use them and cannot be deleted while a group does. Audit: GROUP_CREATED / UPDATED / DELETED.
- Step E5.7.1 (Flyway V6): **Permissions and Roles**. Permission catalogue fixed in code (`PermissionCatalogue`: code, module,
  description), checked against the database at start-up and by a test; new permissions ROLE_MANAGE, WORKSTATION_MANAGE,
  PARAMETER_VIEW, PARAMETER_EDIT (default grants as agreed). Administration -> **Permissions**: read-only, grouped by module, shows
  which roles hold each one. Administration -> **Roles**: tick matrix roles x permissions; the four built-in roles are locked
  (`is_builtin`); custom roles can be created, copied, edited and deleted, by a SUPER_ADMIN only (also needs ROLE_MANAGE); deleting
  a role in use says by whom; saving a role signs out its holders. A custom role code that would turn into a permission
  authority (ROLE_ + code, e.g. "MANAGE") is refused. Audit: ROLE_CREATED / ROLE_UPDATED / ROLE_DELETED with before and after.
  Test fix: `SecurityWebTests` has its own application context (the `csrf()` test helper permanently changes a shared one).
- Step E5.6: **Master data -> Layout preview** (MASTERDATA_VIEW), read-only. Backend `/api/masterdata/layout/plan|racks|rack-face`
  (plain SQL, compact JSON). **Plan view**: one level at a time, locations by x / y coloured per area (legend chips hide / show an
  area), links as lines (dashed = disabled; arrows and location codes appear when zoomed in), disabled = hollow dashed, blocked =
  ring + cross, zoom with wheel or buttons, drag to pan, "show everything", click a location for details and "Open record" (opens
  its drawer); warnings for locations without x / y and for links to other levels. **Rack face** (for INL: about 74 columns x 12
  levels per aisle side): choose aisle + side, grid of column_no (across) by level_no (up), one cell per location coloured by state
  with patterns (disabled hatched, blocked outbound striped, blocked both cross-hatched) so colour is not the only cue, a fill-level
  figure (usable = enabled and not blocked, rounded down; real occupancy is shown once inventory exists), warnings for two locations
  in one cell and locations without a column, click a cell to see / open the location. Colours are theme tokens (`palette.layout`).
  Tested with a 74 x 12 x 2-sided rack (889 cells in well under a second).
- Import: a CSV that mixes separators (e.g. TAB in the header, commas in the rows) now gives ONE file-level message ("The header
  row uses TAB but row 2 uses comma - use one separator in the whole file.") instead of one confusing error per row. Every problem
  with the file as a whole (wrong type, empty, too many rows, unreadable, mixed separators) is now shown that way in the check
  result (and Apply is refused with 422) instead of an error page. The CSV template uses commas (UTF-8 with byte-order mark so
  Excel shows special characters); uploads accept comma, semicolon or TAB, detected from the header row.
- Step E5.5: **Administration -> Users** and **Audit log** (group shown when the user has USER_MANAGE and/or AUDIT_VIEW).
  Users (`/api/admin/users`, permission USER_MANAGE): list with search / status / role filters, create, edit display name, e-mail,
  roles, enable / disable, unlock, reset password. The administrator never chooses or sees a real password: a random 12-character
  temporary password is generated and shown once (never stored readable, never in the audit log, answer is `no-store`); the user
  must change it at the next login. Rules: only SUPER_ADMIN can create / change / reset a SUPER_ADMIN or give that role; nobody can
  disable themselves, change their own roles or reset their own password here; at least one enabled SUPER_ADMIN must remain;
  user names are fixed; stale edits get the 409 "changed by someone else" message. Disabling a user or resetting a password
  signs the user out at once (open sessions are tracked per user). Audit actions: USER_CREATED, USER_UPDATED, USER_ENABLED,
  USER_DISABLED, USER_PASSWORD_RESET, USER_UNLOCKED (before/after = user name, display name, e-mail, enabled, roles only).
  Audit log (`/api/admin/audit`, permission AUDIT_VIEW): newest first, filters user / action / table / record id / text (also
  searches the before and after values) / from-to date-time, paging, click a row for the full details with before and after values
  side by side. The audit module only exposes a query service (no dependency on identity).
- Clearer "still used" message: deleting a record that others use now says what is in the way, e.g. "Equipment TEST-CONV cannot be
  deleted: it is still used by 1 piece of sub-equipment (it is their parent). Remove or change those first." (areas, location types,
  HU types, locations, equipment, equipment types). Checked before the delete, because the database aborts the transaction otherwise.
- Fix: the Equipment list answered HTTP 500 for some sort orders ("Code, descending", ...) when a piece of equipment was listed
  after its sub-equipment: the lazily loaded parent was a Hibernate placeholder whose fields read as empty. The parent is now loaded
  normally. Found by `ListEndpointsTests` on real data; regression test added. Also: `ResponseStatusException` (e.g. an unreadable
  date) is answered with its own status (400) instead of 500.
- Step E5.4 (front end): Master data -> **Import** (needs MASTERDATA_EDIT): choose the table (listed in load order), download the
  template (CSV/Excel) or the current data, see what each column means, upload a file and "Check file" (dry run: counts of new /
  update / add / unchanged / errors, per-row table with an "only errors" switch, file-level problems such as unknown or missing
  columns), then **Apply** after a confirmation (all rows or none; a refused apply shows the wrong rows). Master data -> **Check
  configuration**: overall result, rules with errors/warnings first (expanded for errors) and each finding linked to its record
  (`?open=<id>` opens the record's drawer on the list page), passed rules listed below, "Check again" button, shows what was
  checked and how long it took. Fix: every master-data page now has its own React key, so search text, filters and sort of one
  screen can no longer leak into the next one.
- Step E5.3: Master data -> Equipment (static configuration; the top-level "Equipment (WCS)" entry stays for live status in
  Step F/H) with list, filters (type, area, enabled) and drawer (type, area, parent equipment, PLC connection, external reference,
  extra attributes); Equipment types screen (built-in types shown with a lock, read-only; custom types can be added, renamed and
  deleted by SUPER_ADMIN only); three assignment editors opened from a button on the row: equipment -> locations (add one by one,
  or add all locations of an area / matching a text, max 5 000 at once, remove selected, nothing saved until Save), location type ->
  allowed handling-unit types, HU type -> types it may be placed in / on (with optional max quantity; loops are refused by the
  server). Backend: assignment endpoints now also return code and name of each assigned record; equipment list shows the parent code;
  the equipment-types list uses the same paged shape as all other lists.
- Step E5.2 (front end): Locations screen (all fields, filters area / location type / level / enabled / blocked, hidden-by-default
  columns for position, weight, reason; tick several rows and block / unblock inbound or outbound, enable or disable them in one
  go, with a reason dialog when blocking, max 1 000 rows) and Location links screen (from / to location chosen by searching the
  server, handling-unit type empty = "All types", optional equipment, cost). New searchable reference field (`RefField`, 20 matches
  at a time from the server) that scales to thousands of locations. Menu: Areas, Location types, Locations, Location links, HU types.
- UI: dark side navigation (Schwarzgrau panel, text #E8EAEC, sub-items #B9BEC2, selected = 4 px Blutorange bar + rgba(215,44,6,0.18)
  background + white text, hover #3A4047). Colours are theme tokens (`palette.nav`). Contrast checked: all text at least 5.6:1.
- Flyway V5: four more built-in equipment types (ETV, MONORAIL, TURNTABLE, WASHER; 17 in total). CLAUDE.md now lists the three
  reference projects (EKFC, DFC, INL).

## 2026-11-05
- Brand rules: only the standard DIMOS logo, on a white top bar; negative logo removed.
- `build/` (generated by Spring Modulith) is now git-ignored.
- Step D1: Flyway V3 adds `site`, `app_user`, `app_role`, `app_permission`, `user_role`, `role_permission`,
  `audit_log` plus seed data (site DXB-TEST, 4 roles, users `admin` and `rasheed` as SUPER_ADMIN, no passwords yet).
- Step D2: on startup, users without a password get one from env var WMS_INIT_PASSWORD_<USERNAME> (BCrypt via
  DelegatingPasswordEncoder, must change at first login, audited, never logged). New modules `audit` and `identity`.
- Step D3: session login (`/api/auth/login|logout|me|change-password`), lockout after 5 failures for 15 min, cookie CSRF,
  forced password change enforced on the server, audit of login/failure/lockout/logout/password change, correlation id
  per request, JSON error handler, no generated Spring password. `/actuator/health` public, rest of `/api` needs login.
- Step D4: React + MUI front end (Metropolis font self-hosted, white top bar with the standard logo, Schwarzgrau text,
  Blutorange accent only): login, forced change-password, collapsible side menu, dashboard with placeholder KPIs, i18next (en),
  TanStack Query, React Router. Build into the JAR with `.\mvnw.cmd -Pfrontend clean package` (run in `backend/`).
  Negative logo removed again from `frontend/public/brand/`.
- Step D5 (part 1): profile `test` (HTTPS 8443, keystore from env), WinSW service definition and step-by-step `deploy/README.md`.
  Certificate, firewall rule and service install are done on the server after approval (not in Git).
- Step D5 done on WMS-TEST-SRV: self-signed certificate (D:\WMS\certs\wms-test.p12), firewall rule "DIMOS WMS HTTPS 8443"
  (inbound TCP 8443, Private/Domain, LocalSubnet), Windows service `dimos-wms` (WinSW 2.12.0, automatic start, restart on failure).
  HTTPS answers on localhost, 192.168.1.60 and WMS-TEST-SRV; PostgreSQL still listens on localhost only.
- Fix: accounts without a password are refused but no longer count towards lockout (they could be locked by anyone before the first password existed).
- New passwords must have at least 8 characters (was 12), checked on the server and in the web page.
