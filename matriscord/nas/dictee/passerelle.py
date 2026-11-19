#!/usr/bin/env python3
import asyncio
import collections
import io
import json
import logging
import re
import time
import urllib.error
import urllib.request
import wave

import numpy as np
import os
import websockets
from faster_whisper import decode_audio
from faster_whisper.vad import VadOptions, get_speech_timestamps

HOTE = os.environ.get("VELVET_HOTE", "0.0.0.0")
PORT = int(os.environ.get("VELVET_PORT", "8179"))
WHISPER = "http://127.0.0.1:8178/inference"
SYNAPSE_BASE = os.environ.get("VELVET_SYNAPSE", "http://127.0.0.1:8008")
SYNAPSE_WHOAMI = f"{SYNAPSE_BASE}/_matrix/client/v3/account/whoami"
SR = 16000
PAS = 1.0
FENETRE_MAX = 9.5
GARDE = 3.0
TRANCHE = 25
DUREE_MAX = 30 * 60

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
journal = logging.getLogger("dictee")

verrou_whisper = asyncio.Lock()

def norme(mot):
    return re.sub(r"[^\w]", "", mot.lower())

def parole(pcm):
    audio = np.frombuffer(pcm, dtype=np.int16).astype(np.float32) / 32768.0
    return get_speech_timestamps(audio, VadOptions(
        threshold=0.6, min_speech_duration_ms=300, min_silence_duration_ms=300, speech_pad_ms=200))

def seulement_parole(pcm):
    zones = parole(pcm)
    blanc = bytes(int(0.3 * SR) * 2)
    return blanc.join(pcm[z["start"] * 2:z["end"] * 2] for z in zones)

FANTOMES = re.compile(
    r"(sous-titr\w*[^.!?]*(amara(.org)?|st' ?501|communauté|radio-canada)[^.!?]*[.!?]?"
    r"|merci d'avoir regardé[^.!?]*[.!?]?|abonnez-vous[^.!?]*[.!?]?|merci de votre attention[.!?]?"
    r"|\[musique\]|\(musique\)|\[rires?\]|♪+)",
    re.IGNORECASE,
)

def nettoyer(texte):
    return " ".join(FANTOMES.sub(" ", texte).split())

def pcm_du_fichier(octets):
    audio = decode_audio(io.BytesIO(octets), sampling_rate=SR)[: DUREE_MAX * SR]
    return (np.clip(audio, -1, 1) * 32767).astype(np.int16).tobytes()

async def transcrire_vocal(ws):
    try:
        octets = await asyncio.wait_for(ws.recv(), timeout=60)
        if not isinstance(octets, bytes):
            raise ValueError("fichier attendu")
        pcm = await asyncio.to_thread(pcm_du_fichier, octets)
        texte = ""
        if await asyncio.to_thread(parole, pcm):
            async with verrou_whisper:
                texte = await asyncio.to_thread(texte_complet, pcm)
        await ws.send(json.dumps({"type": "final", "texte": texte}))
        await ws.close(1000, "fin")
    except websockets.ConnectionClosed:
        pass
    except Exception as e:
        journal.warning("transcription de vocal en échec : %s", e)
        await ws.send(json.dumps({"type": "erreur", "message": "transcription impossible"}))
        await ws.close(1011, "echec")

SESSIONS_PAR_PERSONNE = 2
SESSIONS_TOTAL = 4
_sessions = collections.Counter()

def verifier_jeton(jeton):
    requete = urllib.request.Request(SYNAPSE_WHOAMI, headers={"Authorization": f"Bearer {jeton}"})
    try:
        with urllib.request.urlopen(requete, timeout=10) as r:
            return json.loads(r.read()).get("user_id")
    except urllib.error.HTTPError:
        return None
    except (urllib.error.URLError, OSError, ValueError) as e:
        journal.error("Synapse injoignable (%s) : %s — toute dictée sera refusée", SYNAPSE_WHOAMI, e)
        return None

