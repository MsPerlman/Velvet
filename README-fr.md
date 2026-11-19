# velvet

coucou. velvet c'est un clone de discord qui tourne sur matrix, sur un serveur qui est à toi.

je l'ai fait parce que je voulais sortir mes potes et moi de discord sans obliger tout le monde à réapprendre une appli. donc ça ressemble à discord. beaucoup. exprès. si tu cherchais une interface originale, désolé·e, mauvais dépôt

y'a une appli android (velvet), une appli de bureau pour windows et linux (velvetdesk), et un script bash qui installe la partie serveur sur une debian. c'est une seule personne qui fait tout ça, le soir, sur un nas rangé dans un placard. chez nous ça marche. chez toi peut-être. honnêtement j'en ai un peu ras le cul de ce projet à ce stade mais bon, on est là 🖤

**tu veux juste l'utiliser ?** l'apk, l'installeur windows et l'archive linux sont sur [velvet-chat.li/fr/download](https://velvet-chat.li/fr/download). le script serveur sur [velvet-chat.li/fr/server](https://velvet-chat.li/fr/server).

english version: [README.md](README.md)

## ce que ça fait

serveurs, salons, catégories, fils, mp, rôles, invitations. chiffrement de bout en bout avec vérification des appareils. appels vocaux et vidéo via livekit, partage d'écran sur le bureau. notifs push (firebase, ou un service en arrière-plan si ton tel n'a pas les trucs google). emojis perso, stickers, gifs, sondages, soundboard. tout le délire du profil, bannières, décos, plaques, effets, parce que oui ça compte. des thèmes. la dictée qui tourne sur ton propre whisper. l'import de tes vieux mp discord avec leurs vraies dates. et l'appli se met à jour toute seule via matrix.

la connexion passe par matrix authentication service (MAS) si ton serveur l'a, donc ton mot de passe se tape sur la page de ton serveur et jamais dans l'appli. sur un serveur sans MAS la connexion classique marche toujours.

ce qui est pas ouf :

- le bureau linux c'est un tar.gz, pas un .deb, et il se met pas encore à jour tout seul. le partage de fenêtre c'est windows seulement, l'écran entier devrait marcher sous x11
- c'est fait pour un petit serveur. certains trucs supposent que t'as toute la stack de `serveur/`

## le dépôt

```
matriscord/            tout le code des applis (le dossier a gardé son ancien nom, j'y touche plus)
  core/                le moteur, le sdk matrix rust derrière une seule interface kotlin
  ui-commun/           les écrans partagés entre les deux applis
  app-android/         l'appli du téléphone
  app-desktop/         l'appli windows + son installeur
  design/              couleurs, espacements, thèmes
  verif-bureau/        des scripts que je lance contre un vrai serveur pour vérifier que ça marche encore
  banc-android/        pareil, sur un téléphone
serveur/               le script d'installation du serveur et quelques fichiers compose
```

les applis appellent jamais le sdk matrix directement, seulement l'interface `MatrixEngine` dans `core`. c'est la seule règle que j'ai vraiment tenue.

les commentaires sont longs. ils expliquent pourquoi des trucs sont bizarres, surtout parce qu'un jour ça a cassé et que je veux plus jamais le débugger deux fois.

## compiler

il te faut le jdk 21 et le sdk android.

**android**, ça marche depuis un clone tout frais :

```
cd matriscord
./gradlew :app-android:assembleDebug
```

t'obtiens une appli qui pointe vers aucun serveur, tu tapes le tien à la connexion. pour un apk de release il te faut ta propre clé de signature dans `local.properties` (regarde `local.properties.example`, chaque clé y est expliquée). y'a pas de repli sur la clé de debug, parce que l'appli se met à jour toute seule et qu'android refuse une mise à jour signée par une autre clé. appris à la dure.

`-Pvelvet.public=true` compile sans rien de ta config privée. ajoute `-Pvelvet.aosp=true` pour les téléphones sans services google.

