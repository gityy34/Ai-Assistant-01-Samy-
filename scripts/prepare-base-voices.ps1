$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path -Parent $PSScriptRoot
$cache = Join-Path $root '.local-tools/model-downloads'
New-Item -ItemType Directory -Force $cache | Out-Null
function Get-VerifiedFile($url, $path, $hash) {
    if (-not (Test-Path $path)) { Invoke-WebRequest $url -OutFile $path }
    if ((Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) {
        Remove-Item $path -Force
        throw "Checksum mismatch: $path"
    }
}
$libs = Join-Path $root 'app/libs'
New-Item -ItemType Directory -Force $libs | Out-Null
Get-VerifiedFile 'https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar' (Join-Path $libs 'sherpa-onnx-1.13.8.aar') '633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96'
$assets = Join-Path $root 'app/src/main/assets/models'
New-Item -ItemType Directory -Force $assets | Out-Null
$packs = @(
    @{ Name = 'vits-piper-ur_PK-fasih-medium'; Hash = 'de64e03109f3c896b7cf910db6d9ffd2703e3d665580d9a19bedbf3e0760d0e4' },
    @{ Name = 'vits-piper-en_US-lessac-medium'; Hash = '9e3febfacf0abf4270172d2958bcec246032b7e88efc2720840cc80c93de334e' }
)
foreach ($pack in $packs) {
    $name = $pack.Name
    $archive = Join-Path $cache "$name.tar.bz2"
    Get-VerifiedFile "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$name.tar.bz2" $archive $pack.Hash
    & tar -xjf $archive -C $assets
    if ($LASTEXITCODE -ne 0) { throw "Model extraction failed: $name" }
    if (-not (Test-Path (Join-Path $assets "$name/tokens.txt"))) { throw "Tokens missing: $name" }
}
Write-Host 'Verified base voice models and Android runtime prepared. These are not cloning models.'
