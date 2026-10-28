param([switch]$CheckOnly)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$logDirectory = Join-Path $projectRoot 'artifacts\server'
try {
    $port = 8788
    $configPath = Join-Path $projectRoot 'server\.env'
    if (Test-Path -LiteralPath $configPath) {
        foreach ($line in Get-Content -LiteralPath $configPath) {
            if ($line -match '^\s*PORT\s*=\s*["'']?(\d+)') { $port = [int]$Matches[1] }
        }
    }
    if ($port -lt 1 -or $port -gt 65535) { throw 'Invalid server port.' }
    $siteUrl = "http://127.0.0.1:$port/"
    function Test-MemBoom {
        try {
            $health = Invoke-RestMethod -Uri ($siteUrl + 'health') -TimeoutSec 2
            $page = Invoke-WebRequest -Uri $siteUrl -UseBasicParsing -TimeoutSec 2
            return $health.status -eq 'ok' -and $page.Content -match 'MemBoom'
        } catch { return $false }
    }
    if (!(Test-MemBoom)) {
        if ($CheckOnly) { throw 'MemBoom is not running.' }
        $nodePath = (Get-Command node.exe -ErrorAction SilentlyContinue).Source
        if (!$nodePath) { $nodePath = Join-Path $env:ProgramFiles 'nodejs\node.exe' }
        if (!(Test-Path -LiteralPath $nodePath)) { throw 'Node.js is not installed.' }
        New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
        $entryPoint = Join-Path $PSScriptRoot 'start.mjs'
        Start-Process -FilePath $nodePath -ArgumentList ('"' + $entryPoint + '"') -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDirectory 'autostart.log') -RedirectStandardError (Join-Path $logDirectory 'autostart-error.log') | Out-Null
        $ready = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            if (Test-MemBoom) { $ready = $true; break }
            Start-Sleep -Seconds 1
        }
        if (!$ready) { throw 'MemBoom did not start. See autostart-error.log.' }
    }
    if ($CheckOnly) { Write-Output "MemBoom is ready: $siteUrl" }
    else { Start-Process $siteUrl }
} catch {
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    Add-Content -LiteralPath (Join-Path $logDirectory 'autostart-error.log') -Value ("$(Get-Date -Format s) " + $_.Exception.Message)
    Write-Error $_
    exit 1
}
