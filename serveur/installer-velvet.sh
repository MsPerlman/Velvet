#!/usr/bin/env bash
# =============================================================================================
#  installer-velvet.sh — sets up the whole velvet stack on a fresh debian
# =============================================================================================
#
#  What it installs, each piece independently:
#
#    · synapse + postgres      the matrix homeserver, the only mandatory piece
#    · mas                     matrix authentication service: logins go through oidc
#    · livekit + jwt           voice and video calls
#    · sygnal OR ntfy          push notifications, through firebase or self hosted
#    · whisper + dictation     voice dictation (whisper.cpp server + gateway)
#    · discord import          the service that replays a discord export into matrix
#    · mautrix-discord         the discord bridge, to keep a foot over there
#    · cloudflared             the tunnel, to expose without opening a port
#
#  Everything is asked, nothing is guessed: the script writes an answers file
#  (`/opt/velvet/reponses.env`) and reads it back when rerun, so an install can be
#  replayed identically, or extended one piece at a time.
#
#  Usage:
#      sudo ./installer-velvet.sh                 # interactive
#      sudo ./installer-velvet.sh --tout          # no questions asked (--all)
#      sudo ./installer-velvet.sh --essai         # dry run, writes nothing (--dry-run)
#      sudo ./installer-velvet.sh --en            # in english
#
#  It speaks french or english (`--fr` / `--en`, otherwise the system language).
#
#  Tested on debian 12 and 13. It never overwrites anything without saying so.
# =============================================================================================
set -euo pipefail

RACINE=${VELVET_RACINE:-/opt/velvet}
ICI=$(cd "$(dirname "$0")" && pwd)
REPONSES="$RACINE/reponses.env"
ESSAI=0
TOUT=0

case "${LANG:-}" in en*) LANGUE=en ;; *) LANGUE=fr ;; esac

for arg in "$@"; do
  case "$arg" in
    --essai|--dry-run) ESSAI=1 ;;
    --tout|--all) TOUT=1 ;;
    --fr) LANGUE=fr ;;
    --en) LANGUE=en ;;
    -h|--help) awk 'NR > 1 && !/^#/ { exit } NR > 1' "$0"; exit 0 ;;
    *) echo "option inconnue / unknown option : $arg" >&2; exit 2 ;;
  esac
done

dit() {
  local cle=$1; shift
  local valeur
  if [ "$LANGUE" = en ]; then valeur=${MOTS_EN[$cle]:-${MOTS_FR[$cle]:-$cle}}
  else valeur=${MOTS_FR[$cle]:-$cle}; fi
  # shellcheck disable=SC2059
  printf "$valeur" "$@"
}

declare -A MOTS_FR=(
  [prerequis]="Prérequis"
  [inchange]="   = %s (inchangé)"
  [sauvegarde]="   ~ %s (sauvegarde %s)"
  [q_livekit_hote]="Nom d'hôte public de LiveKit"
  [q_mas]="Confier les connexions à Matrix Authentication Service (MAS) ?"
  [q_mas_hote]="Nom d'hôte public de MAS"
  [mas_migration]="   migration des comptes Synapse vers MAS (syn2mas), Synapse arrêté le temps de la copie"
  [admin_ligne_mas]="   compte administrateur : cd %s/matrix && docker compose exec mas mas-cli manage register-user -a %s"
  [fin_mas]="  1b. MAS : %s doit arriver sur http://%s:8085 ; %s continue d'arriver sur le port 8008, qui aiguille lui-même les connexions vers MAS."
  [synapse_attente]="   attente de Synapse…"
  [synapse_ok]="   Synapse répond"
  [synapse_ko]="Synapse ne répond pas après 3 minutes : cd %s/matrix && docker compose logs synapse"
  [pont_config]="   config.yaml écrit dans %s/data : à régler (homeserver, jetons) puis docker compose up -d"
  [docker_absent]="Docker absent : installation par le dépôt officiel"
  [docker_present]="   docker %s déjà là"
  [quoi]="Ce qu'on installe"
  [q_domaine]="Nom du serveur Matrix (ce qu'il y a après les deux-points des identifiants)"
  [q_hote]="Nom d'hôte public du homeserver"
  [q_admin]="Identifiant de l'administrateur (sans @ ni domaine)"
  [q_lan]="Adresse du serveur sur le réseau local"
  [q_synapse]="Installer Synapse + PostgreSQL ?"
  [q_livekit]="Installer LiveKit (appels) ?"
  [appli_defaut]="l'identifiant de paquet par défaut ne peut pas être conservé : mettez le vôtre (exemple : com.votredomaine.velvet)"
  [appli_velvet]="l'identifiant de paquet doit contenir « velvet »"
  [appli_points]="l'identifiant de paquet doit ressembler à « com.votredomaine.velvet »"
  [fcm_absent]="clé Firebase introuvable : %s"
  [t_ntfy]="ntfy (notifications auto-hébergées)"
  [ntfy_suite]="   UnifiedPush : l'application s'enregistre auprès de ce ntfy, rien ne passe par Google"
  [push_aucun]="   Sans push, l'application garde son service en avant-plan et sa notification permanente"
  [q_dictee]="Installer la dictée (whisper.cpp + passerelle) ?"
  [q_import]="Installer le service d'import Discord ?"
  [q_pont]="Installer le pont mautrix-discord ?"
  [q_tunnel]="Installer cloudflared (tunnel) ?"
  [q_sdk]="Compiler le SDK Matrix patché (long : une demi-heure et 4 Go) ?"
  [q_fcm]="Chemin de la clé de service Firebase (json), la vôtre"
  [q_appli]="Identifiant du paquet Android (doit contenir « velvet », et vous appartenir)"
  [q_push]="Notifications poussées : sygnal (Firebase), ntfy (auto-hébergé), ou aucune ?"
  [q_langue_dictee]="Langue de la dictée (code ISO : fr, en, es…)"
  [q_modele]="Modèle whisper à télécharger"
  [domaine_obligatoire]="le nom du serveur est obligatoire (exemple : exemple.org)"
  [t_synapse]="Synapse + PostgreSQL"
  [t_livekit]="LiveKit (appels)"
  [t_sygnal]="Sygnal (notifications poussées)"
  [t_dictee]="Dictée (whisper.cpp + passerelle)"
  [t_import]="Service d'import Discord"
  [t_pont]="Pont mautrix-discord"
  [t_tunnel]="Tunnel Cloudflare"
  [t_sdk]="SDK Matrix patché"
  [t_fini]="Installé"
  [admin_ligne]="   compte administrateur : cd %s/matrix && docker compose exec synapse register_new_matrix_user -u %s -a -c /data/homeserver.yaml http://localhost:8008"
  [modele_telecharge]="   téléchargement du modèle : %s"
  [passerelle_ok]="   passerelle velvet-dictee sur le port 8179"
  [passerelle_introuvable]="   passerelle introuvable — copier matriscord/nas/dictee/passerelle.py vers %s"
  [import_copie]="   service copié : %s"
  [import_introuvable]="   service introuvable — copier matriscord/tools/service-import-discord.py vers %s"
  [import_declare]="   déclaré dans app_service_config_files"
  [import_a_declarer]="   Synapse n'est pas géré ici : ajouter %s à app_service_config_files, puis le relancer"
  [import_proprietaire]="   chaque paquet reçoit un fichier 'proprietaire' contenant son identifiant Matrix"
  [pont_premier]="   premier lancement : le pont écrit data/config.yaml, à régler puis relancer"
  [tunnel_jeton]="   poser TUNNEL_TOKEN dans %s/.env avant de démarrer"
  [racine]="Racine : %s — réponses (et secrets) dans %s"
  [sdk_outils]="   installation de rust et des outils de compilation"
  [sdk_clone]="   récupération de matrix-rust-sdk au commit %s"
  [sdk_patch]="   application du correctif : android_cleaner = false partout"
  [sdk_build]="   compilation (matrix-sdk-ffi, feature sentry) — c'est la partie longue"
  [sdk_ok]="   SDK compilé : %s"
  [sdk_rappel]="   ce commit DOIT être celui de l'AAR Android, sinon le module partagé ne compile plus que d'un côté"
)

