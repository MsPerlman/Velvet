
import math
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ICI = os.path.dirname(os.path.abspath(__file__))
POLICES = os.path.join(ICI, "polices")

NUIT = (10, 1, 2)            # --nuit
BRAISE = (42, 4, 6)          # --braise
BRAISE_CLAIRE = (74, 8, 12)  # --braise-claire
SANG = (225, 6, 27)          # --sang
SANG_SOMBRE = (143, 4, 18)   # --sang-sombre
OS = (243, 233, 220)         # --os
CENDRE = (181, 155, 151)     # --cendre
NEON = (245, 239, 0)         # --neon
BORD_CLAIR = (255, 90, 102)  # --bord-clair
BORD_SOMBRE = (58, 0, 5)     # --bord-sombre

LARGEUR, HAUTEUR = 620, 460

def _version():
    chemin = os.path.normpath(os.path.join(ICI, "..", "..", "gradle.properties"))
    with open(chemin, encoding="utf-8") as f:
        for ligne in f:
            if ligne.startswith("velvet.version="):
                return ligne.split("=", 1)[1].strip()
    raise SystemExit("velvet.version absente de " + chemin)

VERSION = _version()

PHRASES = {
    "fr": {
        "bandeau": "* bienvenue, âme de passage * zéro pub, zéro traqueur "
                   "* tout dort sur ta machine ",
        "sous_titre": "installation pour Windows",
        "titres": [("t-accueil.png", "velvet.exe — bienvenue"),
                   ("t-options.png", "velvet.exe — installation avancée"),
                   ("t-progres.png", "velvet.exe — ça descend"),
                   ("t-fin.png", "velvet.exe — c'est bon"),
                   ("t-rate.png", "velvet.exe — ça a raté"),
                   ("t-deja.png", "velvet.exe — déjà là")],
        "primaires": [("b-installer.png", "Installer"), ("b-valider.png", "Valider"),
                      ("b-lancer.png", "Lancer Velvet"), ("b-redemarrer.png", "Redémarrer"),
                      ("b-desinstaller.png", "Désinstaller"), ("b-continuer.png", "Continuer")],
        "discrets": [("d-options.png", "Avancé"), ("d-annuler.png", "Annuler"),
                     ("d-retour.png", "Retour"), ("d-parcourir.png", "Parcourir"),
                     ("d-fermer.png", "Fermer"), ("d-reparer.png", "Réparer")],
    },
    "en": {
        "bandeau": "* welcome, passing soul * no ads, no trackers "
                   "* it all sleeps on your own machine ",
        "sous_titre": "setup for Windows",
        "titres": [("t-accueil.png", "velvet.exe — welcome"),
                   ("t-options.png", "velvet.exe — advanced setup"),
                   ("t-progres.png", "velvet.exe — coming down"),
                   ("t-fin.png", "velvet.exe — all good"),
                   ("t-rate.png", "velvet.exe — it failed"),
                   ("t-deja.png", "velvet.exe — already here")],
        "primaires": [("b-installer.png", "Install"), ("b-valider.png", "Done"),
                      ("b-lancer.png", "Start Velvet"), ("b-redemarrer.png", "Reboot"),
                      ("b-desinstaller.png", "Uninstall"), ("b-continuer.png", "Carry on")],
        "discrets": [("d-options.png", "Advanced"), ("d-annuler.png", "Cancel"),
                     ("d-retour.png", "Back"), ("d-parcourir.png", "Browse"),
                     ("d-fermer.png", "Close"), ("d-reparer.png", "Repair")],
    },
}

def pol(nom, taille):
    return ImageFont.truetype(os.path.join(POLICES, nom), taille)

def pirata(t):
    return pol("PirataOne-Regular.ttf", t)

def vt(t):
    return pol("VT323-Regular.ttf", t)

def chakra(t, gras=True):
    return pol("ChakraPetch-Bold.ttf" if gras else "ChakraPetch-Regular.ttf", t)

def centre(d, boite, texte, f, couleur):
    x0, y0, x1, y1 = boite
    b = d.textbbox((0, 0), texte, font=f)
    d.text((x0 + (x1 - x0 - (b[2] - b[0])) / 2 - b[0],
            y0 + (y1 - y0 - (b[3] - b[1])) / 2 - b[1]), texte, font=f, fill=couleur)

