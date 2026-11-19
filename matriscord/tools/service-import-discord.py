#!/usr/bin/env python3

import http.server
import json
import os
import re
import socketserver
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

HS = os.environ.get("HS", "http://synapse:8008").rstrip("/")
AS_TOKEN = os.environ.get("AS_TOKEN", "")
PAQUETS = os.environ.get("VELVET_IMPORT_PAQUETS", "/paquets")
ADMINS = {a.strip() for a in os.environ.get("VELVET_IMPORT_ADMINS", "").split(",") if a.strip()}
PROPRIETAIRES = dict(
    p.split(":", 1) for p in os.environ.get("VELVET_IMPORT_PROPRIETAIRES", "").split(",") if ":" in p
)
PORT = int(os.environ.get("VELVET_IMPORT_PORT", "8181"))

EMOJI_RX = re.compile(r"<(a?):(\w+):(\d+)>")
HDR_AS = {"Authorization": "Bearer " + AS_TOKEN, "Content-Type": "application/json"}

def _req(methode, url, corps=None, entetes=None, brut=False, delai=60):
    h = dict(HDR_AS)
    if entetes:
        h.update(entetes)
    data = corps if brut else (json.dumps(corps).encode() if corps is not None else None)
    requete = urllib.request.Request(url, data=data, headers=h, method=methode)
    for essai in range(5):
        try:
            with urllib.request.urlopen(requete, timeout=delai) as r:
                return json.loads(r.read() or b"{}")
        except urllib.error.HTTPError as e:
            texte = e.read().decode(errors="replace")
            if e.code == 429:
                attente = 1.0
                try:
                    attente = json.loads(texte).get("retry_after_ms", 1000) / 1000.0
                except Exception:
                    pass
                time.sleep(min(attente, 10))
                continue
            raise RuntimeError(f"{methode} {url} → {e.code} {texte[:300]}")
        except (urllib.error.URLError, TimeoutError):
            time.sleep(2 * (essai + 1))
    raise RuntimeError(f"{methode} {url} → échec après cinq essais")

def url_as(chemin, mxid):
    sep = "&" if "?" in chemin else "?"
    return f"{HS}{chemin}{sep}user_id={urllib.parse.quote(mxid)}"

def qui_est(jeton):
    requete = urllib.request.Request(
        HS + "/_matrix/client/v3/account/whoami",
        headers={"Authorization": "Bearer " + jeton},
    )
    try:
        with urllib.request.urlopen(requete, timeout=15) as r:
            return json.loads(r.read()).get("user_id")
    except Exception:
        return None

NOM_PAQUET = re.compile(r"[A-Za-z0-9][A-Za-z0-9 ._-]{0,127}")

def nom_sain(paquet):
    return bool(paquet) and NOM_PAQUET.fullmatch(paquet) is not None and ".." not in paquet

def proprietaire_de(paquet):
    fichier = os.path.join(PAQUETS, paquet, "proprietaire")
    if os.path.isfile(fichier):
        with open(fichier, encoding="utf-8") as f:
            nom = f.read().strip()
            if nom:
                return nom
    return PROPRIETAIRES.get(paquet)

def accessible(paquet, qui):
    if not nom_sain(paquet):
        return False
    maitre = proprietaire_de(paquet)
    if maitre is None:
        return qui in ADMINS
    return maitre == qui

def paquets(qui=None):
    if not os.path.isdir(PAQUETS):
        return []
    sortie = []
    for nom in sorted(os.listdir(PAQUETS)):
        racine = os.path.join(PAQUETS, nom)
        messages = dossier_messages(racine)
        if not messages:
            continue
        if qui is not None and not accessible(nom, qui):
            continue
        sortie.append({
            "nom": nom,
            "salons": len([d for d in os.listdir(messages) if d.startswith("c")]),
            "modifie": int(os.path.getmtime(racine) * 1000),
            "proprietaire": proprietaire_de(nom),
        })
    return sorted(sortie, key=lambda p: -p["modifie"])