declare -A MOTS_EN=(
  [prerequis]="Prerequisites"
  [inchange]="   = %s (unchanged)"
  [sauvegarde]="   ~ %s (backup %s)"
  [q_livekit_hote]="Public hostname of LiveKit"
  [q_mas]="Hand logins over to Matrix Authentication Service (MAS)?"
  [q_mas_hote]="Public hostname of MAS"
  [mas_migration]="   moving the synapse accounts to MAS (syn2mas), synapse stopped during the copy"
  [admin_ligne_mas]="   admin account: cd %s/matrix && docker compose exec mas mas-cli manage register-user -a %s"
  [fin_mas]="  1b. MAS: %s has to reach http://%s:8085; %s still goes to port 8008, which routes logins to MAS by itself."
  [synapse_attente]="   waiting for synapse…"
  [synapse_ok]="   synapse answers"
  [synapse_ko]="synapse is not answering after 3 minutes: cd %s/matrix && docker compose logs synapse"
  [pont_config]="   config.yaml written in %s/data: edit it (homeserver, tokens) then docker compose up -d"
  [docker_absent]="Docker missing: installing from the official repository"
  [docker_present]="   docker %s already here"
  [quoi]="What gets installed"
  [q_domaine]="Matrix server name (what comes after the colon in user ids)"
  [q_hote]="Public hostname of the homeserver"
  [q_admin]="Administrator username (without @ or domain)"
  [q_lan]="Address of this server on the local network"
  [q_synapse]="Install Synapse + PostgreSQL?"
  [q_livekit]="Install LiveKit (calls)?"
  [appli_defaut]="the default package id cannot be kept: use your own (example: com.yourdomain.velvet)"
  [appli_velvet]="the package id must contain 'velvet'"
  [appli_points]="the package id should look like 'com.yourdomain.velvet'"
  [fcm_absent]="firebase key not found: %s"
  [t_ntfy]="ntfy (self hosted push)"
  [ntfy_suite]="   unifiedpush: the app registers with this ntfy, nothing goes through google"
  [push_aucun]="   without push, the app keeps its foreground service and its permanent notification"
  [q_dictee]="Install dictation (whisper.cpp + gateway)?"
  [q_import]="Install the Discord import service?"
  [q_pont]="Install the mautrix-discord bridge?"
  [q_tunnel]="Install cloudflared (tunnel)?"
  [q_sdk]="Build the patched Matrix SDK (long: half an hour and 4 GB)?"
  [q_fcm]="Path to your own firebase service account key (json)"
  [q_appli]="Android package id (must contain 'velvet', and must be yours)"
  [q_push]="Push notifications: sygnal (firebase), ntfy (self hosted), or none?"
  [q_langue_dictee]="Dictation language (ISO code: fr, en, es…)"
  [q_modele]="Whisper model to download"
  [domaine_obligatoire]="the server name is required (example: exemple.org)"
  [t_synapse]="Synapse + PostgreSQL"
  [t_livekit]="LiveKit (calls)"
  [t_sygnal]="Sygnal (push notifications)"
  [t_dictee]="Dictation (whisper.cpp + gateway)"
  [t_import]="Discord import service"
  [t_pont]="mautrix-discord bridge"
  [t_tunnel]="Cloudflare tunnel"
  [t_sdk]="Patched Matrix SDK"
  [t_fini]="Installed"
  [admin_ligne]="   admin account: cd %s/matrix && docker compose exec synapse register_new_matrix_user -u %s -a -c /data/homeserver.yaml http://localhost:8008"
  [modele_telecharge]="   downloading the model: %s"
  [passerelle_ok]="   velvet-dictee gateway on port 8179"
  [passerelle_introuvable]="   gateway not found — copy matriscord/nas/dictee/passerelle.py to %s"
  [import_copie]="   service copied: %s"
  [import_introuvable]="   service not found — copy matriscord/tools/service-import-discord.py to %s"
  [import_declare]="   declared in app_service_config_files"
  [import_a_declarer]="   synapse is not managed here: add %s to app_service_config_files, then restart it"
  [import_proprietaire]="   every package gets a 'proprietaire' file holding its owner's matrix id"
  [pont_premier]="   first run: the bridge writes data/config.yaml, edit it then restart"
  [tunnel_jeton]="   put TUNNEL_TOKEN in %s/.env before starting"
  [racine]="Root: %s — answers (and secrets) in %s"
  [sdk_outils]="   installing rust and the build tools"
  [sdk_clone]="   fetching matrix-rust-sdk at commit %s"
  [sdk_patch]="   applying the patch: android_cleaner = false everywhere"
  [sdk_build]="   building (matrix-sdk-ffi, sentry feature) — this is the long part"
  [sdk_ok]="   SDK built: %s"
  [sdk_rappel]="   this commit MUST match the Android AAR, otherwise the shared module only builds on one side"
)