def papier_peint(im):
    tuile = Image.new("RGBA", (84, 84), (0, 0, 0, 0))
    d = ImageDraw.Draw(tuile)
    rouge = SANG + (23,)  # stroke-opacity .09
    d.ellipse((25, 25, 59, 59), outline=rouge, width=1)
    d.line([(42, 25), (52, 56), (26, 37), (58, 37), (32, 56), (42, 25)], fill=rouge, width=1)
    for y in range(0, HAUTEUR, 84):
        for x in range(0, LARGEUR, 84):
            im.alpha_composite(tuile, (x, y))

def lueur(im):
    couche = Image.new("RGBA", (LARGEUR, HAUTEUR), (0, 0, 0, 0))
    d = ImageDraw.Draw(couche)
    cx, cy = LARGEUR // 2, int(HAUTEUR * 0.18)
    rx, ry = int(LARGEUR * 0.75), int(HAUTEUR * 0.62)
    pas = 44
    for i in range(pas, 0, -1):
        k = i / pas
        a = int(56 * (1 - k) ** 1.6)
        d.ellipse((cx - rx * k, cy - ry * k, cx + rx * k, cy + ry * k), fill=SANG + (a,))
    im.alpha_composite(couche.filter(ImageFilter.GaussianBlur(18)))

def scanlines(im):
    couche = Image.new("RGBA", (LARGEUR, HAUTEUR), (0, 0, 0, 0))
    d = ImageDraw.Draw(couche)
    for y in range(0, HAUTEUR, 3):
        d.line([(0, y), (LARGEUR, y)], fill=(0, 0, 0, 56))
    im.alpha_composite(couche)

