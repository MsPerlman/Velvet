<#
.SYNOPSIS
    Produit la lib native et les bindings Kotlin desktop pour le module `core`.

.DESCRIPTION
    Android n'a besoin de rien : les bindings y viennent de l'AAR publié sur Maven Central.
    Seul le desktop demande une compilation, parce qu'aucun artefact JVM n'est publié.

    Le script est idempotent : relancé, il ne recompile que ce qui a changé.

    TROIS PIÈGES WINDOWS, tous rencontrés et tous silencieux (voir ../../FAULTS.md) :

      1. vcvars64 est indispensable — aws-lc-sys et zstd-sys compilent du C, et sans
         INCLUDE/LIB, cl.exe échoue sur un simple #include <stdio.h>.

      2. Le répertoire de build doit être un chemin COURT. cc-rs écrit ses fichiers sondes
         dans OUT_DIR ; au-delà de MAX_PATH (260), cl.exe n'ouvre plus rien, la détection de
         famille de compilateur échoue EN SILENCE, cc-rs envoie alors des options GCC à MSVC,
         et l'erreur finale ne parle que de zstd. Symptôme et cause sans rapport.

      3. android_cleaner doit être à false dans TOUS les uniffi.toml du dépôt, pas seulement
         celui de matrix-sdk-ffi : sinon le Kotlin généré importe android.os.Build.

    INVARIANT — la feature `sentry` doit être activée. Elle ne fait pas partie du jeu par
    défaut mais la build Android de l'AAR l'active. Sans elle, TracingConfiguration n'a pas
    le même nombre de champs des deux côtés, et le wrapper partagé de `core` ne compile plus
    que sur une cible. C'est la condition qui rend tout le montage possible.

.PARAMETER Commit
    Révision de matrix-rust-sdk à compiler. DOIT correspondre à celle de l'AAR Android,
    que `sdkGitSha()` rapporte à l'exécution.

.PARAMETER WorkDir
    Chemin COURT pour le clone et les artefacts Rust. Voir piège 2.
#>
[CmdletBinding()]
param(
    [string]$Commit = 'ab673a6d71e6333934cf6cb8f87f9578cdfaed5a',
    [string]$WorkDir = 'C:\mrst',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$coreDir = Join-Path $repoRoot 'core'
$genDir = Join-Path $coreDir 'src\jvmMain\generated\kotlin'
$nativeDir = Join-Path $coreDir 'native'
$srcDir = Join-Path $WorkDir 'matrix-rust-sdk'
$target = 'x86_64-pc-windows-msvc'
$dll = Join-Path $WorkDir "$target\release\matrix_sdk_ffi.dll"

function Step($m) { Write-Host "==> $m" -ForegroundColor Cyan }

# --- Prérequis ---------------------------------------------------------------------------
foreach ($t in 'cargo', 'git') {
    if (-not (Get-Command $t -ErrorAction SilentlyContinue)) { throw "$t introuvable dans le PATH" }
}
$vcvars = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
if (-not (Test-Path $vcvars)) { throw "vcvars64.bat introuvable — installer les Build Tools MSVC" }

# --- 1. Source ---------------------------------------------------------------------------
if (-not (Test-Path $srcDir)) {
    Step "Clone de matrix-rust-sdk dans $srcDir"
    New-Item -ItemType Directory -Force $srcDir | Out-Null
    git -C $srcDir init -q .
    # core.longpaths : sans lui, le checkout échoue sur les fichiers .snap (MAX_PATH).
    git -C $srcDir config core.longpaths true
    git -C $srcDir remote add origin https://github.com/matrix-org/matrix-rust-sdk.git
}
$current = (git -C $srcDir rev-parse HEAD 2>$null)
if ($current -ne $Commit) {
    Step "Récupération du commit $($Commit.Substring(0,9))"
    git -C $srcDir fetch --depth 1 origin $Commit 2>&1 | Out-Null
    git -C $srcDir checkout -q --force FETCH_HEAD
}

# --- 2. android_cleaner = false partout ---------------------------------------------------
Step 'Configuration UniFFI pour la génération desktop'
$patched = 0
Get-ChildItem $srcDir -Recurse -Filter 'uniffi.toml' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch 'target' } | ForEach-Object {
        $c = Get-Content $_.FullName -Raw
        if ($c -match 'android_cleaner\s*=\s*true') {
            ($c -replace 'android_cleaner\s*=\s*true', 'android_cleaner = false') |
                Set-Content $_.FullName -Encoding UTF8 -NoNewline
            $patched++
        }
    }
Write-Host "    $patched fichier(s) ajusté(s)"

# --- 3. Compilation ------------------------------------------------------------------------
if ($Force -or -not (Test-Path $dll)) {
    Step 'Compilation de matrix-sdk-ffi (compter ~11 min à froid)'
    $bat = Join-Path $env:TEMP 'matriscord-build-ffi.bat'
    @"
@echo off
call "$vcvars" >nul 2>&1 || exit /b 1
set CARGO_TARGET_DIR=$WorkDir
cd /d "$srcDir"
cargo build --release -p matrix-sdk-ffi --features sentry --target $target
"@ | Set-Content $bat -Encoding ASCII
    & cmd /c $bat
    if ($LASTEXITCODE -ne 0) { throw "Compilation Rust échouée (code $LASTEXITCODE)" }
} else {
    Step 'DLL déjà présente (utiliser -Force pour recompiler)'
}
if (-not (Test-Path $dll)) { throw "DLL introuvable : $dll" }

# --- 4. Bindings ----------------------------------------------------------------------------
Step 'Génération des bindings Kotlin desktop'
$bindgen = Join-Path $WorkDir 'debug\uniffi-bindgen.exe'
if (-not (Test-Path $bindgen)) {
    Push-Location $srcDir
    $env:CARGO_TARGET_DIR = $WorkDir
    cargo build -p uniffi-bindgen | Out-Null
    Pop-Location
}
New-Item -ItemType Directory -Force $genDir | Out-Null
Push-Location $srcDir   # uniffi-bindgen exige un workspace cargo comme répertoire courant
try {
    # PAS de --config : un fichier unique écraserait le package_name de TOUS les composants,
    # qui se retrouveraient dans le même package où leurs UniffiLib internes se percutent.
    & $bindgen generate --library $dll --language kotlin --no-format --out-dir $genDir
    if ($LASTEXITCODE -ne 0) { throw "uniffi-bindgen a échoué (code $LASTEXITCODE)" }
} finally { Pop-Location }

$android = (Get-ChildItem $genDir -Recurse -Filter *.kt |
    ForEach-Object { Select-String -Path $_.FullName -Pattern '^import (android\.|androidx\.)' }).Count
if ($android -gt 0) { throw "$android import(s) Android dans les bindings desktop — android_cleaner mal appliqué" }

# --- 5. Installation --------------------------------------------------------------------------
New-Item -ItemType Directory -Force $nativeDir | Out-Null
Copy-Item $dll (Join-Path $nativeDir 'matrix_sdk_ffi.dll') -Force

Step 'Terminé'
Write-Host "    bindings : $genDir"
Write-Host "    native   : $nativeDir\matrix_sdk_ffi.dll ($('{0:N0}' -f (Get-Item $dll).Length) octets)"
Write-Host "    commit   : $($Commit.Substring(0,9))  — doit correspondre à sdkGitSha() côté Android"
