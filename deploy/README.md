# deploy - running the WMS on WMS-TEST-SRV

All commands run in an **Administrator PowerShell on WMS-TEST-SRV**.

## 1. Keystore password (once)
```powershell
$s = Read-Host "Keystore password" -AsSecureString
[Environment]::SetEnvironmentVariable("WMS_KEYSTORE_PASSWORD", [System.Net.NetworkCredential]::new("", $s).Password, "Machine")
```

## 2. Test certificate (once, valid 2 years; not committed)
```powershell
$keytool = "C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot\bin\keytool.exe"
& $keytool -genkeypair -alias wms-test -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 730 `
  -dname "CN=WMS-TEST-SRV, O=DIMOS FZCO, C=AE" `
  -ext "SAN=dns:WMS-TEST-SRV,ip:192.168.1.60,dns:localhost" `
  -keystore D:\WMS\certs\wms-test.p12 -storetype PKCS12 -storepass $env:WMS_KEYSTORE_PASSWORD
```
Browsers show a warning for a self-signed certificate; accept it for the test. Export `wms-test.cer`
(`keytool -exportcert ...`) and import it into "Trusted Root" on office PCs to remove the warning.

## 3. Build and publish the JAR
```powershell
cd D:\WMS\src\dimos-wms\backend
.\mvnw.cmd -Pfrontend clean package
Copy-Item target\wms-backend-0.0.1-SNAPSHOT.jar D:\WMS\app\wms-backend.jar -Force
```

## 4. Firewall (inbound TCP 8443, local subnet only)
```powershell
New-NetFirewallRule -DisplayName "DIMOS WMS HTTPS 8443" -Direction Inbound -Protocol TCP -LocalPort 8443 `
  -Action Allow -Profile Private,Domain -RemoteAddress LocalSubnet
```
Note: the port 5432 (PostgreSQL) is deliberately **not** opened.

## 5. Windows service (WinSW)
Download the WinSW release exe (`WinSW-x64.exe`) from https://github.com/winsw/winsw/releases into `D:\WMS\tools`, then:
```powershell
Copy-Item D:\WMS\tools\WinSW-x64.exe D:\WMS\tools\dimos-wms.exe
Copy-Item D:\WMS\src\dimos-wms\deploy\dimos-wms.xml D:\WMS\tools\dimos-wms.xml
D:\WMS\tools\dimos-wms.exe install
Start-Service dimos-wms
```
Logs: `D:\WMS\logs\dimos-wms.out.log` / `.err.log` (WinSW) .
Update later: `Stop-Service dimos-wms`, copy the new JAR, `Start-Service dimos-wms`.

WinSW used on WMS-TEST-SRV: v2.12.0 (WinSW-x64.exe, SHA256 05B82D46AD331CC16BDC00DE5C6332C1EF818DF8CEEFCD49C726553209B3A0DA; the release is not code-signed).

## 6. Database backup (`deploy/backup-wms.ps1`)

The script runs `pg_dump -Fc` of `wms_dev` as `wms_owner`, saves the file to `<BackupRoot>\yyyy-MM-dd\`, checks that it
can be read (`pg_restore --list`), deletes dated folders older than `-KeepDays` (default 14), writes a log to
`<BackupRoot>\logs\backup-wms-yyyy-MM-dd.log` and exits with code 1 if anything fails.
The password is read **only** from a pgpass file - never from the command line. There is no NAS copy; copy the
dated folders to the office NAS by hand.

| Parameter     | Default                                                                 |
|---------------|-------------------------------------------------------------------------|
| `-BackupRoot` | `E:\WMS-Backup` on WMS-TEST-SRV, `D:\Dimos-WMS\Backups` on any other PC |
| `-KeepDays`   | `14`                                                                    |
| `-PgPassFile` | `%APPDATA%\postgresql\pgpass.conf` of the user running the script       |
| `-PgBin`      | `C:\Program Files\PostgreSQL\18\bin`, else `pg_dump.exe` on PATH        |

### 6a. Schedule it on WMS-TEST-SRV (daily 02:00) - Administrator PowerShell on **WMS-TEST-SRV**
The task runs as `SYSTEM` (no stored Windows password). The pgpass file lives in a folder that only SYSTEM and
Administrators can read.
```powershell
# 1. Secrets folder, readable only by SYSTEM and Administrators
New-Item -ItemType Directory -Force D:\WMS\secrets | Out-Null
icacls D:\WMS\secrets /inheritance:r /grant:r "SYSTEM:(OI)(CI)F" "Administrators:(OI)(CI)F"

# 2. pgpass file from the existing machine variable (the password is not shown on screen)
$pw = [Environment]::GetEnvironmentVariable("WMS_DB_OWNER_PASSWORD", "Machine")
Set-Content D:\WMS\secrets\pgpass.conf "localhost:5432:wms_dev:wms_owner:$pw" -Encoding ASCII
Remove-Variable pw

# 3. Copy the script next to the other tools (update this copy after a git pull that changes it)
Copy-Item D:\WMS\src\dimos-wms\deploy\backup-wms.ps1 D:\WMS\tools\backup-wms.ps1 -Force

