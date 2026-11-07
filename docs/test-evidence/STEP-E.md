# Step E - test evidence

## E6 - import / project-load timing (2026-11-07)

Machine: the owner's laptop, PostgreSQL 18 on localhost, real audit log (every change audited), Java 25.
Test: `ImportTimingTests` (5,000 locations, 10,000 links, private test site) and `ProjectLoadTests` (demo plant).
The times vary between runs by a factor of 2-6 with the load of the machine (OneDrive, browser, etc.); ranges over several runs:

| Operation | Rows | Before optimisation | After (range of runs) |
|---|---|---|---|
| Locations, dry run | 5,000 | 39.6 s | 7 - 17 s |
| Locations, apply | 5,000 | 91.8 s | 5 - 12 s |
| Links, dry run | 10,000 | 412 s (stopped) | 8 - 51 s |
| Links, apply | 10,000 | not finished | 9 - 26 s |
| Same files again (all unchanged) | 5,000 / 10,000 | - | 2.5 - 11 s / 5 - 18 s |
| Demo plant, Replace (13 files, 5,351 locations, 10,680 links, 5,351 assignments) | 21,500 | - | about 100 s |

Optimisations: persistence session flushed and cleared every 100 rows (the cost per row no longer grows with the file), audit rows
of a file written in one batch instead of one transaction per row. Possible later optimisation if needed: cache code-to-id lookups.

Demo plant: configuration check = PASS, 0 errors, 0 warnings.

## E7 acceptance
(to be filled in after the browser tests)
