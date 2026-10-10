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
    @{ Name = 'vits-piper-en_US-hfc_male-medium'; Hash = '76388f84acfca8ba5c0ed1636a26ada14c598abd52e76f110d4756fe326fc5f2' },
    @{ Name = 'vits-piper-en_US-hfc_female-medium-int8'; Hash = '7c467d19054ddf84056a4887a8be75f5f3b2a5d426637b526953a5e488fec5ce' },
    @{ Name = 'sherpa-onnx-pocket-tts-int8-2026-01-26'; Hash = '2f3b88823cbbb9bf0b2477ec8ae7b3fec417b3a87b6bb5f256dba66f2ad967cb' }
)
foreach ($pack in $packs) {
    $name = $pack.Name
    $archive = Join-Path $cache "$name.tar.bz2"
    Get-VerifiedFile "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$name.tar.bz2" $archive $pack.Hash
    & tar -xjf $archive -C $assets
    if ($LASTEXITCODE -ne 0) { throw "Model extraction failed: $name" }
    if ($name.StartsWith("vits-") -and -not (Test-Path (Join-Path $assets "$name/tokens.txt"))) { throw "Tokens missing: $name" }
}
Write-Host 'Verified base voice models and Android runtime prepared. These are not cloning models.'

$revision = 'ac47520e7afce6f675f9ba8a892dc5ed06a1c299'
$female = Join-Path $assets 'vits-piper-ur_PK-aegis_female-medium'
New-Item -ItemType Directory -Force $female | Out-Null
$model = Join-Path $female 'ur_PK-aegis_female-medium.onnx'
Get-VerifiedFile "https://huggingface.co/mahwizzzz/piper-voice-ur-aegis-female/resolve/$revision/ur_PK-aegis_female-medium.onnx" (Join-Path $cache 'aegis-original.onnx') '3ec306035cfd0bbc22342fee5890450708a16dd231809522731678aa583c961f'
Copy-Item (Join-Path $cache 'aegis-original.onnx') $model -Force
foreach ($file in @('ur_PK-aegis_female-medium.onnx.json', 'LICENSE', 'MODEL_CARD')) {
    Invoke-WebRequest "https://huggingface.co/mahwizzzz/piper-voice-ur-aegis-female/resolve/$revision/$file" -OutFile (Join-Path $female $file)
}
Copy-Item (Join-Path $assets 'vits-piper-ur_PK-fasih-medium/espeak-ng-data') (Join-Path $female 'espeak-ng-data') -Recurse -Force
python -m pip install onnx==1.17.0
if ($LASTEXITCODE -ne 0) { throw 'Unable to prepare model converter' }
python (Join-Path $PSScriptRoot 'prepare-aegis.py') $female
if ($LASTEXITCODE -ne 0) { throw 'Unable to prepare Aegis voice' }