# 4. Scheduled task "DIMOS WMS Backup", daily at 02:00, as SYSTEM
$action  = New-ScheduledTaskAction -Execute "powershell.exe" `
  -Argument '-NoProfile -ExecutionPolicy Bypass -File "D:\WMS\tools\backup-wms.ps1" -BackupRoot "E:\WMS-Backup" -KeepDays 14 -PgPassFile "D:\WMS\secrets\pgpass.conf"'
$trigger = New-ScheduledTaskTrigger -Daily -At 02:00
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Hours 2)
Register-ScheduledTask -TaskName "DIMOS WMS Backup" -Action $action -Trigger $trigger -Settings $settings `
  -User "SYSTEM" -RunLevel Highest -Description "pg_dump of wms_dev to E:\WMS-Backup (deploy/backup-wms.ps1)"

# 5. Test it now and check the result (0 = OK, 1 = failed -> read the log)
Start-ScheduledTask -TaskName "DIMOS WMS Backup"
Start-Sleep 30
(Get-ScheduledTaskInfo -TaskName "DIMOS WMS Backup").LastTaskResult
Get-Content "E:\WMS-Backup\logs\backup-wms-$(Get-Date -Format yyyy-MM-dd).log" -Tail 20
```
`-StartWhenAvailable` means a run missed because the server was off is done as soon as it is back on.
If the `wms_owner` password changes, rewrite `D:\WMS\secrets\pgpass.conf` (step 2).

### 6b. Run it by hand on the laptop - normal PowerShell on the **laptop**
Once, create the pgpass file for your Windows user (you type the password; it is not echoed):
```powershell
New-Item -ItemType Directory -Force "$env:APPDATA\postgresql" | Out-Null
$s = Read-Host "wms_owner password" -AsSecureString
$pw = [System.Net.NetworkCredential]::new("", $s).Password
Set-Content "$env:APPDATA\postgresql\pgpass.conf" "localhost:5432:wms_dev:wms_owner:$pw" -Encoding ASCII
Remove-Variable pw, s
icacls "$env:APPDATA\postgresql\pgpass.conf" /inheritance:r /grant:r "${env:USERNAME}:F"
```
Then, whenever you want a backup:
```powershell
cd D:\Dimos-WMS\GlitHub\dimos-wms
powershell -NoProfile -ExecutionPolicy Bypass -File deploy\backup-wms.ps1
# -> D:\Dimos-WMS\Backups\yyyy-MM-dd\wms_dev_yyyy-MM-dd_HHmmss.dump ; $LASTEXITCODE is 0 when OK
```

### 6c. Test restore into a scratch database `wms_restore_test`
Run on the machine that holds the dump (server: Administrator PowerShell on **WMS-TEST-SRV**; laptop: normal
PowerShell). It creates a separate database and never touches `wms_dev`. The `postgres` superuser password is asked
for interactively.
```powershell
$bin  = "C:\Program Files\PostgreSQL\18\bin"
$dump = Get-ChildItem E:\WMS-Backup -Recurse -Filter *.dump | Sort-Object LastWriteTime | Select-Object -Last 1   # laptop: D:\Dimos-WMS\Backups
$dump.FullName

# 1. Empty scratch database owned by wms_owner
& "$bin\createdb.exe" -h localhost -U postgres -O wms_owner wms_restore_test

# 1b. pgpass line for the scratch database (the existing line only covers wms_dev). Once per machine; skip if already added.
$pgpass = "D:\WMS\secrets\pgpass.conf"           # laptop: "$env:APPDATA\postgresql\pgpass.conf"
$s = Read-Host "wms_owner password" -AsSecureString
$pw = [System.Net.NetworkCredential]::new("", $s).Password
Add-Content $pgpass "localhost:5432:wms_restore_test:wms_owner:$pw" -Encoding ASCII
Remove-Variable pw, s

# 2. Restore (uses the pgpass file of wms_owner; stops at the first error)
$env:PGPASSFILE = $pgpass                        # laptop: this line may be omitted (default location is used)
& "$bin\pg_restore.exe" -h localhost -U wms_owner -d wms_restore_test --no-owner --exit-on-error -w $dump.FullName
"pg_restore exit code: $LASTEXITCODE"            # must be 0

# 3. Spot checks - compare with the same queries on wms_dev
& "$bin\psql.exe" -h localhost -U wms_owner -d wms_restore_test -w -c "SELECT version, description, success FROM wms.flyway_schema_history ORDER BY installed_rank;"
& "$bin\psql.exe" -h localhost -U wms_owner -d wms_restore_test -w -c "SELECT count(*) AS users FROM wms.app_user; SELECT count(*) AS audit_rows FROM wms.audit_log;"

# 4. Remove the scratch database again (only wms_restore_test - double-check the name)
Remove-Item Env:\PGPASSFILE -ErrorAction SilentlyContinue
& "$bin\dropdb.exe" -h localhost -U postgres wms_restore_test
```
Do a test restore after setting up the task, and then about once a month.