def inference(pcm, contexte, format_reponse):
    tampon = io.BytesIO()
    x = wave.open(tampon, "wb")
    x.setnchannels(1)
    x.setsampwidth(2)
    x.setframerate(SR)
    x.writeframes(pcm)
    x.close()
    frontiere = "----velvet"
    corps = (
        f"--{frontiere}\r\nContent-Disposition: form-data; name=\"response_format\"\r\n\r\n{format_reponse}\r\n"
        f"--{frontiere}\r\nContent-Disposition: form-data; name=\"temperature\"\r\n\r\n0\r\n"
        f"--{frontiere}\r\nContent-Disposition: form-data; name=\"audio_ctx\"\r\n\r\n{contexte}\r\n"
        f"--{frontiere}\r\nContent-Disposition: form-data; name=\"suppress_non_speech\"\r\n\r\ntrue\r\n"
        f"--{frontiere}\r\nContent-Disposition: form-data; name=\"no_speech_thold\"\r\n\r\n0.6\r\n"
        f"--{frontiere}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.wav\"\r\n"
        f"Content-Type: audio/wav\r\n\r\n"
    ).encode() + tampon.getvalue() + f"\r\n--{frontiere}--\r\n".encode()
    requete = urllib.request.Request(WHISPER, corps, {"Content-Type": f"multipart/form-data; boundary={frontiere}"})
    with urllib.request.urlopen(requete, timeout=300) as r:
        return json.loads(r.read())

def texte_complet(pcm):
    voix = seulement_parole(pcm)
    if not voix:
        return ""
    return nettoyer(inference(voix, 0, "json").get("text", ""))

def transcrire(pcm, contexte=512):
    reponse = inference(pcm, contexte, "verbose_json")
    mots = []
    for segment in reponse.get("segments", []):
        for m in segment.get("words") or []:
            brut = m.get("word", "")
            texte = brut.strip()
            if not texte or not nettoyer(texte):
                continue
            if mots and (not brut.startswith(" ") or not norme(texte)):
                precedent = mots[-1]
                mots[-1] = (precedent[0] + texte, precedent[1], float(m["end"]))
                continue
            if not norme(texte):
                continue
            mots.append((texte, float(m["start"]), float(m["end"])))
    return mots

class Dictee:

    def __init__(self):
        self.pcm = bytearray()
        self.debut_tampon = 0.0
        self.valides = []
        self.precedente = []
        self.provisoire = []
        self.debut_complet = 0.0
        self.complets = []

    @property
    def duree(self):
        return len(self.pcm) / 2 / SR

    def _retirer_repetition(self, hyp):
        for n in (3, 2, 1):
            if len(self.valides) >= n and len(hyp) >= n and hyp[n - 1][1] <= self.debut_tampon + 0.8 \
                    and [norme(m) for m, _, _ in hyp[:n]] == [norme(m) for m in self.valides[-n:]]:
                return hyp[n:]
        return hyp

    async def passage(self, final=False):
        fin = self.duree
        if fin - self.debut_tampon < (0.3 if final else 0.5):
            return False
        a, b = int(self.debut_tampon * SR) * 2, int(fin * SR) * 2
        zones = await asyncio.to_thread(parole, bytes(self.pcm[a:b]))
        if not zones:
            if not final and fin - self.debut_tampon > 1.5:
                self.debut_tampon = fin - 1.0
            self.precedente, self.provisoire = [], []
            return False
        debut_parole = zones[0]["start"] / SR
        if debut_parole > 0.5:
            self.debut_tampon += debut_parole - 0.2
            a = int(self.debut_tampon * SR) * 2
        async with verrou_whisper:
            relatifs = await asyncio.to_thread(transcrire, bytes(self.pcm[a:b]))
        hyp = self._retirer_repetition([(m, self.debut_tampon + d, self.debut_tampon + f) for m, d, f in relatifs])
        if final:
            self.valides.extend(m for m, _, _ in hyp)
            self.provisoire = []
            return True
        k = 0
        while k < min(len(hyp), len(self.precedente)) and norme(hyp[k][0]) == norme(self.precedente[k][0]):
            k += 1
        a_valider = hyp[:k]
        if not a_valider and fin - self.debut_tampon >= FENETRE_MAX:
            a_valider = [mot for mot in hyp if mot[2] <= fin - GARDE]
        if a_valider:
            self.valides.extend(m for m, _, _ in a_valider)
            self.debut_tampon = max(self.debut_tampon, a_valider[-1][2])
        self.precedente = hyp[len(a_valider):]
        self.provisoire = self.precedente
        return True

    async def tranche_complete(self):
        if self.duree - self.debut_complet < TRANCHE:
            return
        a = int(self.debut_complet * SR) * 2
        morceau = bytes(self.pcm[a:a + TRANCHE * SR * 2])
        zones = await asyncio.to_thread(parole, morceau)
        coupe, plus_long = TRANCHE * SR, 0
        for z1, z2 in zip(zones, zones[1:]):
            if z2["start"] >= TRANCHE * SR / 2 and z2["start"] - z1["end"] > plus_long:
                coupe, plus_long = (z1["end"] + z2["start"]) // 2, z2["start"] - z1["end"]
        if zones:
            async with verrou_whisper:
                texte = await asyncio.to_thread(texte_complet, morceau[:coupe * 2])
            if texte:
                self.complets.append(texte)
        self.debut_complet += coupe / SR

    async def texte_final(self):
        a = int(self.debut_complet * SR) * 2
        reste = bytes(self.pcm[a:])
        if len(reste) >= 0.3 * SR * 2 and await asyncio.to_thread(parole, reste):
            async with verrou_whisper:
                self.complets.append(await asyncio.to_thread(texte_complet, reste))
        return " ".join(t for t in self.complets if t)

    def etat(self):
        return {
            "type": "partiel",
            "valide": " ".join(self.valides),
            "provisoire": " ".join(m for m, _, _ in self.provisoire),
        }