vert() { printf '\033[32m%s\033[0m\n' "$*"; }
gris() { printf '\033[90m%s\033[0m\n' "$*"; }
rouge() { printf '\033[31m%s\033[0m\n' "$*" >&2; }
titre() { MODIFIE=0; printf '\n\033[1m── %s ──\033[0m\n' "$*"; }
# a config file mounted into a container changed: `up -d` alone would keep the old one running
recreer() { [ "${MODIFIE:-0}" = 1 ] && echo --force-recreate; true; }

faire() {
  if [ "$ESSAI" = 1 ]; then gris "   (essai) $*"; else (eval "$@"); fi
}

ecrire() {
  local cible=$1 mode=${2:-}
  local contenu; contenu=$(cat)
  local sauvegarde=
  if [ -f "$cible" ]; then
    if [ "$contenu" = "$(cat "$cible")" ]; then gris "$(dit inchange "$cible")"; return; fi
    sauvegarde="$cible.bak-$(date +%Y%m%d-%H%M%S)"
    gris "$(dit sauvegarde "$cible" "$(basename "$sauvegarde")")"
    [ "$ESSAI" = 1 ] || cp "$cible" "$sauvegarde"
  else
    gris "   + $cible"
  fi
  MODIFIE=1
  [ "$ESSAI" = 1 ] && return 0
  mkdir -p "$(dirname "$cible")"
  printf '%s\n' "$contenu" > "$cible"
  if [ -n "$mode" ]; then
    chmod "$mode" "$cible"
    [ -z "$sauvegarde" ] || chmod "$mode" "$sauvegarde"
  fi
}

demander() {
  local cle=$1 question=$2 defaut=${3:-}
  local actuel=${!cle:-$defaut}
  if [ "$TOUT" = 1 ] && [ -n "$actuel" ]; then printf -v "$cle" '%s' "$actuel"; return; fi
  local reponse
  read -r -p "$question${actuel:+ [$actuel]} : " reponse </dev/tty || true
  printf -v "$cle" '%s' "${reponse:-$actuel}"
}

oui_non() {
  local cle=$1 question=$2 defaut=$3
  local actuel=${!cle:-$defaut}
  if [ "$TOUT" = 1 ]; then printf -v "$cle" '%s' "$actuel"; return; fi
  local reponse
  read -r -p "$question [$actuel] " reponse </dev/tty || true
  reponse=${reponse:-$actuel}
  case "$reponse" in o|O|y|Y|oui|yes) printf -v "$cle" '%s' o ;; *) printf -v "$cle" '%s' n ;; esac
}

attendre_synapse() {
  [ "$ESSAI" = 1 ] && return 0
  gris "$(dit synapse_attente)"
  for _ in $(seq 90); do
    if curl -fs -o /dev/null "http://$LAN:8008/_matrix/client/versions"; then gris "$(dit synapse_ok)"; return 0; fi
    sleep 2
  done
  rouge "$(dit synapse_ko "$RACINE")"; exit 1
}

secret() { head -c 48 /dev/urandom | base64 | tr -d '/+=' | head -c 48; }

[ "$(id -u)" = 0 ] || { rouge "à lancer en root (sudo)"; exit 1; }

titre "$(dit prerequis)"
if ! command -v docker >/dev/null; then
  vert "$(dit docker_absent)"
  faire "apt-get update -qq"
  faire "apt-get install -y -qq ca-certificates curl gnupg"
  faire "install -m 0755 -d /etc/apt/keyrings"
  faire "curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc"
  faire "chmod a+r /etc/apt/keyrings/docker.asc"
  faire "echo 'deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable' > /etc/apt/sources.list.d/docker.list"
  faire "apt-get update -qq"
  faire "apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin"
else
  gris "$(dit docker_present "$(docker --version | cut -d' ' -f3)")"
fi
command -v python3 >/dev/null || faire "apt-get install -y -qq python3"

mkdir -p "$RACINE"
# shellcheck disable=SC1090
[ -f "$REPONSES" ] && . "$REPONSES"

titre "$(dit quoi)"
demander DOMAINE        "$(dit q_domaine)" "${DOMAINE:-}"
demander HOTE_PUBLIC    "$(dit q_hote)" "${HOTE_PUBLIC:-matrix.$DOMAINE}"
demander ADMIN          "$(dit q_admin)" "${ADMIN:-admin}"
demander LAN            "$(dit q_lan)" "${LAN:-$(hostname -I | awk '{print $1}')}"

[ -n "$DOMAINE" ] || { rouge "$(dit domaine_obligatoire)"; exit 2; }

