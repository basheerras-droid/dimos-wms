# Step D - First demonstrator

Goal: from another office PC, open `https://192.168.1.60:8443` (or `https://wms-test-srv:8443`) in Chrome/Edge,
log in, and see a DIMOS-branded application shell with an empty dashboard. This proves the full chain
browser -> HTTPS -> Spring Boot -> PostgreSQL.

Read `CLAUDE.md` first. Work in the numbered order below; build, test, commit and push after each part.

## D1 - Database foundation (Flyway V3 in `common/` where portable)
Tables: `site`, `app_user`, `app_role`, `app_permission`, `user_role`, `role_permission`, `audit_log`.
- `app_user`: username (unique), display_name, email, password_hash (nullable until first set),
  must_change_password, enabled, failed_attempts, locked_until, last_login_at + standard audit/version columns.
- Seed: one site `DXB-TEST` ("DIMOS Test Site"); roles `SUPER_ADMIN`, `ADMIN`, `OPERATOR`, `VIEWER`;
  users `admin` and `rasheed`, both with role `SUPER_ADMIN`, no password yet.

## D2 - Initial passwords (no secret ever in Git or logs)
- On startup, for each user with an empty password_hash, read an environment variable
  `WMS_INIT_PASSWORD_<USERNAME>` (e.g. WMS_INIT_PASSWORD_ADMIN, WMS_INIT_PASSWORD_RASHEED). If present: hash it,
  store it, set must_change_password = true, write an audit entry. If absent: log a clear warning naming the
  variable (never the value).
- Explain to the owner how to set these two variables on the server with `Read-Host -AsSecureString` and
  `[Environment]::SetEnvironmentVariable(..., "Machine")`, then remove them after first login.

## D3 - Authentication and security
- Spring Security with database users, DelegatingPasswordEncoder, lockout after 5 failed attempts for 15 minutes.
- Session-based login for the browser (`POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`,
  `POST /api/auth/change-password`). CSRF enabled (cookie-based token for the SPA). Forced password change when
  must_change_password is true.
- `/actuator/health` stays public; everything else requires login. Roles available to the frontend via `/api/auth/me`.
- Audit: login success, login failure, lockout, logout, password change.
- Correlation ID per request in logs; global JSON error handler (no stack traces to the browser).

## D4 - Frontend shell (React + TypeScript + MUI)
- MUI theme from the brand rules in CLAUDE.md (Metropolis font self-hosted, Schwarzgrau base, Blutorange accent).
- Login page: standard logo, username + password, clear error messages, "DIMOS WMS" product name, version in footer.
- Change-password page (forced on first login).
- App shell: white top bar with the standard logo (Schwarzgrau text, Blutorange accent only), user menu (name, role, logout), collapsible side menu with
  placeholder entries: Dashboard, Inbound, Inventory, Outbound, Equipment (WCS), Reports, Administration.
- Dashboard: placeholder KPI cards (Open transport orders, Stock locations used, Active alarms, Equipment online)
  showing "--" until real data exists.
- All text via i18next (`en` only for now). React Router for pages, TanStack Query for API calls.
- Production build copied into the backend's static resources during the Maven build (frontend-maven-plugin or a
  documented npm step), so `java -jar` serves the UI.

## D5 - HTTPS and running as a service
- Generate a self-signed test certificate (PKCS12, SAN = DNS WMS-TEST-SRV and IP 192.168.1.60) into
  D:\WMS\certs\ with keytool; keystore password from env var WMS_KEYSTORE_PASSWORD. Not committed.
- Profile `test` (server-only config, gitignored if it has paths/secrets): port 8443, SSL on.
- Windows Firewall: inbound TCP 8443, Private/Domain profiles, RemoteAddress LocalSubnet only. Ask before applying.
- Package as a Windows service with WinSW (download the release exe into D:\WMS\tools, config in `deploy/`),
  service name `dimos-wms`, logs to D:\WMS\logs, restart on failure. Ask before installing the service.

## D6 - Acceptance test (record results in docs/test-evidence/STEP-D.md)
1. From another office PC (Chrome and Edge): page loads over HTTPS (accept the test-certificate warning).
2. `admin` and `rasheed` can log in with their initial passwords and are forced to change them.
3. Five wrong passwords lock the account for 15 minutes; audit_log shows the events.
4. Logout works; protected pages redirect to login.
5. Service survives a server reboot.
6. From the laptop, `Test-NetConnection 192.168.1.60 -Port 5432` FAILS (database not exposed).
