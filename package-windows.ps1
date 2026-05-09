param(
    [string]$AppName = "WASPBackend",
    [switch]$SkipBackendBuild,
    [switch]$SkipNativeBuild,
    [switch]$SkipSenderBuild,
    [switch]$SkipInstaller
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Write-Step {
    param([string]$Message)
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Resolve-IsccPath {
    $cmd = Get-Command ISCC -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    $candidates = @(
        "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
        "C:\Program Files\Inno Setup 6\ISCC.exe"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    return $null
}

$repoRoot = Split-Path -Parent $PSCommandPath
$backendDir = Join-Path $repoRoot "wasp-backend"
$nativeDir = Join-Path $repoRoot "Native"
$nativeFrontendDir = Join-Path $nativeDir "src"
$nativeSenderScript = Join-Path $nativeDir "send_client.py"
$nativeSenderExe = Join-Path $nativeDir "build\send_client.exe"
$nativeTrayExe = Join-Path $nativeDir "build\WASPTray.exe"
$distDir = Join-Path $repoRoot "dist"
$stagingDir = Join-Path $distDir "staging"
$appImageDir = Join-Path $stagingDir "app-image"
$installerDir = Join-Path $distDir "installer"
$issPath = Join-Path $repoRoot "WASP.iss"
$backendStaticDir = Join-Path $backendDir "src\main\resources\static"

if (-not (Test-Path $backendDir)) { throw "Missing backend directory: $backendDir" }
if (-not (Test-Path $nativeDir)) { throw "Missing native directory: $nativeDir" }
if (-not (Test-Path $nativeFrontendDir)) { throw "Missing native frontend directory: $nativeFrontendDir" }
if (-not (Test-Path $nativeSenderScript)) { throw "Missing metrics sender script: $nativeSenderScript" }
if (-not (Test-Path $issPath)) { throw "Missing installer definition: $issPath" }

Write-Step "Preparing output directories"
if (Test-Path $distDir) {
    Remove-Item -Path $distDir -Recurse -Force
}
New-Item -ItemType Directory -Path $appImageDir -Force | Out-Null
New-Item -ItemType Directory -Path $installerDir -Force | Out-Null

Write-Step "Reading backend version"
[xml]$pom = Get-Content -Path (Join-Path $backendDir "pom.xml")
$version = $pom.project.version
if ([string]::IsNullOrWhiteSpace($version)) {
    throw "Could not parse project version from pom.xml"
}

# jpackage requires numeric dot-separated versions (e.g., 1.2.3).
$packageVersion = ($version -replace "[^0-9.].*$", "").Trim(".")
if ([string]::IsNullOrWhiteSpace($packageVersion)) {
    $packageVersion = "1.0.0"
}

Write-Step "Syncing embedded frontend assets"
if (-not (Test-Path $backendStaticDir)) {
    New-Item -ItemType Directory -Path $backendStaticDir -Force | Out-Null
}

$frontendAssets = @("index.html", "main.js", "styles.css")
foreach ($asset in $frontendAssets) {
    $source = Join-Path $nativeFrontendDir $asset
    if (-not (Test-Path $source)) {
        throw "Missing frontend asset: $source"
    }
    Copy-Item -Path $source -Destination (Join-Path $backendStaticDir $asset) -Force
}

# Keep static subdirectories (favicon/logo assets, etc.) in sync for backend-served UI.
$frontendAssetDirs = @("assets")
foreach ($dirName in $frontendAssetDirs) {
    $sourceDir = Join-Path $nativeFrontendDir $dirName
    if (-not (Test-Path $sourceDir)) {
        continue
    }
    $destDir = Join-Path $backendStaticDir $dirName
    if (Test-Path $destDir) {
        Remove-Item -Path $destDir -Recurse -Force
    }
    Copy-Item -Path $sourceDir -Destination $backendStaticDir -Recurse -Force
}

if (-not $SkipBackendBuild) {
    Write-Step "Building backend JAR"
    Push-Location $backendDir
    try {
        & ".\mvnw.cmd" clean package -DskipTests
        if ($LASTEXITCODE -ne 0) {
            throw "Backend build failed."
        }
    }
    finally {
        Pop-Location
    }
}

$jar = Get-ChildItem -Path (Join-Path $backendDir "target") -Filter "wasp-backend-*.jar" |
    Where-Object { $_.Name -notmatch "(?i)original|plain" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    throw "Could not find built backend jar in wasp-backend\target"
}

if (-not $SkipNativeBuild) {
    Write-Step "Building native metrics executable"
    Push-Location $nativeDir
    try {
        & ".\build.ps1" -NoPrompt
        if ($LASTEXITCODE -ne 0) {
            throw "Native build failed."
        }
    }
    finally {
        Pop-Location
    }
}

$nativeExe = Join-Path $nativeDir "build\system_metrics.exe"
if (-not (Test-Path $nativeExe)) {
    throw "Could not find native executable: $nativeExe"
}

if (-not (Test-Path $nativeTrayExe)) {
    throw "Could not find tray executable: $nativeTrayExe"
}

if (-not $SkipSenderBuild) {
    Write-Step "Building metrics sender executable (PyInstaller)"
    $py = Get-Command py -ErrorAction SilentlyContinue
    if (-not $py) {
        throw "Python launcher 'py' not found. Install Python on build machine to package send_client.exe."
    }

    Push-Location $nativeDir
    try {
        & $py.Source -3 -m pip show pyinstaller *> $null
        if ($LASTEXITCODE -ne 0) {
            & $py.Source -3 -m pip install pyinstaller
            if ($LASTEXITCODE -ne 0) {
                throw "Failed installing pyinstaller."
            }
        }

        & $py.Source -3 -m pip show websockets *> $null
        if ($LASTEXITCODE -ne 0) {
            & $py.Source -3 -m pip install websockets
            if ($LASTEXITCODE -ne 0) {
                throw "Failed installing websockets."
            }
        }

        & $py.Source -3 -m PyInstaller `
            --onefile `
            --noconsole `
            --noconfirm `
            --clean `
            --name send_client `
            --distpath ".\build" `
            --workpath ".\build\pyi-work" `
            --specpath ".\build\pyi-spec" `
            ".\send_client.py"
        if ($LASTEXITCODE -ne 0) {
            throw "Failed building send_client.exe with PyInstaller."
        }
    }
    finally {
        Pop-Location
    }
}

if (-not (Test-Path $nativeSenderExe)) {
    throw "Could not find sender executable: $nativeSenderExe"
}

$jpackage = Get-Command jpackage -ErrorAction SilentlyContinue
if (-not $jpackage) {
    throw "jpackage was not found. Install JDK 17+ and ensure jpackage is on PATH."
}

Write-Step "Creating Windows app image via jpackage"
& $jpackage.Source `
    --type app-image `
    --name $AppName `
    --dest $appImageDir `
    --input (Join-Path $backendDir "target") `
    --main-jar $jar.Name `
    --app-version $packageVersion `
    --vendor "WASP"

if ($LASTEXITCODE -ne 0) {
    throw "jpackage app-image creation failed."
}

$packagedAppRoot = Join-Path $appImageDir $AppName
if (-not (Test-Path $packagedAppRoot)) {
    throw "Expected app image was not created: $packagedAppRoot"
}

Write-Step "Copying native executable into app image"
Copy-Item -Path $nativeExe -Destination (Join-Path $packagedAppRoot "system_metrics.exe") -Force
Copy-Item -Path $nativeSenderExe -Destination (Join-Path $packagedAppRoot "send_client.exe") -Force
Copy-Item -Path $nativeTrayExe -Destination (Join-Path $packagedAppRoot "WASPTray.exe") -Force

$launcherPath = Join-Path $packagedAppRoot "LaunchWASP.bat"
@"
@echo off
set "APP_DIR=%~dp0"
set "DATA_DIR=%LOCALAPPDATA%\WASP"
set "LOG_DIR=%DATA_DIR%\logs"
if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
cd /d "%DATA_DIR%"

:loop
"%APP_DIR%system_metrics.exe" >> "%LOG_DIR%\system_metrics.log" 2>&1
timeout /t 2 /nobreak >nul
goto loop
"@ | Set-Content -Path (Join-Path $packagedAppRoot "RunMetricsLoop.bat") -Encoding ASCII

@"
@echo off
set "APP_DIR=%~dp0"
set "DATA_DIR=%LOCALAPPDATA%\WASP"
if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"
set "WASP_METRICS_JSON=%DATA_DIR%\system_metrics_output.json"

start "" "%APP_DIR%WASPBackend.exe"
start "" /min cmd /c ""%APP_DIR%RunMetricsLoop.bat""
start "" /min "%APP_DIR%send_client.exe"

timeout /t 3 /nobreak >nul
start "" "http://localhost:8080/"
"@ | Set-Content -Path $launcherPath -Encoding ASCII

$senderRunnerPath = Join-Path $packagedAppRoot "RunSender.bat"
@"
@echo off
set "APP_DIR=%~dp0"
set "DATA_DIR=%LOCALAPPDATA%\WASP"
set "LOG_DIR=%DATA_DIR%\logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
set "WASP_METRICS_JSON=%DATA_DIR%\system_metrics_output.json"
cd /d "%APP_DIR%"
"%APP_DIR%send_client.exe" >> "%LOG_DIR%\send_client.log" 2>&1
"@ | Set-Content -Path $senderRunnerPath -Encoding ASCII

$launcherVbsPath = Join-Path $packagedAppRoot "LaunchWASP.vbs"
@"
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

appDir = fso.GetParentFolderName(WScript.ScriptFullName)
dataDir = shell.ExpandEnvironmentStrings("%LOCALAPPDATA%") & "\WASP"

If Not fso.FolderExists(dataDir) Then
  fso.CreateFolder(dataDir)
End If

' Clean up previous hidden instances so relaunch is deterministic.
shell.Run "cmd /c taskkill /IM send_client.exe /F >nul 2>nul", 0, True
shell.Run "cmd /c taskkill /IM system_metrics.exe /F >nul 2>nul", 0, True
shell.Run "cmd /c taskkill /IM WASPBackend.exe /F >nul 2>nul", 0, True

shell.Run Chr(34) & appDir & "\WASPBackend.exe" & Chr(34), 0, False
shell.Run "cmd /c " & Chr(34) & Chr(34) & appDir & "\RunMetricsLoop.bat" & Chr(34) & Chr(34), 0, False
shell.Run "cmd /c " & Chr(34) & Chr(34) & appDir & "\RunSender.bat" & Chr(34) & Chr(34), 0, False

WScript.Sleep 3000
shell.Run "http://localhost:8080/", 0, False
"@ | Set-Content -Path $launcherVbsPath -Encoding ASCII

if (-not $SkipInstaller) {
    $iscc = Resolve-IsccPath
    if (-not $iscc) {
        throw "ISCC.exe (Inno Setup) not found. Install Inno Setup 6 or add ISCC to PATH."
    }

    Write-Step "Building installer with Inno Setup"
    & $iscc `
        "/DAppVersion=$version" `
        "/DSourceDir=$packagedAppRoot" `
        "/DOutputDir=$installerDir" `
        $issPath

    if ($LASTEXITCODE -ne 0) {
        throw "Installer build failed."
    }
}

Write-Host "`nPackaging complete." -ForegroundColor Green
Write-Host "App image: $packagedAppRoot"
if (-not $SkipInstaller) {
    $installer = Get-ChildItem -Path $installerDir -Filter "wasp-*-setup.exe" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($installer) {
        Write-Host "Installer: $($installer.FullName)"
    }
}
