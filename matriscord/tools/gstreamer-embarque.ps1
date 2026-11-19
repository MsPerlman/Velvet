
$ErrorActionPreference = 'Stop'

$racineDepot = Split-Path -Parent $PSScriptRoot
$cible = Join-Path $racineDepot 'core\gstreamer'

$candidats = @(
    $env:GSTREAMER_1_0_ROOT_MSVC_X86_64,
    "$env:LOCALAPPDATA\Programs\gstreamer\1.0\msvc_x86_64",
    'C:\gstreamer\1.0\msvc_x86_64'
) | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\gst-launch-1.0.exe')) }

$source = $candidats | Select-Object -First 1
if (-not $source) {
    Write-Host ''
    Write-Host 'GStreamer introuvable. Installez-le, puis relancez ce script :' -ForegroundColor Yellow
    Write-Host ''
    Write-Host '    winget install --id gstreamerproject.gstreamer -e'
    Write-Host ''
    Write-Host "Il s'installe sans droits administrateur, dans %LOCALAPPDATA%\Programs."
    Write-Host 'A defaut : https://gstreamer.freedesktop.org/download/ (paquet runtime MSVC 64 bits).'
    exit 1
}
Write-Host "Source : $source"

$fichiers = @(
    'bin\ffi-7.dll'
    'bin\glib-2.0-0.dll'
    'bin\gmodule-2.0-0.dll'
    'bin\gobject-2.0-0.dll'
    'bin\gst-launch-1.0.exe'
    'bin\gstaudio-1.0-0.dll'
    'bin\gstbase-1.0-0.dll'
    'bin\gstcodecparsers-1.0-0.dll'
    'bin\gstcodecs-1.0-0.dll'
    'bin\gstd3d11-1.0-0.dll'
    'bin\gstd3dshader-1.0-0.dll'
    'bin\gstdxva-1.0-0.dll'
    'bin\gstreamer-1.0-0.dll'
    'bin\gsttag-1.0-0.dll'
    'bin\gstvideo-1.0-0.dll'
    'bin\intl-8.dll'
    'bin\orc-0.4-0.dll'
    'bin\pcre2-8-0.dll'
    'bin\png16.dll'
    'bin\z-1.dll'
    'lib\gstreamer-1.0\gstaudioconvert.dll'
    'lib\gstreamer-1.0\gstaudioresample.dll'
    'lib\gstreamer-1.0\gstcoreelements.dll'
    'lib\gstreamer-1.0\gstd3d11.dll'
    'lib\gstreamer-1.0\gstpng.dll'
    'lib\gstreamer-1.0\gstvideoconvertscale.dll'
    'lib\gstreamer-1.0\gstvideorate.dll'
    'lib\gstreamer-1.0\gstwasapi2.dll'
)

$manquants = $fichiers | Where-Object { -not (Test-Path (Join-Path $source $_)) }
if ($manquants) {
    Write-Host ''
    Write-Host 'Cette installation de GStreamer est incomplete :' -ForegroundColor Red
    $manquants | ForEach-Object { Write-Host "    $_" }
    Write-Host ''
    Write-Host "Reinstallez en choisissant l'installation complete."
    exit 1
}

Remove-Item $cible -Recurse -Force -ErrorAction SilentlyContinue
foreach ($rel in $fichiers) {
    $dst = Join-Path $cible $rel
    New-Item -ItemType Directory -Force -Path (Split-Path $dst) | Out-Null
    Copy-Item (Join-Path $source $rel) $dst
}

@"
GStreamer, embarque tel quel et sans modification, sous LGPL-2.1-or-later.

Ces fichiers proviennent du paquet officiel GStreamer pour Windows (build MSVC 64 bits)
et ne sont pas l'oeuvre de ce projet. Velvet ne s'y lie pas : il lance gst-launch-1.0
comme un processus separe.

Source et code : https://gstreamer.freedesktop.org/
Texte de la licence : https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

Ces bibliotheques se remplacent : deposez-en d'autres au meme endroit, dans
l'application installee, et Velvet les utilisera.
"@ | Set-Content -Path (Join-Path $cible 'LISEZMOI-LICENCE.txt') -Encoding UTF8

$mo = ((Get-ChildItem $cible -Recurse -File | Measure-Object Length -Sum).Sum / 1MB)
Write-Host ("Pret : {0} fichiers, {1:N1} Mo dans core\gstreamer" -f (Get-ChildItem $cible -Recurse -File).Count, $mo)