def membre_du_salon(jeton, salon, qui):
    chemin = (
        HS + "/_matrix/client/v3/rooms/" + urllib.parse.quote(salon)
        + "/state/m.room.member/" + urllib.parse.quote(qui)
    )
    requete = urllib.request.Request(chemin, headers={"Authorization": "Bearer " + jeton})
    try:
        with urllib.request.urlopen(requete, timeout=15) as r:
            return json.loads(r.read()).get("membership") == "join"
    except Exception:
        return False

def dossier_messages(racine):
    for nom in ("Messages", "messages"):
        chemin = os.path.join(racine, nom)
        if os.path.isdir(chemin):
            return chemin
    return None

def lire_json(chemin):
    with open(chemin, encoding="utf-8", errors="replace") as f:
        return json.load(f)

def arbre(paquet):
    racine = os.path.join(PAQUETS, paquet)
    messages = dossier_messages(racine)
    if not messages:
        raise FileNotFoundError("paquet illisible : " + paquet)

    index = {}
    fichier_index = os.path.join(messages, "index.json")
    if os.path.isfile(fichier_index):
        index = {str(k): v for k, v in (lire_json(fichier_index) or {}).items()}

    serveurs, mp = {}, []
    for nom in os.listdir(messages):
        dossier = os.path.join(messages, nom)
        fiche = os.path.join(dossier, "channel.json")
        if not os.path.isfile(fiche):
            continue
        try:
            c = lire_json(fiche)
        except Exception:
            continue
        octets = 0
        fichier = os.path.join(dossier, "messages.json")
        if os.path.isfile(fichier):
            octets = os.path.getsize(fichier)
        entree = {
            "id": str(c.get("id") or nom.lstrip("c")),
            "nom": c.get("name") or "sans nom",
            "type": c.get("type") or "?",
            "octets": octets,
        }
        guilde = c.get("guild")
        if guilde:
            g = serveurs.setdefault(
                str(guilde.get("id")),
                {"id": str(guilde.get("id")), "nom": guilde.get("name") or "serveur", "salons": []},
            )
            g["salons"].append(entree)
        else:
            etiquette = index.get(entree["id"], "")
            for prefixe in ("Direct Message with ", "Message direct avec "):
                if etiquette.startswith(prefixe):
                    etiquette = etiquette[len(prefixe):]
            entree["nom"] = etiquette or c.get("name") or ", ".join(
                str(r) for r in (c.get("recipients") or [])
            ) or "conversation privée"
            entree["groupe"] = (c.get("type") or "").upper().endswith("GROUP_DM")
            mp.append(entree)

    for g in serveurs.values():
        g["salons"].sort(key=lambda s: s["nom"].lower())
    return {
        "serveurs": sorted(serveurs.values(), key=lambda g: g["nom"].lower()),
        "mp": sorted(mp, key=lambda s: -s["octets"]),
    }

def messages_de(paquet, salon):
    messages = dossier_messages(os.path.join(PAQUETS, paquet))
    fichier = os.path.join(messages, "c" + salon, "messages.json")
    if not os.path.isfile(fichier):
        raise FileNotFoundError("salon absent du paquet : " + salon)
    return lire_json(fichier)

