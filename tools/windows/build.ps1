param([string]$Dotnet = '')

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $Dotnet) {
    $bundledDotnet = Join-Path $projectRoot '.tools/dotnet/dotnet.exe'
    $Dotnet = if (Test-Path -LiteralPath $bundledDotnet) { $bundledDotnet } else { 'dotnet' }
}
$output = Join-Path $projectRoot 'artifacts/windows'
$portable = Join-Path $output 'portable'
$qaOutput = Join-Path $output 'qa'
Push-Location $projectRoot
try {
    & $Dotnet run --project clients/windows/Tests/MemBoom.CoreChecks.csproj -c Release
    if ($LASTEXITCODE -ne 0) { throw 'Core checks failed' }
    $previousQaDotnet = $env:MEMBOOM_DOTNET
    try {
        $env:MEMBOOM_DOTNET = (Get-Command $Dotnet -ErrorAction Stop).Source
        & node tools/windows/verify-sync.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Sync integration checks failed' }
        & node tools/windows/verify-auth.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Account authentication integration checks failed' }
    }
    finally { $env:MEMBOOM_DOTNET = $previousQaDotnet }
    & $Dotnet publish clients/windows/App/MemBoom.Windows.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:EnableCompressionInSingleFile=true -p:DebugType=None -p:DebugSymbols=false -o $portable
    if ($LASTEXITCODE -ne 0) { throw 'Windows publish failed' }
    $smokeProcess = Start-Process -FilePath (Join-Path $portable 'MemBoom.exe') -ArgumentList @('--ui-smoke', ('"' + $qaOutput + '"')) -WindowStyle Hidden -Wait -PassThru
    if ($smokeProcess.ExitCode -ne 0) { throw "UI smoke failed: $($smokeProcess.ExitCode)" }
    Copy-Item -LiteralPath clients/windows/PORTABLE-README.txt -Destination (Join-Path $portable 'README.txt') -Force
    Compress-Archive -LiteralPath (Join-Path $portable 'MemBoom.exe'),(Join-Path $portable 'README.txt') -DestinationPath (Join-Path $output 'MemBoom-Windows-x64.zip') -Force
    Write-Output "Windows checks passed; package: $(Join-Path $output 'MemBoom-Windows-x64.zip')"
}
finally { Pop-Location }
