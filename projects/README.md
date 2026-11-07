# Project data sets

A project data set is a folder with a `project.json` and the CSV files it lists. It holds everything that makes
one customer project different from the base product: areas, location types, locations (with coordinates), links,
equipment, handling-unit types, and also user groups, workstation groups, workstations and parameter overrides.

| Folder | What it is |
|---|---|
| `projects/demo/` | A generic demo plant invented by DIMOS. No customer data. Shipped inside the JAR. |
| `projects/<customer>/` | Not in Git for customer-owned data. Put it in the server folder given by `wms.projects.dir`. |

**Customer-owned material (PLC programs, drawings, customer data) never goes into Git.** Only invented or DIMOS-owned
data sets (like the demo) live in this folder.

## `project.json`

```json
{
  "code": "demo",
  "name": "Demo plant",
  "description": "Shown on the screen",
  "files": [
    { "type": "areas",     "file": "areas.csv" },
    { "type": "locations", "file": "locations.csv" }
  ]
}
```

The files are loaded **in the order listed**. A row can only refer to records that were loaded before it
(areas and location types before locations, locations and equipment before links, and so on).

File types for master data (columns: the templates on the Import screen): `areas`, `location-types`, `hu-types`,
`locations`, `equipment`, `location-links`, `equipment-locations`, `location-type-hu-types`, `hu-type-nesting`.

File types for administration:

| Type | Columns |
|---|---|
| `user-groups` | `code, name, description, enabled, roles` (roles separated by `;`). Members of an existing group are kept. |
| `workstation-groups` | `code, name, description, enabled, allowed_permissions` (separated by `;`) |
| `workstations` | `code, name, type (PC/HANDHELD/HMI/KIOSK), location_code, workstation_group_code, host_name, ip_address, check_ip, default_printer, enabled` |
| `parameters` | `parameter, scope (SITE/WS_GROUP/WORKSTATION), scope_code, value` |

## Loading

**On the screen** (SUPER_ADMIN): *Master data > Project data*. Choose the data set, choose the mode, press **Check** (it does
the whole load and then undoes it), then press the load button.

* **Replace master data** deletes all master data of the site first (areas, location types, HU types and their nesting and
  assignments, locations, links, equipment and its assignments, custom equipment types), then loads the files.
  Users, roles, permissions, user groups, workstation groups, parameters, the audit log and the site are **kept**.
  Workstations are **kept**; their location reference is cleared first (the data set sets it again where it names a location).
  The screen lists what will be deleted and asks you to type the project name.
* **Add / update** adds new records and updates existing ones; nothing is deleted.

Both modes run in **one transaction**: everything is loaded or nothing is changed. The audit log gets one `PROJECT_LOAD`
entry with the counts deleted and loaded, plus the usual entry for every changed record.

**From the command line** (no web screen; defaults to a dry run that changes nothing):

```powershell
java -jar wms-backend.jar --spring.main.web-application-type=none --wms.project-load.code=demo `
     --wms.project-load.mode=REPLACE --wms.project-load.dry-run=false --wms.project-load.confirm="Demo plant"
```

Stop the running application first if it uses the same port; the command needs no web port.

## Changing the demo plant

`projects/demo/generate_demo.py` writes the CSV files (`python generate_demo.py`). Change the layout there and commit the new CSV files.
