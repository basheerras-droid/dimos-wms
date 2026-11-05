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
