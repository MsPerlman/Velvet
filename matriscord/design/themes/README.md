# Thèmes

Fichiers de tokens lus au runtime. Changer une valeur change l'application **sans
recompilation** — c'est l'exigence du §2.4ter du cahier.

## Écrire un thème

Un thème ne redéfinit **que ce qu'il change**. Tout champ absent garde sa valeur par défaut,
donc le fichier le plus court possible est parfaitement valide :

```json
{ "name": "Violet", "colors": { "accent": "#8B7BD8" } }
```

C'est le cas d'usage le plus courant, et `accent-violet.json` en est l'exemple.

## Le modèle

`modele.json` est le thème **avec tous les champs écrits**, 62 en tout. C'est le point de
départ : on le copie, on efface ce qu'on ne veut pas changer, on garde le reste.

**Il est engendré, pas écrit à la main.** Un modèle écrit à la main est faux dès le premier
champ ajouté, et faux en silence : quelqu'un le copie, règle ce qu'il y trouve, et
n'apprend jamais l'existence des autres. `ModeleThemeTest` sérialise le thème par défaut et
échoue quand les deux divergent — et quand il échoue, il écrit à côté ce qu'il attendait,
donc corriger est une copie. Un second test le relit pour prouver qu'un thème complet passe
le chargeur sans rien perdre.

Ce qu'un thème peut changer aujourd'hui :

| Groupe | Ce qu'il tient |
|---|---|
| `colors` | 19 couleurs : fond, surfaces, bordure, trois niveaux de texte, accent, danger, avertissement, succès, pastille et point de non-lu, surlignage et barre de mention, trait de citation, fond de conversation, opacité du voile |
| `radii` | 5 arrondis, dont celui d'une icône de serveur au repos et sélectionnée |
| `spacing` | 6 pas d'espacement |
| `tailles` | icône de serveur, pastille, texte de pastille, bouton d'envoi |
| `typography` | 2 familles plus une à chasse fixe, corps, interligne, titres, légende, et les échelles de titre, de sous-texte et d'emoji géant |
| `density` | 14 réglages : **largeur du rail des serveurs**, des listes de salons et de membres, hauteur de ligne, regroupement des messages, taille d'avatar, seuils de glissement, largeur du tiroir |
| `motion` | durées, courbes de Bézier à l'entrée et à la sortie, ressorts, et un interrupteur global |

## Garanties

| Situation | Comportement |
|---|---|
| Fichier absent ou vide | Thème par défaut |
| Champs manquants | Valeurs par défaut, champ par champ |
| Clé inconnue (thème écrit pour une version plus récente) | Ignorée, le reste s'applique |
| JSON malformé | Thème par défaut **et motif conservé**, à afficher dans les réglages |

**Un thème ne peut pas casser l'application.** C'est ce qui rend le rechargement à chaud sûr :
on peut éditer le fichier pendant que le client tourne sans risquer un écran noir. Un thème
refusé le dit ; il n'échoue jamais en silence.

## Structure

| Groupe | Contenu | Référence cahier |
|---|---|---|
| `colors` | Palette teal/cyan sombre, propre au projet | §7 |
| `radii` | Coins arrondis généreux | §7 |
| `typography` | Deux familles, dont une à chiffres tabulaires | §7 |
| `density` | Largeurs de rail/listes, espacements — densité Discord | §7 |
| `motion` | Durées courtes, et interrupteur global | §7 |

**Cette page affirmait que « la palette n'emprunte aucune valeur à Discord ». Ce n'est plus
vrai depuis le 19 août 2026**, et la décision est consignée dans `Colors` : le propriétaire du
projet a demandé un clone fidèle après que la réserve légale a été soulevée, et les valeurs par
défaut sont désormais celles de la référence. Les polices, elles, ne sont toujours pas reprises
— c'est la *structure* typographique à deux familles qui est reproduite, pas l'actif protégé.

## Thèmes livrés

| Fichier | Ce qu'il est |
|---|---|
| `accent-violet.json` | L'exemple minimal : une seule couleur redéfinie |
| `velvet-curfew.json` | Encre, bordeaux et amande — la palette proposée comme défaut de Velvet |
| `arctic.json` | Vert profond, jaune soleil, menthe glacier |
| `blossom.json` | **Clair** : vapeur rose, corail, lavande |
| `soft-canvas.json` | **Clair** : écru, coquelicot, pourpre impérial |

Les trois couleurs d'une palette ne suffisent pas à habiller une application : un thème a besoin
d'un fond, de deux niveaux de surface, de trois niveaux de texte et d'une bordure. Les valeurs
des palettes sont donc posées **telles quelles** là où elles conviennent, et les intermédiaires
en sont **dérivés** — plus sombre pour un fond, plus clair pour une surface surélevée.

## Poser un thème

Copier un de ces fichiers sous le nom `theme.json` dans le dossier de données :
`%APPDATA%\VelvetDesk\theme.json`. Il est lu au démarrage ; un fichier absent ou illisible
laisse le thème par défaut.

`motion.enabled` permet de couper toutes les animations sans toucher au code, pour vérifier
une hypothèse de performance — la contrainte « zéro animation continue coûteuse » du §7 vient
directement du liquid glass temps réel qui a fait ramer NEST et Café Leblanc.