async def session(ws):
    porteur = {}
    try:
        await _session(ws, porteur)
    finally:
        qui = porteur.get("utilisateur")
        if qui:
            _sessions[qui] -= 1
            if _sessions[qui] <= 0:
                del _sessions[qui]

async def _session(ws, porteur):
    if ws.request.path != "/dictee":
        await ws.close(4404, "chemin inconnu")
        return
    try:
        premier = json.loads(await asyncio.wait_for(ws.recv(), timeout=10))
    except (asyncio.TimeoutError, ValueError, TypeError):
        await ws.send(json.dumps({"type": "erreur", "message": "premier message attendu : debut + jeton"}))
        await ws.close(4400, "debut attendu")
        return
    utilisateur = await asyncio.to_thread(verifier_jeton, premier.get("jeton", "")) \
        if premier.get("type") in ("debut", "transcrire") else None
    if not utilisateur:
        await ws.send(json.dumps({"type": "erreur", "message": "jeton refusé"}))
        await ws.close(4401, "jeton refusé")
        return

    if sum(_sessions.values()) >= SESSIONS_TOTAL or _sessions[utilisateur] >= SESSIONS_PAR_PERSONNE:
        journal.warning("plafond atteint, dictée refusée pour %s (%d en cours)",
                        utilisateur, sum(_sessions.values()))
        await ws.send(json.dumps({"type": "erreur", "message": "trop de dictées en cours"}))
        await ws.close(4429, "trop de dictées en cours")
        return
    _sessions[utilisateur] += 1
    porteur["utilisateur"] = utilisateur

    await ws.send(json.dumps({"type": "pret", "utilisateur": utilisateur}))
    if premier["type"] == "transcrire":
        journal.info("vocal à transcrire pour %s", utilisateur)
        await transcrire_vocal(ws)
        return
    journal.info("dictée ouverte pour %s", utilisateur)
    dictee = Dictee()
    termine = asyncio.Event()

    async def boucle():
        while not termine.is_set():
            debut = time.monotonic()
            try:
                if await dictee.passage():
                    await ws.send(json.dumps(dictee.etat()))
                await dictee.tranche_complete()
            except (urllib.error.URLError, OSError) as e:
                journal.warning("passage Whisper en échec : %s", e)
                await ws.send(json.dumps({"type": "erreur", "message": "transcription indisponible"}))
            await asyncio.sleep(max(0.0, PAS - (time.monotonic() - debut)))

    tache = asyncio.create_task(boucle())
    try:
        async for message in ws:
            if isinstance(message, bytes):
                dictee.pcm.extend(message)
                if dictee.duree > DUREE_MAX:
                    await ws.send(json.dumps({"type": "erreur", "message": "dictée trop longue (30 min)"}))
                    break
            elif json.loads(message).get("type") == "fin":
                break
    except websockets.ConnectionClosed:
        pass
    finally:
        termine.set()
        await tache
    try:
        await ws.send(json.dumps({"type": "finalisation"}))
        try:
            texte = await dictee.texte_final()
        except (urllib.error.URLError, OSError) as e:
            journal.warning("passe complète en échec, texte du direct gardé : %s", e)
            await dictee.passage(final=True)
            texte = " ".join(dictee.valides)
        await ws.send(json.dumps({"type": "final", "texte": texte}))
        await ws.close(1000, "fin")
    except websockets.ConnectionClosed:
        pass
    journal.info("dictée fermée pour %s : %.0f s", utilisateur, dictee.duree)

async def principal():
    async with websockets.serve(session, HOTE, PORT, max_size=32 * 2 ** 20):
        journal.info("passerelle de dictée à l'écoute sur ws://%s:%d/dictee", HOTE, PORT)
        await asyncio.Future()

if __name__ == "__main__":
    asyncio.run(principal())
