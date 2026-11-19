#!/usr/bin/env bash
set -euo pipefail

SOURCE="$(cd "$(dirname "$0")" && pwd)/installer-velvet.sh"
[ -f "$SOURCE" ] || { echo "installer-velvet.sh introuvable à côté"; exit 1; }

gris() { :; }
ESSAI=0
# shellcheck disable=SC1090
. /dev/stdin <<<"$(sed -n '/^ecrire() {/,/^}/p' "$SOURCE")"

T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
cd "$T"

echoue() { echo "ÉCHEC — $1"; exit 1; }

ecrire secret.yaml 600 <<'X'
as_token: un
X
[ "$(stat -c %a secret.yaml)" = 600 ] || echoue "mode non appliqué"

ecrire secret.yaml 600 <<'X'
as_token: deux
X
bak=$(ls secret.yaml.bak-* | head -1)
[ "$(stat -c %a "$bak")" = 600 ] || echoue "sauvegarde en $(stat -c %a "$bak")"
grep -q un "$bak" || echoue "la sauvegarde ne porte pas l'ancien contenu"
grep -q deux secret.yaml || echoue "le nouveau contenu n'est pas écrit"

ecrire ordinaire.yml <<'X'
port: 7880
X
[ -f ordinaire.yml ] || echoue "écriture sans mode"

ecrire ordinaire.yml <<'X'
port: 7880
X
[ "$(ls ordinaire.yml* | wc -l)" = 1 ] || echoue "une sauvegarde pour rien"

ESSAI=1
ecrire rien.yml 600 <<'X'
a: b
X
[ ! -e rien.yml ] || echoue "--essai a écrit"

echo "ecrire() : 5 vérifications OK"