def cercle_runes(im, cx, cy, r):
    couche = Image.new("RGBA", (LARGEUR, HAUTEUR), (0, 0, 0, 0))
    d = ImageDraw.Draw(couche)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=SANG + (190,), width=2)
    d.ellipse((cx - r + 7, cy - r + 7, cx + r - 7, cy + r - 7), outline=SANG_SOMBRE + (160,), width=1)
    for i in range(36):
        a = math.radians(i * 10)
        x0, y0 = cx + (r - 4) * math.cos(a), cy + (r - 4) * math.sin(a)
        x1, y1 = cx + (r + 1) * math.cos(a), cy + (r + 1) * math.sin(a)
        d.line([(x0, y0), (x1, y1)], fill=SANG + (120 if i % 3 else 220,), width=2)
    im.alpha_composite(couche)

    src = Image.open(os.path.join(ICI, "logo-source.png")).convert("RGBA")
    taille = int(r * 1.42)
    petit = src.resize((taille, taille), Image.LANCZOS)
    masque = Image.new("L", (taille, taille), 0)
    ImageDraw.Draw(masque).ellipse((0, 0, taille - 1, taille - 1), fill=255)
    halo = Image.new("RGBA", (LARGEUR, HAUTEUR), (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    hd.ellipse((cx - taille // 2 - 6, cy - taille // 2 - 6,
                cx + taille // 2 + 6, cy + taille // 2 + 6), fill=SANG + (110,))
    im.alpha_composite(halo.filter(ImageFilter.GaussianBlur(11)))
    im.paste(petit, (cx - taille // 2, cy - taille // 2), masque)

def bandeau_frames(langue, sortie):
    f = vt(21)
    phrase = PHRASES[langue]["bandeau"]
    mesure = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    periode = int(mesure.textlength(phrase, font=f))

    large = Image.new("RGB", (periode * 2 + LARGEUR, 31), NEON)
    d = ImageDraw.Draw(large)
    x = 0
    while x < large.width:
        d.text((x, 2), phrase, font=f, fill=NUIT)
        x += periode
    d.rectangle((0, 29, large.width, 31), fill=NUIT)

    pas = max(6, periode // 34)
    noms = []
    for i, dx in enumerate(range(0, periode, pas)):
        nom = "bandeau-%02d.png" % i
        large.crop((dx, 0, dx + LARGEUR, 31)).save(os.path.join(sortie, nom))
        noms.append(nom)
    return noms

def titre(im, version, langue):
    d = ImageDraw.Draw(im)
    f = pirata(66)
    x, y = 176, 42
    d.text((x, y + 4), "Velvet", font=f, fill=SANG_SOMBRE)
    halo = Image.new("RGBA", (LARGEUR, HAUTEUR), (0, 0, 0, 0))
    ImageDraw.Draw(halo).text((x, y), "Velvet", font=f, fill=SANG + (200,))
    im.alpha_composite(halo.filter(ImageFilter.GaussianBlur(9)))
    d.text((x, y), "Velvet", font=f, fill=OS)
    d.text((x + 4, y + 78), PHRASES[langue]["sous_titre"], font=chakra(15, False), fill=CENDRE)

    et = Image.new("RGBA", (150, 46), (0, 0, 0, 0))
    ed = ImageDraw.Draw(et)
    ed.rectangle((4, 4, 128, 34), fill=NEON + (255,), outline=(0, 0, 0, 255), width=2)
    ed.text((12, 6), f"v{version}", font=vt(22), fill=NUIT)
    im.alpha_composite(et.rotate(7, resample=Image.BICUBIC, expand=False), (LARGEUR - 176, 34))

def fenetre(im, y0, y1):
    d = ImageDraw.Draw(im)
    x0, x1 = 24, LARGEUR - 24
    d.rectangle((x0 + 8, y0 + 8, x1 + 8, y1 + 8), fill=(0, 0, 0, 255))     # l'ombre
    d.rectangle((x0, y0, x1, y1), fill=(18, 2, 4, 235))
    for i in range(3):
        d.line([(x0 + i, y0 + i), (x1 - i, y0 + i)], fill=(90, 11, 16))
        d.line([(x0 + i, y0 + i), (x0 + i, y1 - i)], fill=(90, 11, 16))
        d.line([(x0 + i, y1 - i), (x1 - i, y1 - i)], fill=(18, 0, 1))
        d.line([(x1 - i, y0 + i), (x1 - i, y1 - i)], fill=(18, 0, 1))
    for x in range(x0 + 3, x1 - 2):
        k = (x - x0) / max(1, (x1 - x0))
        if k < 0.7:
            c = tuple(int(a + (b - a) * (k / 0.7)) for a, b in zip(SANG_SOMBRE, SANG))
        else:
            c = tuple(int(a + (b - a) * ((k - 0.7) / 0.3)) for a, b in zip(SANG, (255, 61, 77)))
        d.line([(x, y0 + 3), (x, y0 + 29)], fill=c)
    for i, s in enumerate(("_", "x")):
        bx = x1 - 30 - i * 26
        d.rectangle((bx, y0 + 6, bx + 21, y0 + 26), fill=BRAISE, outline=(122, 26, 32))
        centre(d, (bx, y0 + 6, bx + 21, y0 + 26), s, vt(17), OS)

def morceaux(sortie, langue, version=VERSION):
    plein = Image.new("RGBA", (LARGEUR, HAUTEUR), NUIT + (255,))
    lueur(plein)
    papier_peint(plein)
    cercle_runes(plein, 96, 100, 52)
    titre(plein, version, langue)
    scanlines(plein)

    plein.convert("RGB").save(os.path.join(sortie, "fond.png"))

    plein.crop((0, 31, LARGEUR, 160)).convert("RGB").save(
        os.path.join(sortie, "deco-haut.png"))

    g = Image.new("RGB", (3, 202), NUIT)
    ImageDraw.Draw(g).rectangle((0, 0, 2, 201), fill=(90, 11, 16))
    g.save(os.path.join(sortie, "rail-gauche.png"))

    d_ = Image.new("RGB", (11, 202), NUIT)
    dd = ImageDraw.Draw(d_)
    dd.rectangle((0, 0, 2, 201), fill=(18, 0, 1))
    dd.rectangle((3, 8, 10, 201), fill=(0, 0, 0))
    d_.save(os.path.join(sortie, "rail-droit.png"))

    b = Image.new("RGB", (580, 12), NUIT)
    bd = ImageDraw.Draw(b)
    bd.rectangle((0, 0, 571, 3), fill=(18, 0, 1))
    bd.rectangle((8, 4, 579, 11), fill=(0, 0, 0))
    b.save(os.path.join(sortie, "cadre-bas.png"))

def barre_titre(sortie, nom, texte):
    l, h = 572, 30
    im = Image.new("RGB", (l, h), NUIT)
    d = ImageDraw.Draw(im)
    for x in range(l):
        k = x / l
        if k < 0.7:
            c = tuple(int(a + (b - a) * (k / 0.7)) for a, b in zip(SANG_SOMBRE, SANG))
        else:
            c = tuple(int(a + (b - a) * ((k - 0.7) / 0.3)) for a, b in zip(SANG, (255, 61, 77)))
        d.line([(x, 3), (x, h - 1)], fill=c)
    d.rectangle((0, 0, l - 1, 2), fill=(90, 11, 16))
    d.rectangle((0, 0, 2, h - 1), fill=(90, 11, 16))
    d.rectangle((l - 3, 0, l - 1, h - 1), fill=(18, 0, 1))
    d.text((12, 4), texte, font=vt(21), fill=OS)
    for i, sgn in enumerate(("x", "_")):
        bx = l - 32 - i * 26
        d.rectangle((bx, 5, bx + 21, 24), fill=BRAISE, outline=(122, 26, 32))
        centre(d, (bx, 5, bx + 21, 24), sgn, vt(16), OS)
    im.save(os.path.join(sortie, nom))

def barre_progres(sortie):
    h = 14
    etats = [SANG, (224, 87, 91), SANG_SOMBRE, SANG_SOMBRE]
    im = Image.new("RGB", (120, h * 4), NUIT)
    d = ImageDraw.Draw(im)
    for i, c in enumerate(etats):
        y = i * h
        d.rectangle((0, y, 119, y + h - 1), fill=c)
        d.line([(0, y), (119, y)], fill=BORD_CLAIR)
        d.line([(0, y + h - 1), (119, y + h - 1)], fill=BORD_SOMBRE)
    im.save(os.path.join(sortie, "progres.png"))

def biseau(d, boite, fond_, clair, sombre):
    x0, y0, x1, y1 = boite
    d.rectangle(boite, fill=fond_)
    for i in range(3):
        d.line([(x0 + i, y0 + i), (x1 - i, y0 + i)], fill=clair)
        d.line([(x0 + i, y0 + i), (x0 + i, y1 - i)], fill=clair)
        d.line([(x0 + i, y1 - i), (x1 - i, y1 - i)], fill=sombre)
        d.line([(x1 - i, y0 + i), (x1 - i, y1 - i)], fill=sombre)

def bouton(sortie, nom, libelle, largeur, hauteur, sang=True):
    if sang:
        etats = [(SANG, BORD_CLAIR, BORD_SOMBRE, 5, 0),
                 ((255, 26, 46), BORD_CLAIR, BORD_SOMBRE, 5, 0),
                 (SANG, BORD_SOMBRE, BORD_CLAIR, 2, 3),
                 ((70, 20, 24), (92, 40, 44), (30, 8, 10), 5, 0)]
        textes = [OS, OS, OS, (140, 120, 118)]
    else:
        etats = [(BRAISE_CLAIRE, (122, 26, 32), (26, 0, 2), 5, 0),
                 ((94, 13, 18), (122, 26, 32), (26, 0, 2), 5, 0),
                 (BRAISE_CLAIRE, (26, 0, 2), (122, 26, 32), 2, 3),
                 ((40, 8, 10), (70, 20, 24), (20, 0, 2), 5, 0)]
        textes = [OS, OS, OS, (120, 100, 98)]

    im = Image.new("RGB", (largeur, hauteur * 4), NUIT)
    f = chakra(16)
    for i, ((fnd, clair, sombre, ombre, glisse), ct) in enumerate(zip(etats, textes)):
        y = i * hauteur
        d = ImageDraw.Draw(im)
        corps = (glisse, y + glisse, largeur - 1 - (5 - glisse), y + hauteur - 1 - (5 - glisse))
        d.rectangle((corps[0] + ombre, corps[1] + ombre, corps[2] + ombre, corps[3] + ombre),
                    fill=(0, 0, 0))
        biseau(d, corps, fnd, clair, sombre)
        centre(d, corps, libelle, f, ct)
    im.save(os.path.join(sortie, nom))

def tout(langue, version=VERSION):
    sortie = os.path.join(ICI, langue)
    os.makedirs(sortie, exist_ok=True)
    trames = bandeau_frames(langue, sortie)
    morceaux(sortie, langue, version)
    barre_progres(sortie)
    for nom, texte in PHRASES[langue]["titres"]:
        barre_titre(sortie, nom, texte)
    for nom, libelle in PHRASES[langue]["primaires"]:
        bouton(sortie, nom, libelle, 168, 44, sang=True)
    for nom, libelle in PHRASES[langue]["discrets"]:
        bouton(sortie, nom, libelle, 126, 44, sang=False)
    return trames

if __name__ == "__main__":
    for lg in ("fr", "en"):
        trames = tout(lg)
        print(lg, ":", len(os.listdir(os.path.join(ICI, lg))), "images, dont",
              len(trames), "trames de bandeau")