oui_non VEUT_SYNAPSE    "$(dit q_synapse)" "${VEUT_SYNAPSE:-o}"
if [ "$VEUT_SYNAPSE" = o ]; then
  oui_non VEUT_MAS "$(dit q_mas)" "${VEUT_MAS:-$([ -f "$RACINE/matrix/data/homeserver.yaml" ] && echo n || echo o)}"
  [ "$VEUT_MAS" = o ] && demander HOTE_AUTH "$(dit q_mas_hote)" "${HOTE_AUTH:-auth.$DOMAINE}"
fi
VEUT_MAS=${VEUT_MAS:-n}
oui_non VEUT_LIVEKIT    "$(dit q_livekit)" "${VEUT_LIVEKIT:-o}"
[ "$VEUT_LIVEKIT" = o ] && demander HOTE_LIVEKIT "$(dit q_livekit_hote)" "${HOTE_LIVEKIT:-livekit.$DOMAINE}"
demander PUSH           "$(dit q_push)" "${PUSH:-sygnal}"
oui_non VEUT_DICTEE     "$(dit q_dictee)" "${VEUT_DICTEE:-o}"
oui_non VEUT_IMPORT     "$(dit q_import)" "${VEUT_IMPORT:-o}"
oui_non VEUT_PONT       "$(dit q_pont)" "${VEUT_PONT:-n}"
oui_non VEUT_TUNNEL     "$(dit q_tunnel)" "${VEUT_TUNNEL:-n}"
oui_non VEUT_SDK        "$(dit q_sdk)" "${VEUT_SDK:-n}"

if [ "$PUSH" != aucun ] || [ "$VEUT_SYNAPSE" = o ]; then
  demander ID_APPLI "$(dit q_appli)" "${ID_APPLI:-com.exemple.velvet}"
  case "$ID_APPLI" in
    com.exemple.velvet|com.example.velvet|"")
      rouge "$(dit appli_defaut)"; exit 2 ;;
  esac
  case "$ID_APPLI" in
    *velvet*) ;;
    *) rouge "$(dit appli_velvet)"; exit 2 ;;
  esac
  case "$ID_APPLI" in
    *.*.*) ;;
    *) rouge "$(dit appli_points)"; exit 2 ;;
  esac
fi

if [ "$PUSH" = sygnal ]; then
  demander FCM_JSON "$(dit q_fcm)" "${FCM_JSON:-}"
  if [ ! -f "${FCM_JSON:-}" ]; then rouge "$(dit fcm_absent "${FCM_JSON:-}")"; exit 2; fi
fi
[ "$VEUT_DICTEE" = o ] && demander MODELE_WHISPER "$(dit q_modele)" "${MODELE_WHISPER:-large-v3-turbo}"
[ "$VEUT_DICTEE" = o ] && demander LANGUE_DICTEE "$(dit q_langue_dictee)" "${LANGUE_DICTEE:-fr}"

COMMIT_SDK=${COMMIT_SDK:-ab673a6d71e6333934cf6cb8f87f9578cdfaed5a}
MDP_PG=${MDP_PG:-$(secret)}
SECRET_INSCRIPTION=${SECRET_INSCRIPTION:-$(secret)}
SECRET_MACAROON=${SECRET_MACAROON:-$(secret)}
SECRET_FORM=${SECRET_FORM:-$(secret)}
JETON_AS=${JETON_AS:-$(secret)}
JETON_HS=${JETON_HS:-$(secret)}
CLE_LIVEKIT=${CLE_LIVEKIT:-velvet}
SECRET_LIVEKIT=${SECRET_LIVEKIT:-$(secret)}
SECRET_MAS=${SECRET_MAS:-$(secret)}

ecrire "$REPONSES" 600 <<EOF
DOMAINE=$DOMAINE
HOTE_PUBLIC=$HOTE_PUBLIC
ADMIN=$ADMIN
LAN=$LAN
VEUT_SYNAPSE=$VEUT_SYNAPSE
VEUT_MAS=${VEUT_MAS:-n}
HOTE_AUTH=${HOTE_AUTH:-}
VEUT_LIVEKIT=$VEUT_LIVEKIT
HOTE_LIVEKIT=${HOTE_LIVEKIT:-}
PUSH=$PUSH
VEUT_DICTEE=$VEUT_DICTEE
VEUT_IMPORT=$VEUT_IMPORT
VEUT_PONT=$VEUT_PONT
VEUT_TUNNEL=$VEUT_TUNNEL
VEUT_SDK=$VEUT_SDK
COMMIT_SDK=$COMMIT_SDK
ID_APPLI=${ID_APPLI:-}
FCM_JSON=${FCM_JSON:-}
MODELE_WHISPER=${MODELE_WHISPER:-}
LANGUE_DICTEE=${LANGUE_DICTEE:-}
MDP_PG=$MDP_PG
SECRET_INSCRIPTION=$SECRET_INSCRIPTION
SECRET_MACAROON=$SECRET_MACAROON
SECRET_FORM=$SECRET_FORM
JETON_AS=$JETON_AS
JETON_HS=$JETON_HS
CLE_LIVEKIT=$CLE_LIVEKIT
SECRET_LIVEKIT=$SECRET_LIVEKIT
SECRET_MAS=$SECRET_MAS
EOF

AS_HOTE="$RACINE/matrix/data/velvet-import.yaml"
AS_CONTENEUR="/data/velvet-import.yaml"
if [ "$VEUT_IMPORT" = o ]; then AS_FICHIERS="\"$AS_CONTENEUR\""; else AS_FICHIERS=""; fi
[ "$VEUT_IMPORT" = o ] && ecrire "$AS_HOTE" 600 <<EOF
id: velvet-import
url: null
as_token: $JETON_AS
hs_token: $JETON_HS
sender_localpart: velvetimport
namespaces:
  users:
    - exclusive: false
      regex: '@.*:$DOMAINE'
  aliases: []
  rooms: []
