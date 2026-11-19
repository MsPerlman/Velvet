# L'assistant d'installation

À la charte du site (`site/index.modele.html`) : fond nuit, lueur de braise, papier peint au
sceau, lignes de balayage, bandeau néon, cercle de runes, et des boutons biseautés façon
Windows 95 passé au sang.

```
gradlew :app-desktop:bundleInstalleur
→ app-desktop/build/compose/binaries/main/bundle/VelvetSetup.exe   (~137 Mo, un seul fichier)
```

## Ce que c'est

Un *bootstrapper* **WiX Burn** qui embarque le MSI et le lance en silence derrière sa propre
fenêtre. **Le MSI reste dessous, intact** : c'est son `upgradeUuid` qui fait qu'une nouvelle
version *remplace* l'ancienne au lieu d'installer une deuxième Velvet à côté, et le canal de
mise à jour en dépend. Un installeur qui poserait les fichiers lui-même (Inno Setup, un
lanceur à nous) perdrait les deux.

Le MSI est `perUserInstall`, donc **aucune demande d'administrateur**, ni pour lui ni pour le
bootstrapper.

WiX n'est pas une dépendance à installer : le plugin Compose le télécharge déjà dans
`build/wix311` pour fabriquer le MSI, et la tâche Gradle se sert du même.

## Les fichiers

| Fichier | Rôle |
|---|---|
| `bundle.wxs` | le bundle : quel MSI, quelles variables, quelles charges utiles |
| `velvet.thm` | la fenêtre : pages, positions, polices, images |
| `velvet.wxl` | les textes, en français |
| `images.py` | engendre tous les PNG — **la source de vérité**, les PNG sont sa sortie |
| `polices/` | Pirata One, VT323, Chakra Petch (OFL, cf. `OFL.txt`), celles du site |
| `deco-haut.png` | l'en-tête : bandeau, cercle, logo, titre, autocollant, scanlines |
| `rail-*.png`, `cadre-bas.png` | les bords biseautés du cadre et son ombre portée |
| `t-*.png` | la barre de titre rouge, une par page |
| `b-*.png`, `d-*.png` | les boutons, quatre états empilés chacun |

## Quatre choses apprises à la dure

Chacune a coûté un aller-retour. Elles sont là pour éviter de les repayer.

**1. Une image posée en contrôle passe devant tout.** Un `<Image>` plein cadre en guise de
fond recouvre le texte et les boutons — page vide. Essayé en tête *et* en queue de fichier,
même résultat : l'ordre de déclaration n'y change rien pour un `<Image>` face à un `<Text>`.
D'où le découpage en morceaux qui ne touchent aucun contrôle.

**2. `ImageFile` sur `<Theme>` existe dans le schéma mais wixstdba ne le peint pas.** Essayé
avec et sans `SourceX`/`SourceY`, en 614 × 431 comme en 620 × 460 : fond noir à chaque fois.
`ThemeDrawBackground` est dans l'en-tête, mais c'est à l'hôte de l'appeler.

**3. Les coordonnées sont celles de la zone cliente.** `<Window Width="620" Height="460">`
donne 614 × 431 dedans, mesuré par `GetClientRect`. Le décor est dessiné à cette taille-là.

**4. L'ordre de déclaration est l'ordre d'empilement, à l'envers.** Deux boutons au même
endroit — `LaunchButton` et `SuccessRestartButton` — et c'est le premier déclaré qu'on voit.
Déclaré en dernier, « Lancer Velvet » restait invisible et la page de fin n'offrait que
« Fermer ».

Et deux règles de thmutil qui ne se devinent pas :

- **Un bouton qui a une image ne montre plus son libellé.** D'où une image par libellé,
  gravée par `images.py`. Sans image, c'est un bouton Windows gris.
- **`Visible="yes"` sur une image de page la rend visible sur *toutes* les pages.** Les six
  barres de titre se superposaient et on voyait la dernière. L'attribut est à omettre.
- **Un `Hypertext` est un `SysLink` : sans `Background` sur sa police il se peint en blanc.**
  Les `Text`, eux, restent transparents sans rien demander.

Le gabarit d'origine se ressort d'un bundle construit, avec
`dark.exe VelvetSetup.exe -x dossier` — il tombe dans `UX/thm.xml`. Plus sûr que de le
recopier d'internet : c'est celui de *notre* version de WiX.

## Deux langues, deux fichiers

`gradlew :app-desktop:bundleInstalleur` sort **deux** exécutables : `VelvetSetup.exe` en
français et `VelvetSetup-en.exe` en anglais.

Pourquoi deux plutôt qu'un bilingue : wixstdba ne prend qu'un seul fichier de textes et ne
choisit pas la langue tout seul — il faudrait lui passer un identifiant de langue connu à la
construction, ce qui ne veut rien dire. Les images des deux langues portent **les mêmes noms
de fichiers**, dans `fr/` et `en/`, donc `velvet.thm` est unique : c'est le dossier copié qui
décide de ce qu'on lit à l'écran. `images.py` engendre les deux d'un coup.

## Ce qui bouge

