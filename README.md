# velvet

hi. this is velvet, a discord lookalike that runs on matrix, on a server you own.

i made it because i wanted my friends and me off discord without making everyone learn a new app. so it looks like discord. a lot. on purpose. if you came here for an original ui, sorry babe, wrong repo

there's an android app (velvet), a desktop app for windows and linux (velvetdesk), and a bash script that sets up the server side on a debian box. it's one person doing this, in the evenings, on a nas that lives in a cupboard. it works for us. it might work for you. i'm honestly a bit sick of it at this point but here we are 🖤

**just want to use it?** grab the apk, the windows installer or the linux archive on [velvet-chat.li/download](https://velvet-chat.li/download). the server script is on [velvet-chat.li/server](https://velvet-chat.li/server).

french version: [README-fr.md](README-fr.md)

## what it does

servers, channels, categories, threads, dms, roles, invites. end to end encryption with device verification. voice and video calls through livekit, screen share on the desktop. push notifications (firebase, or a background service if your phone has no google stuff). custom emoji, stickers, gifs, polls, a soundboard. all the profile stuff, banners, decorations, nameplates, effects, because yes that matters. themes. speech to text running on your own whisper. importing your old discord dms with their real dates. and the app updates itself through matrix.

login goes through matrix authentication service (MAS) if your server has it, so your password is typed on your server's page and never in the app. old school password login still works on servers without it.

what's not great:

- the linux desktop is a tar.gz, not a .deb, and it doesn't update itself yet. window sharing is windows only, whole screens should work on x11
- it was made for one small server. some things assume you run the whole stack from `serveur/`

## the repo

```
matriscord/            all the app code (the folder kept its old name, i'm not renaming it now)
  core/                the engine, matrix rust sdk behind one kotlin interface
  ui-commun/           screens shared by both apps
  app-android/         the phone app
  app-desktop/         the windows app + its installer
  design/              colours, spacing, themes
  verif-bureau/        scripts i run against a real server to check stuff still works
  banc-android/        same idea, on a phone
serveur/               the server install script and a few compose files
```

the apps never call the matrix sdk directly, only the `MatrixEngine` interface in `core`. that's the one rule i actually kept.

the comments are long. they explain why things are weird, mostly because something broke once and i never want to debug it twice.

## building it

you need jdk 21 and the android sdk.

**android**, works from a fresh clone:

```
cd matriscord
./gradlew :app-android:assembleDebug
```

you get an app pointing at no server, you type yours on the login screen. for a release apk you need your own signing key in `local.properties` (see `local.properties.example`, every key is explained there). there's no fallback to the debug key, because the app updates itself and android refuses an update signed by a different key. learned that the hard way.

`-Pvelvet.public=true` builds without anything from your private config. add `-Pvelvet.aosp=true` for phones without google services.

you don't need the private config anymore anyway. the apps ask the server what it offers when you sign in: voice comes from the standard `org.matrix.msc4143.rtc_foci` in its `.well-known`, and everything else (dictation, discord import, gifs, cosmetics catalogue, invite links, push, lan address) from a state event `li.velvetchat.installation` in a room that only accounts of that server can join. the `.well-known` just names that room under `li.velvetchat.salon`. so the apk on the site works on any velvet server, and nothing secret is readable without an account. the gory details are at the top of `core/.../Installation.kt`

**windows**, a bit more annoying. the desktop needs two native libraries that aren't in the repo because they're 150 MB:

- download [velvet-natif-windows.zip](https://velvet-chat.li/dl/velvet-natif-windows.zip) (there's a .sha256 next to it) and unzip it into `matriscord/core/native/`
- or build them yourself with `matriscord/tools/build-desktop-sdk.ps1`, it's like eleven minutes of rust compiling

screen share also needs gstreamer:

```
winget install --id gstreamerproject.gstreamer -e
pwsh matriscord/tools/gstreamer-embarque.ps1
```

then:

```
./gradlew :app-desktop:run                 # just run it
./gradlew :app-desktop:bundleInstalleur    # VelvetSetup.exe
```

**linux**, from any machine, windows included (jpackage only packages for the system it runs on, so it's a plain archive with its own java):

- put the two linux libraries in `matriscord/core/native/linux-x64/`: grab [velvet-natif-linux.tar.gz](https://velvet-chat.li/dl/velvet-natif-linux.tar.gz), or build `libmatrix_sdk_ffi.so` yourself at the same commit as the windows one (`cargo build --release -p matrix-sdk-ffi --features sentry`, ~17 min on 8 cores) and take `liblivekit_ffi.so` from livekit's `livekit-ffi/v0.12.76` release
- then `./gradlew :app-desktop:archiveLinux -Pvelvet.linux=true` gives `app-desktop/build/linux/velvetdesk-linux-x64.tar.gz`

## the server

`serveur/installer-velvet.sh` sets up everything on debian 12 or 13: synapse + postgres, MAS, livekit for calls, sygnal or ntfy for push, whisper for dictation, the discord import service, the mautrix bridge if you want it. it asks before each piece and saves your answers so you can run it again later. please read it before you `sudo` it. it's long but it's commented.

## stuff that's missing on purpose

- **gg sans**, discord's font. not mine to give. the app uses the system font without it. drop the four .ttf files in `res/font/` and `resources/polices/` if you have them
- **sound effects**. i don't know anymore where they came from, so they're not here. the apps are just silent without them, put your own .ogg files in if you want
- **discord's shop art** (decorations etc.). server owners load that into their own catalogue room
- **git history**. this is one commit. the real history has my whole life in it lol

## contributing

issues are welcome. i might answer fast, i might answer in three weeks, depends on how tired i am. if you send a pull request keep it small, i review on my phone in bed.

## licence

AGPL-3.0. if you run a modified velvet for other people, they get your changes. that's the whole point. see LICENSE and NOTICE (third party stuff and what's deliberately not included).

made in liechtenstein. with love and too much dammann tea 🐈‍⬛🔥