rate_limited: false
EOF

# ------------------------------------------------------------------------------- synapse
if [ "$VEUT_SYNAPSE" = o ]; then
  titre "$(dit t_synapse)"
  IMAGE_MAS=ghcr.io/element-hq/matrix-authentication-service:1.28.0
  SYNAPSE_PORTS="    ports:
      - \"$LAN:8008:8008\""
  SERVICES_MAS=
  if [ "$VEUT_MAS" = o ]; then
    SYNAPSE_PORTS=
    SERVICES_MAS="
  mas:
    image: $IMAGE_MAS
    restart: unless-stopped
    depends_on:
      postgres: { condition: service_healthy }
    environment:
      MAS_CONFIG: /mas/config.yaml
    volumes:
      - ./mas:/mas
    ports:
      - \"$LAN:8085:8080\"

  entree:
    image: caddy:2-alpine
    restart: unless-stopped
    depends_on: [synapse, mas]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
    ports:
      - \"$LAN:8008:8008\""
  fi
  ecrire "$RACINE/matrix/docker-compose.yml" <<EOF
services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_USER: synapse
      POSTGRES_PASSWORD: $MDP_PG
      POSTGRES_DB: synapse
      POSTGRES_INITDB_ARGS: "--encoding=UTF8 --lc-collate=C --lc-ctype=C"
    volumes:
      - ./postgres:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -h 127.0.0.1 -U synapse"]
      interval: 10s
      start_period: 120s

  synapse:
    image: matrixdotorg/synapse:latest
    restart: unless-stopped
    depends_on:
      postgres: { condition: service_healthy }
    environment:
      SYNAPSE_CONFIG_PATH: /data/homeserver.yaml
    volumes:
      - ./data:/data
$SYNAPSE_PORTS
$SERVICES_MAS
EOF

  MAS_ACTIF=0
  grep -q '^matrix_authentication_service:' "$RACINE/matrix/data/homeserver.yaml" 2>/dev/null && MAS_ACTIF=1
  A_MIGRER=0
  [ "$VEUT_MAS" = o ] && [ "$MAS_ACTIF" = 0 ] && [ -f "$RACINE/matrix/data/homeserver.yaml" ] && A_MIGRER=1
  SYNAPSE_MAS=
  [ "$VEUT_MAS" = o ] && SYNAPSE_MAS="matrix_authentication_service:
  enabled: true
  endpoint: http://mas:8080/
  secret: \"$SECRET_MAS\"
password_config:
  enabled: false"
  # .well-known/matrix/client: what the apps read at login. the voice focus is the standard
  # key, the li.velvetchat block is everything velvet used to need baked into its build
  RTC_WK="extra_well_known_client_content:"
  [ "$VEUT_LIVEKIT" = o ] && RTC_WK="$RTC_WK
  org.matrix.msc4143.rtc_foci:
    - type: livekit
      livekit_service_url: \"https://$HOTE_LIVEKIT\""
  RTC_WK="$RTC_WK
  li.velvetchat:
    lan: \"http://$LAN:8008\""
  [ "$VEUT_DICTEE" = o ] && RTC_WK="$RTC_WK
    dictee: \"ws://$LAN:8179\""
  [ "$VEUT_IMPORT" = o ] && RTC_WK="$RTC_WK
    import: \"http://$LAN:8181\""
  [ "$PUSH" = sygnal ] && RTC_WK="$RTC_WK
    push: \"http://$LAN:5000/_matrix/push/v1/notify\""
  ecrire "$RACINE/matrix/data/homeserver.yaml" 600 <<EOF
server_name: "$DOMAINE"
public_baseurl: "https://$HOTE_PUBLIC/"
pid_file: /data/homeserver.pid
listeners:
  - port: 8008
    tls: false
    type: http
    x_forwarded: true
    bind_addresses: ['0.0.0.0']
    resources:
      - names: [client, federation]
        compress: false

database:
  name: psycopg2
  args:
    user: synapse
    password: $MDP_PG
    database: synapse
    host: postgres
    cp_min: 5
    cp_max: 10

log_config: "/data/log.config"
media_store_path: /data/media_store
max_upload_size: 250M
registration_shared_secret: "$SECRET_INSCRIPTION"
macaroon_secret_key: "$SECRET_MACAROON"
form_secret: "$SECRET_FORM"
signing_key_path: "/data/$DOMAINE.signing.key"
report_stats: false
enable_registration: false
serve_server_wellknown: true
experimental_features:
  msc3026_enabled: true
  msc3575_enabled: true
  msc4133_enabled: true
max_event_delay_duration: 24h
rc_message:
  per_second: 50
  burst_count: 500
app_service_config_files: [$AS_FICHIERS]
$RTC_WK
$SYNAPSE_MAS
EOF

  ecrire "$RACINE/matrix/data/log.config" <<'EOF'
version: 1
formatters:
  precise:
    format: '%(asctime)s - %(name)s - %(lineno)d - %(levelname)s - %(request)s - %(message)s'
handlers:
  console:
    class: logging.StreamHandler
    formatter: precise
loggers:
  synapse.storage.SQL:
    level: WARNING
root:
  level: INFO
  handlers: [console]
disable_existing_loggers: false
EOF

  if [ "$VEUT_MAS" = o ]; then
    faire "mkdir -p $RACINE/matrix/mas"
    [ -s "$RACINE/matrix/mas/secrets.yaml" ] || faire "docker run --rm $IMAGE_MAS config generate 2>/dev/null | awk '/^[a-z]/ { p = (\$0 == \"secrets:\") } p' > $RACINE/matrix/mas/secrets.yaml && chmod 600 $RACINE/matrix/mas/secrets.yaml"
    ecrire "$RACINE/matrix/mas/config.yaml" 600 <<EOF