Le bandeau du haut défile pour de bon : un `<Billboard>` enchaîne 35 trames à 55 ms. C'est la
**seule** chose qui sait s'animer dans un thème thmutil, et il faut lui donner une image par
trame — d'où les `bandeau-NN.png`, découpés dans une bande large par `images.py`, avec un
nombre de trames égal à une période entière de la phrase pour que la boucle raccorde.

Le cercle de runes ne tourne pas : ce serait le même procédé, 36 trames de plus, pour un
détail qu'on regarde une fois.

## Ce qui reste

- [ ] L'adresse `velvet-chat.li/server` **n'existe pas encore** (domaine commandé chez OVH,
      en attente). À vérifier avant toute diffusion.
- [ ] Le champ « Dossier d'installation » est un `EDIT` Win32 : fond blanc sur page sombre.
      C'est le dernier morceau de l'interface qui ne suit pas la charte.
- [ ] La fenêtre n'a plus de barre de titre, donc **elle ne se déplace pas** : le `x` et le
      `_` dessinés dans la barre rouge sont du décor. On ferme par le bouton du bas. Rendre
      la fenêtre glissable demanderait de traiter `WM_NCHITTEST`, ce que thmutil ne propose
      pas depuis le thème.
- [ ] Pas de signature de code. C'est ça qui fait « pas sérieux », plus que l'apparence.

### Les raccourcis ne sont pas réglables, et voici pourquoi

Une page avancée avec « mettre un raccourci sur le bureau » a été construite puis retirée.
Le MSI de jpackage conditionne ces composants sur `JP_INSTALL_DESKTOP_SHORTCUT` et
`JP_INSTALL_STARTMENU_SHORTCUT` **en booléen nu**, relevé dans sa table `Component`. Or MSI
lit un symbole nu comme « défini ou non », et la chaîne `"0"` est définie : la case décochée
faisait passer `0`, donc *vrai*, donc le raccourci quand même.

Pour l'éteindre il faudrait passer la propriété **vide**, et wixstdba n'écrit qu'un 0 ou un 1
numérique dans la variable d'une case. Il n'y a pas de moyen déclaratif d'y arriver en WiX
v3. La vraie solution serait de retirer les raccourcis du MSI et de les faire poser par
Burn, ce qui est un autre chantier.

## Le script du homelab n'est pas dedans, et c'est exprès

`serveur/installer-velvet.sh` demande `sudo` sur un **Debian 12/13**. L'installeur Windows
tourne dans le compte d'un utilisateur, sans administrateur, sur une autre machine : le
script n'y serait pas exécutable une seule fois. Et tout le monde ici lance
l'installeur, alors qu'une seule personne monte un serveur.

Sa place est sur le site, en une ligne à copier :

```
curl -fsSL https://velvet-chat.li/installer-velvet.sh | sudo bash
```

L'installeur y renvoie depuis sa page de fin, ce qui coûte un lien au lieu de 35 Ko de bash
embarqués chez des gens qui ne les ouvriront jamais.

## Ce qui reste, et ce qui n'en est plus

Releve le 2026-10-06, en lisant `velvet.thm` plutôt qu'en se fiant à la liste.

| Dit ouvert | État réel |
|---|---|
| barre de progression au vert de Windows | **fait** — `Progressbar ImageFile="progres.png"`, engendrée par `barre_progres()` |
| champ « dossier » à fond blanc | **fait** — `Font Id="6"` porte `Background="2A0406"`, et un EDIT Win32 en veut un |
| le lien se peint dans le bleu du système | **fait** — la page de fin utilise un `<Hyperlink>`, dessiné par thmutil, en néon |
| rien n'est animé | **fait** pour le bandeau — `<Billboard Interval="55" Loop="yes">`. Le « cercle qui tourne » n'a jamais existé |
| `velvet-chat.li/server` n'existe pas | **fait** — la page est en ligne, en trois langues, et sert le script |
| pas de signature de code | **câblée, pas faite** — voir ci-dessous |

Le seul `<Hypertext>` qui reste est `FailureLogFileLink`, sur la page d'échec. C'est un
SysLink piloté par wixstdba : on ne peut ni le renommer ni le remplacer sans perdre le lien
vers le journal, qui est précisément ce qu'on veut à ce moment-là. `EulaHyperlink` en est un
aussi, mais `LicenseUrl=""` dans `bundle.wxs` le cache : il ne se voit jamais.

## La signature de code

`bundleInstalleur` signe le MSI **puis** le bundle dès que `velvet.signature.pfx` existe dans
`prive.properties`. Sans lui, la tâche le dit une fois et continue.

L'ordre compte : le bundle embarque le MSI, donc signer le MSI après coup reviendrait à
signer une copie que personne ne reçoit.

Ce que la ligne de build ne peut pas fournir : **un certificat auto-signé ne change rien**.
SmartScreen juge la réputation d'une signature, pas sa présence, et une signature inconnue
vaut une absence. Il faut un OV (réputation à construire) ou un EV (réputation immédiate,
jeton matériel), et les deux s'achètent. C'est la seule raison pour laquelle cette case reste
ouverte.

