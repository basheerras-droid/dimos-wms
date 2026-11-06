# DIMOS WMS/WCS - project instructions for Claude Code

DIMOS FZCO (Dubai) is a material handling systems (MHS) integrator. This repository is the
DIMOS-owned WMS/WCS product: one base product, adapted per customer project by configuration.
Owner: Abdul Rasheed (a.rasheed@dimos-me.com). He is learning; explain what you do in plain
English, work in small steps, and tell him which machine each manual step runs on.

## Environment (this is a Windows server, not Linux)
- Server: WMS-TEST-SRV, Windows Server 2025, IP 192.168.1.60 (office LAN 192.168.1.0/24, gateway 192.168.1.250).
- Repo: D:\WMS\src\dimos-wms  -> GitHub https://github.com/basheerras-droid/dimos-wms (private, branch main).
- Other folders: D:\WMS\app (deployed build), D:\WMS\logs, D:\WMS\certs, D:\WMS\tools, E:\WMS-Backup.
- Shell: PowerShell 5.1. When writing files from PowerShell use `-Encoding ASCII` (UTF8 adds a BOM that breaks
  psql and javac). Prefer your own file tools over shell redirection.
- Java 25 (Temurin), Spring Boot 4.1.x, Spring Modulith 2.1.x, Maven Wrapper (`.\mvnw.cmd`), Node.js 26 LTS, npm.
- PostgreSQL 18 on localhost:5432 only (listen_addresses = localhost). Database `wms_dev`, schema `wms`.
  - `wms_owner` runs Flyway migrations; `wms_app` is the runtime user (DML only).
  - Passwords are machine environment variables WMS_DB_OWNER_PASSWORD / WMS_DB_APP_PASSWORD. Never print,
    log, commit or hard-code them. Never change PostgreSQL server configuration without asking.

## Architecture (approved)
- WMS Core: Spring Boot modular monolith in `backend/` (package `com.dimos.wms.<module>`), Spring Modulith
  enforces module boundaries. Modules talk through public APIs or events, never through each other's internals.
- WCS Gateway: separate Spring Boot service in `wcs-gateway/` (later phase) - equipment drivers (OPC UA, S7,
  Modbus, fixed-length TCP telegrams, VDA 5050/MQTT). The WMS never talks to PLCs directly.
- Simulator in `simulator/` (later phase). Frontend in `frontend/` (React + TypeScript + Vite + MUI core).
- The built frontend is served by the Spring Boot backend so one JAR runs everything.
- Messaging later: MQTT (Eclipse Mosquitto) + transactional outbox (Spring Modulith event_publication table).

## Database rules
- Every schema change is a Flyway migration. Never use ddl-auto other than `validate`.
  - `db/migration/common/`  - portable SQL that runs on PostgreSQL AND SQL Server.
  - `db/migration/postgresql/` and `db/migration/sqlserver/` - vendor-specific scripts only.
  - Never edit an applied migration; add a new version (V3, V4, ...).
- Prefer portable types: VARCHAR/NVARCHAR-compatible text, BIGINT ids (sequences) or UUID, TIMESTAMP WITH TIME ZONE
  mapped to java.time.Instant, BOOLEAN handled via JPA. No stored procedures, no vendor functions in common/.
- Business tables carry: `site_id`, `version` (optimistic lock, @Version), `created_at`, `created_by`,
  `updated_at`, `updated_by`. Single client for now (no client_id).
- Inventory (later) is ledger-based: immutable stock transactions; balances derived. Handling units can nest
  (carton -> pallet -> ULD). Lot, serial, expiry/FEFO supported.

## Security rules
- No secrets in Git. Config comes from environment variables or a server-only `application-local.yml` (gitignored).
- Passwords: BCrypt or Argon2 via Spring Security's DelegatingPasswordEncoder. Account lockout after 5 failures.
- Session cookie HttpOnly + Secure + SameSite=Lax; CSRF protection on for browser endpoints.
- Validate all input server-side; parameterised queries only.
- Audit important actions (login success/failure, user changes) to an audit table: who, what, when, before/after.
- Login: local users now; LDAP / Active Directory later (keep the authentication layer pluggable).

## UI and brand (from the DIMOS Corporate Design Manual, Jan 2023)
- Colours: Schwarzgrau #2F3339 (base: text, headings), Blutorange #D72C06 (accent: primary buttons, active
  states, alerts - use sparingly), Fenstergrau #90979B (secondary/borders/muted), white #FFFFFF, text 90% black #1A1A1A.
  Light page background #F4F5F6.
- Font: Metropolis (free, SIL Open Font License) - self-host the woff2 files in `frontend/src/assets/fonts/`,
  fallback `"Segoe UI", Arial, sans-serif`. Keep its licence file next to the fonts.
- Logo: use ONLY `frontend/public/brand/dimos-logo.png` (the original logo), always on a white or light
  background. Never stretch or recolour it, never put it on a dark or busy background; keep clear space
  around it at least the height of the lettering.
- App top bar is WHITE with the logo. Schwarzgrau #2F3339 is for text and headings; Blutorange #D72C06 is only
  the accent.
- English only in version 1, but ALL user-visible text goes through i18next translation files (`frontend/src/i18n/`).
- Must look professional and modern, and must NOT imitate other vendors' WMS screens (e.g. Unitechnik Uniware).
- Desktop first, but layouts must also work on 5-6" handheld scanners later (responsive MUI layout).

## Working rules
- Small, reviewable steps. After each step: build, run tests, show the result, then commit with a clear message
  and `git push`. Explain what changed in 2-3 plain sentences.
- Ask before: deleting files outside the repo, dropping tables or data, changing Windows/firewall/PostgreSQL
  configuration, or installing global software.
- Never copy code, SQL or screen designs from Unitechnik/Uniware or any other vendor. Their material may be used only to
  understand functions.
- Free/open-source dependencies with commercial-friendly licences only (no MUI X Pro/Premium). Pin versions.
- Keep a short changelog in `docs/CHANGELOG.md`.

## Reference projects
The product is designed so that these three customer projects can be delivered by configuration of the same base
product. Use them to judge whether a feature or data-model decision is general enough. (Customer-owned material -
PLC programs, drawings, data - never goes in Git.)
- **EKFC** - monorail system with hangers/carts.
- **DFC** - ULD/PMC transport and storage with ETVs (elevating transfer vehicles) and turntables. First real project data;
  expected size about 5,000 locations and 10,000 links.
- **INL** - pallet conveyors, stacker cranes and high-bay storage.
