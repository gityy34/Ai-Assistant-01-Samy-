$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$GradleVersion = "8.11.1"
$GradleSha256 = "f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6"
$ToolsDir = Join-Path $ProjectRoot ".local-tools"
$ZipPath = Join-Path $ToolsDir "gradle-$GradleVersion-bin.zip"
$GradleHome = Join-Path $ToolsDir "gradle-$GradleVersion"
$GradleExe = Join-Path $GradleHome "bin\gradle.bat"
$VerificationDir = Join-Path $ProjectRoot "verification"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$LogPath = Join-Path $VerificationDir "demo1-build-$Timestamp.log"

New-Item -ItemType Directory -Force -Path $ToolsDir, $VerificationDir | Out-Null

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java 17 is required. Install Android Studio and use its bundled JDK 17."
}

if (-not $env:ANDROID_SDK_ROOT -and -not $env:ANDROID_HOME) {
    $DefaultSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $DefaultSdk) {
        $env:ANDROID_SDK_ROOT = $DefaultSdk
    } else {
        throw "Android SDK not found. Install Android Studio, SDK Platform 36, and Build Tools 36.0.0."
    }
}

$SdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
if (-not (Test-Path (Join-Path $SdkRoot "platforms\android-36"))) {
    throw "SDK Platform 36 is missing. Install it from Android Studio > SDK Manager."
}

if (-not (Test-Path $GradleExe)) {
    if (-not (Test-Path $ZipPath)) {
        Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $ZipPath
    }
    $ActualSha256 = (Get-FileHash $ZipPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($ActualSha256 -ne $GradleSha256) {
        Remove-Item $ZipPath -Force
        throw "Gradle download checksum mismatch. The downloaded file was removed."
    }
    Expand-Archive -Path $ZipPath -DestinationPath $ToolsDir -Force
}

Push-Location $ProjectRoot
try {
    & $GradleExe --no-daemon wrapper `
        --gradle-version $GradleVersion `
        --distribution-type bin `
        --gradle-distribution-sha256-sum $GradleSha256
    if ($LASTEXITCODE -ne 0) { throw "Gradle wrapper generation failed." }

    & (Join-Path $ProjectRoot "gradlew.bat") --no-daemon clean test assembleDebug 2>&1 |
        Tee-Object -FilePath $LogPath
    if ($LASTEXITCODE -ne 0) { throw "Demo 1 build failed. See $LogPath" }
} finally {
    Pop-Location
}

$ApkPath = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $ApkPath)) {
    throw "Gradle completed but the debug APK was not found. See $LogPath"
}

Write-Host "Demo 1 shell APK created: $ApkPath"
Write-Host "Build log: $LogPath"