de toute façon t'as plus besoin de la config privée. les applis demandent au serveur ce qu'il propose quand tu te connectes : le vocal vient du `org.matrix.msc4143.rtc_foci` standard de son `.well-known`, et tout le reste (dictée, import discord, gifs, catalogue de cosmétiques, liens d'invitation, push, adresse lan) d'un événement d'état `li.velvetchat.installation` dans un salon que seuls les comptes de ce serveur peuvent rejoindre. le `.well-known` donne juste le nom de ce salon sous `li.velvetchat.salon`. donc l'apk du site marche sur n'importe quel serveur velvet, et rien de secret ne se lit sans compte. les détails pénibles sont en haut de `core/.../Installation.kt`

**windows**, un peu plus chiant. le bureau a besoin de deux bibliothèques natives qui sont pas dans le dépôt parce qu'elles font 150 Mo :

- télécharge [velvet-natif-windows.zip](https://velvet-chat.li/dl/velvet-natif-windows.zip) (y'a un .sha256 à côté) et dézippe-le dans `matriscord/core/native/`
- ou compile-les toi-même avec `matriscord/tools/build-desktop-sdk.ps1`, compte une dizaine de minutes de rust

le partage d'écran a aussi besoin de gstreamer :

```
winget install --id gstreamerproject.gstreamer -e
pwsh matriscord/tools/gstreamer-embarque.ps1
```

puis :

```
./gradlew :app-desktop:run                 # juste le lancer
./gradlew :app-desktop:bundleInstalleur    # VelvetSetup.exe
```

**linux**, depuis n'importe quelle machine, windows compris (jpackage n'empaquette que pour le système sur lequel il tourne, donc c'est une simple archive avec son propre java) :

- mets les deux bibliothèques linux dans `matriscord/core/native/linux-x64/` : prends [velvet-natif-linux.tar.gz](https://velvet-chat.li/dl/velvet-natif-linux.tar.gz), ou compile `libmatrix_sdk_ffi.so` toi-même au même commit que celle de windows (`cargo build --release -p matrix-sdk-ffi --features sentry`, ~17 min sur 8 cœurs) et prends `liblivekit_ffi.so` dans la release `livekit-ffi/v0.12.76` de livekit
- puis `./gradlew :app-desktop:archiveLinux -Pvelvet.linux=true` donne `app-desktop/build/linux/velvetdesk-linux-x64.tar.gz`

## le serveur

`serveur/installer-velvet.sh` installe tout sur debian 12 ou 13 : synapse + postgres, MAS, livekit pour les appels, sygnal ou ntfy pour les notifs, whisper pour la dictée, le service d'import discord, le pont mautrix si tu veux. il demande avant chaque morceau et garde tes réponses pour que tu puisses le relancer plus tard. lis-le avant de le lancer en `sudo` stp. il est long mais il est commenté.

## ce qui manque exprès

- **gg sans**, la police de discord. c'est pas à moi de la filer. sans elle l'appli prend la police du système. mets les quatre .ttf dans `res/font/` et `resources/polices/` si tu les as
- **les effets sonores**. je sais plus d'où ils viennent, donc ils sont pas là. les applis sont juste silencieuses sans eux, mets tes propres .ogg si tu veux
- **les images de la boutique discord** (décos etc.). chaque admin de serveur les charge dans son propre salon catalogue
- **l'historique git**. c'est un seul commit. le vrai historique contient toute ma vie mdr

## contribuer

les issues sont bienvenues. je réponds peut-être vite, peut-être dans trois semaines, ça dépend de ma fatigue. si tu fais une pull request garde-la petite, je relis sur mon tel dans mon lit.

## licence

AGPL-3.0. si tu fais tourner un velvet modifié pour d'autres gens, ils ont droit à tes modifs. c'est tout l'intérêt. voir LICENSE et NOTICE (les trucs tiers et ce qui est volontairement pas inclus).

fabriqué au liechtenstein. avec amour et trop de thé dammann 🐈‍⬛🔥