http:
  public_base: https://$HOTE_AUTH/
  issuer: https://$HOTE_AUTH/
  listeners:
    - name: web
      resources:
        - name: discovery
        - name: human
        - name: oauth
        - name: compat
        - name: graphql
          undocumented_oauth2_access: true
        - name: assets
        - name: adminapi
      binds:
        - address: '[::]:8080'
    - name: internal
      resources:
        - name: health
      binds:
        - address: '[::]:8081'
database:
  uri: postgresql://synapse:$MDP_PG@postgres/mas
matrix:
  kind: synapse
  homeserver: $DOMAINE
  secret: $SECRET_MAS
  endpoint: http://synapse:8008/
account:
  password_registration_enabled: true
  password_registration_token_required: true
  # no mail server is set up, so an email that has to be verified would block every sign up
  password_registration_email_required: false
passwords:
  enabled: true
  schemes:
    - version: 1
      algorithm: bcrypt
      unicode_normalization: true
    - version: 2
      algorithm: argon2id
$(cat "$RACINE/matrix/mas/secrets.yaml" 2>/dev/null)
EOF
    ecrire "$RACINE/matrix/Caddyfile" <<'EOF'
{
	admin off
	auto_https off
	# keep the client address a tunnel or proxy in front already put in X-Forwarded-For
	servers {
		trusted_proxies static private_ranges
	}
}

:8008 {
	@compat path_regexp ^/_matrix/client/[^/]+/(login|logout|refresh)
	reverse_proxy @compat mas:8080
	reverse_proxy synapse:8008
}
EOF
    faire "chown -R 65532:65532 $RACINE/matrix/mas"
    faire "cd $RACINE/matrix && docker compose up -d --wait postgres"
    faire "cd $RACINE/matrix && docker compose exec -T postgres createdb -U synapse mas 2>/dev/null || true"
    if [ "$A_MIGRER" = 1 ]; then
      gris "$(dit mas_migration)"
      faire "cd $RACINE/matrix && docker compose stop synapse"
      faire "cd $RACINE/matrix && docker compose run --rm -T --no-deps --user 0 -v $RACINE/matrix/data:/synapse:ro mas syn2mas migrate --synapse-config /synapse/homeserver.yaml"
    fi
  fi
  faire "chown -R 991:991 $RACINE/matrix/data"
  faire "cd $RACINE/matrix && docker compose up -d --remove-orphans $(recreer)"
  attendre_synapse
  if [ "$VEUT_MAS" = o ]; then gris "$(dit admin_ligne_mas "$RACINE" "$ADMIN")"
  else gris "$(dit admin_ligne "$RACINE" "$ADMIN")"; fi
fi

# ------------------------------------------------------------------------------- livekit
if [ "$VEUT_LIVEKIT" = o ]; then
  titre "$(dit t_livekit)"
  ecrire "$RACINE/livekit/livekit.yaml" 600 <<EOF
port: 7880
rtc:
  tcp_port: 7881
  port_range_start: 50000
  port_range_end: 50200
  use_external_ip: true
keys:
  $CLE_LIVEKIT: $SECRET_LIVEKIT
EOF
  ecrire "$RACINE/livekit/docker-compose.yml" <<EOF
services:
  livekit:
    image: livekit/livekit-server:v1.9.1
    restart: unless-stopped
    network_mode: host
    command: --config /etc/livekit.yaml
    volumes:
      - ./livekit.yaml:/etc/livekit.yaml:ro

  jwt:
    image: ghcr.io/element-hq/lk-jwt-service:0.6.0
    restart: unless-stopped
    environment:
      LIVEKIT_URL: wss://$HOTE_LIVEKIT
      LIVEKIT_KEY: $CLE_LIVEKIT
      LIVEKIT_SECRET: $SECRET_LIVEKIT
      LIVEKIT_JWT_PORT: 8080
      LIVEKIT_FULL_ACCESS_HOMESERVERS: $DOMAINE
      LIVEKIT_CS_API_URL_OVERRIDES: $DOMAINE=http://$LAN:8008
    ports:
      - "$LAN:8080:8080"
EOF
  faire "cd $RACINE/livekit && docker compose up -d $(recreer)"
fi

# -------------------------------------------------------------------------------- sygnal
if [ "$PUSH" = sygnal ]; then
  titre "$(dit t_sygnal)"
  PROJET_FCM=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['project_id'])"     "$FCM_JSON" 2>/dev/null || echo "")
  if [ -z "$PROJET_FCM" ]; then
    rouge "$(dit fcm_absent "$FCM_JSON")"
    exit 2
  fi
  ecrire "$RACINE/sygnal/sygnal.yaml" <<EOF
log:
  setup:
    version: 1
    formatters:
      normal: {format: "%(asctime)s [%(process)d] %(levelname)-5s %(name)s %(message)s"}
    handlers:
      stderr: {class: logging.StreamHandler, formatter: normal}
    loggers:
      sygnal: {level: INFO}
    root: {handlers: [stderr], level: INFO}
http:
  port: 5000
  bind_addresses: ['0.0.0.0']
apps:
  $ID_APPLI.android:
    type: gcm
    api_version: v1
    project_id: $PROJET_FCM
    service_account_file: /data/fcm.json
EOF
  ecrire "$RACINE/sygnal/docker-compose.yml" <<EOF
services:
  sygnal:
    image: matrixdotorg/sygnal:latest
    restart: unless-stopped
    volumes:
      - ./sygnal.yaml:/sygnal.yaml:ro
      - $FCM_JSON:/data/fcm.json:ro
    ports:
      - "$LAN:5000:5000"
EOF
  faire "cd $RACINE/sygnal && docker compose up -d $(recreer)"

elif [ "$PUSH" = ntfy ]; then
  titre "$(dit t_ntfy)"
  ecrire "$RACINE/ntfy/docker-compose.yml" <<EOF
