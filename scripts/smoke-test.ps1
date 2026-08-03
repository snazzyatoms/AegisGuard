param(
    [Parameter(Mandatory = $true)]
    [string]$FixturesRoot,

    [string]$Artifact = "$PSScriptRoot\..\aegisguard-modern\target\AegisGuard-1.3.0.jar",

    [string]$Java = "java",

    [int]$StartupTimeoutSeconds = 100
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path -LiteralPath $FixturesRoot).Path
$artifactPath = (Resolve-Path -LiteralPath $Artifact).Path
$servers = Get-ChildItem -LiteralPath $root -Directory
$failures = 0

foreach ($server in $servers) {
    $serverJar = Get-ChildItem -LiteralPath $server.FullName -File |
        Where-Object { $_.Name -in @("folia.jar", "paper.jar", "purpur.jar", "spigot.jar", "server.jar") } |
        Select-Object -First 1
    if ($null -eq $serverJar) {
        Write-Host "SKIP $($server.Name): no recognized server JAR"
        continue
    }

    $eula = Join-Path $server.FullName "eula.txt"
    if (-not (Test-Path -LiteralPath $eula) -or (Get-Content -LiteralPath $eula -Raw) -notmatch "eula=true") {
        Write-Host "SKIP $($server.Name): eula.txt is not accepted"
        continue
    }

    $plugins = Join-Path $server.FullName "plugins"
    New-Item -ItemType Directory -Path $plugins -Force | Out-Null
    Copy-Item -LiteralPath $artifactPath -Destination (Join-Path $plugins "AegisGuard-1.3.0.jar") -Force

    $log = Join-Path $server.FullName "logs\latest.log"
    $startedAt = Get-Date
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Java
    $info.Arguments = "-Xms512M -Xmx1G -jar $($serverJar.Name) --nogui"
    $info.WorkingDirectory = $server.FullName
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    $null = $process.Start()
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    $started = $false

    while ([DateTime]::UtcNow -lt $deadline -and -not $process.HasExited) {
        Start-Sleep -Milliseconds 500
        if (Test-Path -LiteralPath $log) {
            $item = Get-Item -LiteralPath $log
            if ($item.LastWriteTime -ge $startedAt.AddSeconds(-1)) {
                $content = Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue
                if ($content -match "Done \(") { $started = $true; break }
            }
        }
    }

    if ($started) {
        $process.StandardInput.WriteLine("agadmin reload")
        $process.StandardInput.Flush()
        Start-Sleep -Seconds 7
    }
    if (-not $process.HasExited) {
        $process.StandardInput.WriteLine("stop")
        $process.StandardInput.Flush()
        if (-not $process.WaitForExit(30000)) {
            $process.Kill($true)
            $process.WaitForExit()
        }
    }

    $output = $stdout.GetAwaiter().GetResult()
    $null = $stderr.GetAwaiter().GetResult()
    $content = if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Raw } else { "" }
    $combined = $content + "`n" + $output
    $errors = @($content -split "`r?`n" | Where-Object {
        $_ -match "\[ERROR\]|Exception|Caused by|disabled itself|not enabled|IllegalStateException|UnsupportedOperationException"
    })
    $pass = $started -and
        $combined -match "AegisGuard enabled" -and
        $combined -match "AegisGuard reload complete" -and
        $combined -match "AegisGuard disabled" -and
        $process.ExitCode -eq 0 -and
        $errors.Count -eq 0

    Write-Host "$($server.Name): PASS=$pass EXIT=$($process.ExitCode) ERRORS=$($errors.Count)"
    if (-not $pass) { $failures++ }
}

if ($failures -gt 0) { throw "$failures smoke-test fixture(s) failed." }