def en_ms(horodatage):
    t = horodatage.replace("Z", "+00:00")
    try:
        d = datetime.fromisoformat(t)
    except ValueError:
        d = datetime.strptime(t[:19], "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc)
    if d.tzinfo is None:
        d = d.replace(tzinfo=timezone.utc)
    return int(d.timestamp() * 1000)

def apercu(paquet, salon):
    m = messages_de(paquet, salon)
    if not m:
        return {"messages": 0}
    dates = [en_ms(x["Timestamp"]) for x in m if x.get("Timestamp")]
    pieces = sum(1 for x in m if (x.get("Attachments") or "").strip())
    return {
        "messages": len(m),
        "pieces": pieces,
        "debut": min(dates) if dates else None,
        "fin": max(dates) if dates else None,
    }

taches = {}
verrou = threading.Lock()

def rendu(texte, emojis):
    if not texte:
        return "", None
    corps = EMOJI_RX.sub(lambda m: f":{m.group(2)}:", texte)
    connus = [m for m in EMOJI_RX.finditer(texte) if m.group(3) in emojis]
    if not connus:
        return corps, None
    echappe = texte.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    html = re.sub(
        r"&lt;(a?):(\w+):(\d+)&gt;",
        lambda m: (
            f'<img data-mx-emoticon src="{emojis[m.group(3)]}" alt=":{m.group(2)}:" '
            f'title=":{m.group(2)}:" height="32" />'
            if m.group(3) in emojis else f":{m.group(2)}:"
        ),
        echappe,
    )
    return corps, html

def type_media(ctype):
    if ctype.startswith("image/"):
        return "m.image"
    if ctype.startswith("video/"):
        return "m.video"
    if ctype.startswith("audio/"):
        return "m.audio"
    return "m.file"

def importer(tache, options):
    etat = taches[tache]
    try:
        paquet = options["paquet"]
        mxid = options["auteur"]
        salle = options["salon"]
        medias = bool(options.get("medias", True))
        du = options.get("du")
        au = options.get("au")

        messages = []
        for salon in options["conversations"]:
            for m in messages_de(paquet, salon):
                if not m.get("Timestamp"):
                    continue
                ts = en_ms(m["Timestamp"])
                if du and ts < du:
                    continue
                if au and ts > au:
                    continue
                m["_ts"] = ts
                messages.append(m)
        messages.sort(key=lambda x: (x["_ts"], str(x.get("ID"))))
        etat.update(total=len(messages), phase="import")

        emojis = {}
        t0 = time.time()
        for i, m in enumerate(messages):
            if etat.get("arret"):
                etat.update(phase="arrêté")
                return
            corps, html = rendu(m.get("Contents") or "", emojis)
            if corps.strip():
                contenu = {"msgtype": "m.text", "body": corps}
                if html:
                    contenu.update({"format": "org.matrix.custom.html", "formatted_body": html})
                envoyer(mxid, salle, m["_ts"], contenu)
                etat["evenements"] += 1
            if medias:
                for lien in (m.get("Attachments") or "").split():
                    try:
                        donnees, ctype = telecharger(lien)
                        nom = urllib.parse.urlparse(lien).path.rsplit("/", 1)[-1] or "fichier"
                        mxc = televerser(mxid, donnees, ctype, nom)
                        envoyer(mxid, salle, m["_ts"], {
                            "msgtype": type_media(ctype), "body": nom, "url": mxc,
                            "info": {"mimetype": ctype, "size": len(donnees)},
                        })
                        etat["evenements"] += 1
                    except Exception as e:
                        etat["medias_rates"] += 1
                        etat["dernier"] = f"média refusé : {type(e).__name__}"
            etat["faits"] = i + 1
            etat["vitesse"] = round(etat["evenements"] / max(time.time() - t0, 1e-9), 1)
        etat.update(phase="fini")
    except Exception as e:
        etat.update(phase="échec", erreur=f"{type(e).__name__}: {e}")

def envoyer(mxid, salle, ts, contenu):
    global _numero
    _numero += 1
    chemin = (
        f"/_matrix/client/v3/rooms/{urllib.parse.quote(salle)}"
        f"/send/m.room.message/velvet{int(time.time() * 1000)}_{_numero}?ts={ts}"
    )
    _req("PUT", url_as(chemin, mxid), corps=contenu)

_numero = 0

def telecharger(url):
    requete = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(requete, timeout=120) as r:
        return r.read(), r.headers.get("Content-Type", "application/octet-stream")

def televerser(mxid, donnees, ctype, nom):
    chemin = f"/_matrix/media/v3/upload?filename={urllib.parse.quote(nom)}"
    return _req("POST", url_as(chemin, mxid), corps=donnees,
                entetes={"Content-Type": ctype}, brut=True, delai=300)["content_uri"]

class Poignee(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _repondre(self, code, objet):
        corps = json.dumps(objet, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(corps)))
        self.end_headers()
        self.wfile.write(corps)

    def _autorise(self):
        entete = self.headers.get("Authorization", "")
        jeton = entete[7:] if entete.startswith("Bearer ") else ""
        qui = qui_est(jeton) if jeton else None
        if not qui:
            self._repondre(403, {"erreur": "jeton refusé"})
            return None
        return qui

    def log_message(self, format, *args):  # noqa: A002 — signature imposée
        print("[http] " + format % args, flush=True)

    def do_GET(self):  # noqa: N802 — signature imposée
        qui = self._autorise()
        if not qui:
            return
        url = urllib.parse.urlparse(self.path)
        p = urllib.parse.parse_qs(url.query)
        try:
            if url.path == "/paquets":
                self._repondre(200, {"paquets": paquets(qui)})
            elif url.path == "/arbre":
                if not accessible(p["paquet"][0], qui):
                    return self._repondre(403, {"erreur": "ce paquet n'est pas le vôtre"})
                self._repondre(200, arbre(p["paquet"][0]))
            elif url.path == "/apercu":
                if not accessible(p["paquet"][0], qui):
                    return self._repondre(403, {"erreur": "ce paquet n'est pas le vôtre"})
                self._repondre(200, apercu(p["paquet"][0], p["salon"][0]))
            elif url.path == "/etat":
                etat = taches.get(p["tache"][0], {"phase": "inconnue"})
                if etat.get("demande_par") not in (None, qui):
                    return self._repondre(403, {"erreur": "tâche d'une autre personne"})
                self._repondre(200, etat)
            elif url.path == "/sante":
                self._repondre(200, {"ok": True, "paquets": len(paquets(qui))})
            else:
                self._repondre(404, {"erreur": "chemin inconnu"})
        except Exception as e:  # noqa: BLE001
            self._repondre(500, {"erreur": f"{type(e).__name__}: {e}"})

    def do_POST(self):  # noqa: N802
        qui = self._autorise()
        if not qui:
            return
        taille = int(self.headers.get("Content-Length", "0") or 0)
        corps = json.loads(self.rfile.read(taille) or b"{}")
        url = urllib.parse.urlparse(self.path)
        try:
            if url.path == "/import":
                paquet = corps.get("paquet", "")
                if not accessible(paquet, qui):
                    return self._repondre(403, {"erreur": "ce paquet n'est pas le vôtre"})
                corps["auteur"] = proprietaire_de(paquet) or qui
                jeton = self.headers.get("Authorization", "")[7:]
                if not membre_du_salon(jeton, corps.get("salon", ""), qui):
                    return self._repondre(403, {"erreur": "vous n'êtes pas dans ce salon"})
                tache = f"t{int(time.time() * 1000)}"
                with verrou:
                    taches[tache] = {
                        "phase": "lecture", "faits": 0, "total": 0, "evenements": 0,
                        "medias_rates": 0, "vitesse": 0.0, "salon": corps.get("salon"),
                        "demande_par": qui,
                    }
                threading.Thread(target=importer, args=(tache, corps), daemon=True).start()
                self._repondre(200, {"tache": tache})
            elif url.path == "/arreter":
                etat = taches.get(corps.get("tache"))
                if etat and etat.get("demande_par") not in (None, qui):
                    return self._repondre(403, {"erreur": "tâche d'une autre personne"})
                if etat:
                    etat["arret"] = True
                self._repondre(200, {"ok": etat is not None})
            else:
                self._repondre(404, {"erreur": "chemin inconnu"})
        except Exception as e:  # noqa: BLE001
            self._repondre(500, {"erreur": f"{type(e).__name__}: {e}"})

class Serveur(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

if __name__ == "__main__":
    print(f"[import] paquets={PAQUETS} port={PORT} admins={sorted(ADMINS) or 'tous'}", flush=True)
    Serveur(("0.0.0.0", PORT), Poignee).serve_forever()