services:
  ntfy:
    image: binwiederhier/ntfy:latest
    restart: unless-stopped
    command: serve
    environment:
      NTFY_BASE_URL: https://ntfy.$DOMAINE
      NTFY_LISTEN_HTTP: ":80"
      NTFY_BEHIND_PROXY: "true"
      NTFY_AUTH_DEFAULT_ACCESS: read-write
      NTFY_CACHE_FILE: /var/cache/ntfy/cache.db
      NTFY_ATTACHMENT_CACHE_DIR: /var/cache/ntfy/attachments
    volumes:
      - ./cache:/var/cache/ntfy
    ports:
      - "$LAN:8090:80"
EOF
  gris "$(dit ntfy_suite)"
  faire "cd $RACINE/ntfy && docker compose up -d $(recreer)"

else
  gris "$(dit push_aucun)"
fi

if [ "$VEUT_DICTEE" = o ]; then
  titre "$(dit t_dictee)"
  ecrire "$RACINE/dictee/docker-compose.yml" <<EOF
services:
  whisper:
    image: ghcr.io/ggml-org/whisper.cpp:main
    restart: unless-stopped
    command: ["./build/bin/whisper-server -m /models/ggml-$MODELE_WHISPER.bin --host 0.0.0.0 --port 8178 -l ${LANGUE_DICTEE:-fr} -ac 512"]
    volumes:
      - ./models:/models
    ports:
      - "127.0.0.1:8178:8178"
EOF
  MODELE="$RACINE/dictee/models/ggml-$MODELE_WHISPER.bin"
  if [ ! -s "$MODELE" ]; then
    gris "$(dit modele_telecharge "$MODELE")"
    faire "mkdir -p $RACINE/dictee/models"
    faire "curl -fL --progress-bar -o '$MODELE.part' https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-$MODELE_WHISPER.bin && mv '$MODELE.part' '$MODELE'"
  fi
  faire "cd $RACINE/dictee && docker compose up -d $(recreer)"

  SOURCE_DICTEE="$ICI/../matriscord/nas/dictee/passerelle.py"
  if [ -f "$SOURCE_DICTEE" ]; then
    faire "apt-get install -y -qq python3-venv"
    faire "install -m 0755 '$SOURCE_DICTEE' '$RACINE/dictee/passerelle.py'"
    faire "[ -x $RACINE/dictee/venv/bin/python ] || python3 -m venv $RACINE/dictee/venv"
    faire "$RACINE/dictee/venv/bin/pip install -q numpy websockets faster-whisper"
    ecrire "/etc/systemd/system/velvet-dictee.service" <<EOF
[Unit]
Description=Velvet dictation gateway
After=docker.service network-online.target

[Service]
DynamicUser=yes
Environment=VELVET_HOTE=0.0.0.0
Environment=VELVET_PORT=8179
Environment=VELVET_SYNAPSE=http://$LAN:8008
WorkingDirectory=$RACINE/dictee
ExecStart=$RACINE/dictee/venv/bin/python $RACINE/dictee/passerelle.py
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
    faire "systemctl daemon-reload"
    faire "systemctl enable velvet-dictee && systemctl restart velvet-dictee"
    gris "$(dit passerelle_ok)"
  else
    gris "$(dit passerelle_introuvable "$RACINE/dictee/passerelle.py")"
  fi
fi

if [ "$VEUT_IMPORT" = o ]; then
  titre "$(dit t_import)"
  ecrire "/etc/velvet-import.env" 600 <<EOF
AS_TOKEN=$JETON_AS
EOF

  ecrire "/etc/systemd/system/velvet-import.service" <<EOF
[Unit]
Description=Velvet — service d'import Discord
After=network-online.target docker.service

[Service]
User=root
WorkingDirectory=$RACINE/import
Environment=HS=http://$LAN:8008
EnvironmentFile=/etc/velvet-import.env
Environment=VELVET_IMPORT_PAQUETS=$RACINE/import/paquets
Environment=VELVET_IMPORT_PORT=8181
ExecStart=/usr/bin/python3 $RACINE/import/service_import.py
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
  faire "mkdir -p $RACINE/import/paquets"

  SOURCE_IMPORT="$ICI/../matriscord/tools/service-import-discord.py"
  if [ -f "$SOURCE_IMPORT" ]; then
    faire "install -m 0755 '$SOURCE_IMPORT' '$RACINE/import/service_import.py'"
    gris "$(dit import_copie "$RACINE/import/service_import.py")"
    faire "systemctl daemon-reload"
    faire "systemctl enable velvet-import && systemctl restart velvet-import"
  else
    gris "$(dit import_introuvable "$RACINE/import/service_import.py")"
  fi

  if [ "$VEUT_SYNAPSE" = o ]; then
    gris "$(dit import_declare)"
    faire "chown -R 991:991 $RACINE/matrix/data"
    faire "cd $RACINE/matrix && docker compose restart synapse"
    attendre_synapse
  else
    gris "$(dit import_a_declarer "$AS_CONTENEUR")"
  fi
  gris "$(dit import_proprietaire)"
fi

if [ "$VEUT_PONT" = o ]; then
  titre "$(dit t_pont)"
  ecrire "$RACINE/mautrix-discord/docker-compose.yml" <<EOF
services:
  bridge:
    image: dock.mau.dev/mautrix/discord:latest
    restart: unless-stopped
    volumes:
      - ./data:/data
    ports:
      - "$LAN:29334:29334"
EOF
  if [ -f "$RACINE/mautrix-discord/data/config.yaml" ]; then
    faire "cd $RACINE/mautrix-discord && docker compose up -d $(recreer)"
  else
    faire "cd $RACINE/mautrix-discord && docker compose run --rm bridge >/dev/null"
    gris "$(dit pont_config "$RACINE/mautrix-discord")"
  fi
fi

# ------------------------------------------------------------------------------- tunnel
if [ "$VEUT_TUNNEL" = o ]; then
  titre "$(dit t_tunnel)"
  ecrire "$RACINE/cloudflared/docker-compose.yml" <<'EOF'
