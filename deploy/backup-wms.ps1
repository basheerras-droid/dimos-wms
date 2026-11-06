<#
.SYNOPSIS
    Daily backup of the DIMOS WMS database (pg_dump, custom format).

.DESCRIPTION
    - Dumps database wms_dev with pg_dump -Fc into <BackupRoot>\yyyy-MM-dd\.
    - The password comes ONLY from a pgpass file (PGPASSFILE). It is never passed on the
      command line, never read from an environment variable and never written to the log.
    - Checks the dump with pg_restore --list (the file must be readable).
    - Deletes dated backup folders (and logs) older than -KeepDays.
    - Writes a log to <BackupRoot>\logs\backup-wms-yyyy-MM-dd.log.
    - Exit code 0 = success, 1 = something failed (Task Scheduler shows "Last Run Result" 0x1).
    No network / NAS copy: that is done by hand.

.EXAMPLE
    .\backup-wms.ps1
    .\backup-wms.ps1 -BackupRoot E:\WMS-Backup -KeepDays 14 -PgPassFile D:\WMS\secrets\pgpass.conf
#>
[CmdletBinding()]
param(
    # Default: E:\WMS-Backup on WMS-TEST-SRV, D:\Dimos-WMS\Backups on any other machine (laptop).
    [string]$BackupRoot,
    [ValidateRange(1, 3650)]
    [int]$KeepDays = 14,
    # Default: the standard libpq location for the current Windows user.
    [string]$PgPassFile = (Join-Path $env:APPDATA 'postgresql\pgpass.conf'),
    [string]$Database = 'wms_dev',
    [string]$DbUser = 'wms_owner',
    [string]$DbHost = 'localhost',
    [int]$DbPort = 5432,
    # Folder with pg_dump.exe / pg_restore.exe. Default: PostgreSQL 18 install, else PATH.
    [string]$PgBin
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

if (-not $BackupRoot) {
    if ($env:COMPUTERNAME -eq 'WMS-TEST-SRV') { $BackupRoot = 'E:\WMS-Backup' }
    else { $BackupRoot = 'D:\Dimos-WMS\Backups' }
}

$startTime = Get-Date
$today     = $startTime.ToString('yyyy-MM-dd')
$logDir    = Join-Path $BackupRoot 'logs'
$logFile   = Join-Path $logDir ("backup-wms-$today.log")
$script:logReady = $false

function Write-Log {
    param([string]$Message, [string]$Level = 'INFO')
    $line = '{0} [{1}] {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Level, $Message
    Write-Host $line
    if ($script:logReady) { Add-Content -Path $logFile -Value $line -Encoding ASCII }
}

function Find-PgTool {
    param([string]$Name)
    if ($PgBin) {
        $p = Join-Path $PgBin "$Name.exe"
        if (Test-Path -LiteralPath $p) { return $p }
        throw "$Name.exe not found in -PgBin '$PgBin'."
    }
    $default = "C:\Program Files\PostgreSQL\18\bin\$Name.exe"
    if (Test-Path -LiteralPath $default) { return $default }
    $cmd = Get-Command "$Name.exe" -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "$Name.exe not found (looked in C:\Program Files\PostgreSQL\18\bin and PATH). Use -PgBin."
}

# Runs a native tool, writes its stderr to the log, returns the exit code.
function Invoke-Tool {
    param([string]$Exe, [string[]]$Arguments)
    $errFile = [System.IO.Path]::GetTempFileName()
    $outFile = [System.IO.Path]::GetTempFileName()
    try {
        # Quote every argument so paths with spaces survive.
        $argLine = ($Arguments | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }) -join ' '
        $proc = Start-Process -FilePath $Exe -ArgumentList $argLine -NoNewWindow -Wait -PassThru `
            -RedirectStandardError $errFile -RedirectStandardOutput $outFile
        foreach ($l in (Get-Content -LiteralPath $errFile)) { if ($l) { Write-Log "  $l" 'TOOL' } }
        return @{ ExitCode = $proc.ExitCode; StdOut = (Get-Content -LiteralPath $outFile) }
    }
    finally {
        Remove-Item -LiteralPath $errFile, $outFile -Force -ErrorAction SilentlyContinue
    }
}

$exitCode = 0
try {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $script:logReady = $true
    Write-Log "=== WMS backup started on $env:COMPUTERNAME as $env:USERNAME ==="
    Write-Log "BackupRoot=$BackupRoot KeepDays=$KeepDays Database=$Database User=$DbUser Host=${DbHost}:$DbPort"

    # --- 1. Pre-checks --------------------------------------------------------------
    if (-not (Test-Path -LiteralPath $PgPassFile -PathType Leaf)) {
        throw "pgpass file not found: $PgPassFile (see deploy/README.md, section Backup)."
    }
    if ($env:PGPASSWORD) {
        throw "PGPASSWORD is set in the environment. Remove it; this script uses only the pgpass file."
    }
    $env:PGPASSFILE = $PgPassFile
    Write-Log "Using pgpass file $PgPassFile"

    $pgDump    = Find-PgTool 'pg_dump'
    $pgRestore = Find-PgTool 'pg_restore'
    Write-Log "pg_dump: $pgDump"

    # --- 2. Dump --------------------------------------------------------------------
    $dayDir = Join-Path $BackupRoot $today
    New-Item -ItemType Directory -Force -Path $dayDir | Out-Null
    $dumpFile = Join-Path $dayDir ('{0}_{1}.dump' -f $Database, $startTime.ToString('yyyy-MM-dd_HHmmss'))
    Write-Log "Dumping to $dumpFile"

    # -w = never prompt for a password (fail instead), -Fc = custom format (compressed, for pg_restore).
    $r = Invoke-Tool $pgDump @('-h', $DbHost, '-p', "$DbPort", '-U', $DbUser, '-d', $Database,
                               '-w', '-Fc', '-f', $dumpFile)
    if ($r.ExitCode -ne 0) {
        Remove-Item -LiteralPath $dumpFile -Force -ErrorAction SilentlyContinue
        throw "pg_dump failed with exit code $($r.ExitCode)."
    }

    # --- 3. Verify ------------------------------------------------------------------
    $size = (Get-Item -LiteralPath $dumpFile).Length
    if ($size -le 0) { throw "Dump file is empty: $dumpFile" }
    $r = Invoke-Tool $pgRestore @('--list', $dumpFile)
    if ($r.ExitCode -ne 0) { throw "pg_restore --list could not read the dump (exit code $($r.ExitCode))." }
    $entries = @($r.StdOut | Where-Object { $_ -and -not $_.StartsWith(';') }).Count
    Write-Log ('Dump OK: {0:N0} bytes, {1} entries in table of contents' -f $size, $entries)

    # --- 4. Retention ---------------------------------------------------------------
    # Only folders named exactly yyyy-MM-dd are touched; anything else in BackupRoot is left alone.
    $cutoff = $startTime.Date.AddDays(-$KeepDays)
    foreach ($d in Get-ChildItem -LiteralPath $BackupRoot -Directory) {
        $folderDate = [datetime]::MinValue
        if ($d.Name -match '^\d{4}-\d{2}-\d{2}$' -and
            [datetime]::TryParseExact($d.Name, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture,
                [Globalization.DateTimeStyles]::None, [ref]$folderDate) -and
            $folderDate -lt $cutoff) {
            Remove-Item -LiteralPath $d.FullName -Recurse -Force
            Write-Log "Deleted old backup folder $($d.FullName)"
        }
    }
    foreach ($f in Get-ChildItem -LiteralPath $logDir -File -Filter 'backup-wms-*.log') {
        if ($f.LastWriteTime -lt $cutoff) {
            Remove-Item -LiteralPath $f.FullName -Force
            Write-Log "Deleted old log $($f.Name)"
        }
    }

    Write-Log ('=== WMS backup finished OK in {0:N1} s ===' -f ((Get-Date) - $startTime).TotalSeconds)
}
catch {
    $exitCode = 1
    Write-Log "BACKUP FAILED: $($_.Exception.Message)" 'ERROR'
    if (-not $script:logReady) {
        # Could not even create the log folder - leave a trace in the Windows event log if possible.
        try { Write-EventLog -LogName Application -Source 'Windows PowerShell' -EventId 1 -EntryType Error `
                -Message "WMS backup failed: $($_.Exception.Message)" } catch { }
    }
}
finally {
    Remove-Item Env:\PGPASSFILE -ErrorAction SilentlyContinue
}
exit $exitCode