services:
  cloudflared:
    image: cloudflare/cloudflared:latest
    restart: unless-stopped
    command: tunnel --no-autoupdate run
    environment:
      TUNNEL_TOKEN: ${TUNNEL_TOKEN:?jeton du tunnel absent}
EOF
  gris "$(dit tunnel_jeton "$RACINE/cloudflared")"
fi

if [ "$VEUT_SDK" = o ]; then
  titre "$(dit t_sdk)"
  SDK_DIR="$RACINE/sdk"
  if ! command -v cargo >/dev/null; then
    gris "$(dit sdk_outils)"
    faire "apt-get install -y -qq build-essential pkg-config libssl-dev clang cmake git curl"
    faire "curl -sSf https://sh.rustup.rs | sh -s -- -y --no-modify-path"
    export PATH="$HOME/.cargo/bin:$PATH"
  fi
  gris "$(dit sdk_clone "${COMMIT_SDK:0:9}")"
  if [ ! -d "$SDK_DIR/.git" ]; then
    faire "git clone --filter=blob:none https://github.com/matrix-org/matrix-rust-sdk '$SDK_DIR'"
  fi
  faire "git -C '$SDK_DIR' fetch --depth 1 origin $COMMIT_SDK"
  faire "git -C '$SDK_DIR' checkout --force FETCH_HEAD"

  gris "$(dit sdk_patch)"
  faire "find '$SDK_DIR' -name uniffi.toml -exec sed -i 's/android_cleaner *= *true/android_cleaner = false/' {} +"

  gris "$(dit sdk_build)"
  faire "cd '$SDK_DIR' && cargo build --release -p matrix-sdk-ffi --features sentry"
  faire "cd '$SDK_DIR' && cargo run --release -p uniffi-bindgen -- generate --library target/release/libmatrix_sdk_ffi.so --language kotlin --no-format --out-dir '$SDK_DIR/bindings'"
  gris "$(dit sdk_ok "$SDK_DIR/target/release/libmatrix_sdk_ffi.so")"
  gris "$(dit sdk_rappel)"
fi

titre "$(dit t_fini)"
vert "$(dit racine "$RACINE" "$REPONSES")"
[ "$VEUT_MAS" = o ] && gris "$(dit fin_mas "$HOTE_AUTH" "$LAN" "$HOTE_PUBLIC")"

if [ "$LANGUE" = en ]; then
cat <<EOF

What's left to do by hand, because it depends on you:

  1. DNS: $HOTE_PUBLIC has to reach this server (tunnel, reverse proxy, or an open port).
  2. The admin account (see the line printed above).
  3. The apps need nothing: the ones on velvet-chat.li/download read what this server
     offers (voice, dictation, import, push, its lan address) from its .well-known when you
     sign in. to also hand out an update channel, a cosmetics catalogue, gifs or a site for
     invite links, add them under li.velvetchat in extra_well_known_client_content in
     homeserver.yaml (mises_a_jour, catalogue, intendant, gifs, invitations).
     To build them yourself: ./gradlew :app-android:assembleDebug — that needs nothing else, and gives a
     working app. A *release* apk also needs a signing keystore (four keys in the same
     file): velvet updates itself, and android refuses to install over an apk signed by
     another key, so there is deliberately no fallback to the debug key.

  3b. The windows desktop also needs GStreamer, which travels inside its installer:
         winget install --id gstreamerproject.gstreamer -e
         pwsh matriscord/tools/gstreamer-embarque.ps1
      It installs without administrator rights. packageMsi refuses to run without it
      rather than ship an application whose window sharing is dead.
  4. The windows desktop needs matrix_sdk_ffi.dll, which only builds on windows
     (matriscord/tools/build-desktop-sdk.ps1). The linux build done here covers a linux
     desktop and the bindings — same commit either way, or the shared module stops building
     on one side.

EOF
else
cat <<EOF

Ce qu'il reste à faire à la main, parce que ça dépend de vous :

  1. DNS : $HOTE_PUBLIC doit arriver sur ce serveur (tunnel, reverse proxy, ou port ouvert).
  2. Le compte administrateur (voir la ligne affichée plus haut).
  3. Les applications n'ont besoin de rien : celles de velvet-chat.li/download lisent ce
     que ce serveur propose (vocal, dictée, import, push, son adresse lan) dans son
     .well-known à la connexion. pour distribuer aussi un canal de mises à jour, un
     catalogue de cosmétiques, des gifs ou un site pour les liens d'invitation, ajoutez-les
     sous li.velvetchat dans extra_well_known_client_content de homeserver.yaml
     (mises_a_jour, catalogue, intendant, gifs, invitations).
     Pour les compiler vous-même : ./gradlew :app-android:assembleDebug — qui ne demande rien d'autre et donne une
     application qui marche. Un APK de *release* exige en plus un keystore de signature
     (quatre clés dans le même fichier) : Velvet se met à jour par elle-même, et Android
     refuse d'installer par-dessus un APK signé par une autre clé, donc il n'y a
     délibérément aucun repli sur la clé de débogage.

  3b. Le bureau Windows a aussi besoin de GStreamer, qui voyage dans son installeur :
         winget install --id gstreamerproject.gstreamer -e
         pwsh matriscord/tools/gstreamer-embarque.ps1
      Il s'installe sans droits administrateur. packageMsi refuse de tourner sans lui
      plutôt que de livrer une application dont le partage de fenêtre est mort.
  4. Le bureau Windows a besoin de matrix_sdk_ffi.dll, qui ne se compile que sous Windows
     (matriscord/tools/build-desktop-sdk.ps1). Ce qui est compilé ici vaut pour un bureau
     Linux et pour les liaisons — même commit dans les deux cas, sans quoi le module partagé
     ne compile plus que d'un côté.

EOF
fi
