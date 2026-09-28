#!/usr/bin/env python3
"""Tocca sull'emulatore l'elemento con un certo testo (o descrizione), usando uiautomator.

    python3 tocca.py "Iniziamo"            testo esatto, altrimenti contenuto
    python3 tocca.py --descrizione "Mappa"  solo content-desc
    python3 tocca.py --esiste "Roma"        esce con 0 se c'e (testo identico), 1 se no (non tocca)
    python3 tocca.py --solo-dialoghi        chiude solo gli eventuali dialoghi di sistema
    python3 tocca.py --elenco               scrive i testi visibili sullo schermo
    --subito                                un solo tentativo, senza aspettare che compaia

Se l'elemento non si trova, salva la schermata letta (xml e testi visibili) nella cartella
indicata da PROVA_USCITA, per capire cosa c'era al suo posto.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ultimo_errore = ""


def adb(*argomenti, timeout=30):
    return subprocess.run(["adb", *argomenti], capture_output=True, text=True, timeout=timeout)


def leggi():
    """Legge la gerarchia della schermata. None se uiautomator non ci riesce."""
    global ultimo_errore
    try:
        # senza il file vecchio non rischiamo di leggere la schermata precedente
        adb("shell", "rm", "-f", "/sdcard/ui.xml")
        esito = adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", timeout=60)
        messaggio = (esito.stdout + esito.stderr).strip()
        if "dumped" not in messaggio:
            ultimo_errore = messaggio or "uiautomator non ha risposto"
            return None
        xml = adb("shell", "cat", "/sdcard/ui.xml").stdout
    except subprocess.TimeoutExpired:
        ultimo_errore = "tempo scaduto"
        return None
    if not xml.strip().startswith("<?xml"):
        ultimo_errore = "file vuoto"
        return None
    try:
        return ET.fromstring(xml)
    except ET.ParseError as e:
        ultimo_errore = f"xml non valido: {e}"
        return None


def centro(nodo):
    b = [int(v) for v in re.findall(r"-?\d+", nodo.get("bounds", "[0,0][0,0]"))]
    return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2


def tap(x, y):
    subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)], timeout=30)


def chiudi_dialoghi_di_sistema(radice):
    """Sull'emulatore lento a volte compare "... isn't responding": tocchiamo "Wait"."""
    testi = [n.get("text", "") for n in radice.iter("node")]
    if not any("isn't responding" in t or "non risponde" in t for t in testi):
        return False
    for nodo in radice.iter("node"):
        if nodo.get("text", "") in ("Wait", "Attendi", "Close app", "Chiudi app"):
            x, y = centro(nodo)
            tap(x, y)
            print("chiuso un dialogo di sistema")
            time.sleep(1.5)
            return True
    return False


def schermo():
    for _ in range(4):
        radice = leggi()
        if radice is None:
            time.sleep(1)
            continue
        if chiudi_dialoghi_di_sistema(radice):
            continue
        return radice
    return None


def valori(nodo, solo_descrizione):
    if solo_descrizione:
        return [nodo.get("content-desc", "")]
    return [nodo.get("text", ""), nodo.get("content-desc", ""), nodo.get("hint", "")]


def cerca(radice, testo, solo_descrizione=False, esatto=False):
    """Il testo esatto vince; tra quelli che lo contengono, meglio un elemento toccabile
    (nei dialoghi di sistema il titolo spesso ripete il testo del pulsante). Con [esatto]
    conta solo il testo identico: "Home" non deve trovare "Widget per la schermata Home"."""
    candidati = []
    for nodo in radice.iter("node"):
        for v in valori(nodo, solo_descrizione):
            if not v:
                continue
            if v == testo:
                return nodo
            if not esatto and testo.lower() in v.lower():
                candidati.append(nodo)
    toccabili = [n for n in candidati if n.get("clickable") == "true"]
    return (toccabili or candidati or [None])[0]


def testi_visibili(radice):
    visti = []
    for nodo in radice.iter("node"):
        for v in (nodo.get("text", ""), nodo.get("content-desc", "")):
            v = " ".join(v.split())
            if v and v not in visti:
                visti.append(v)
    return visti


def salva_diagnosi(testo, radice):
    cartella = os.environ.get("PROVA_USCITA")
    if not cartella:
        return
    nome = re.sub(r"[^a-z0-9]+", "-", testo.lower()).strip("-")[:40] or "vuoto"
    percorso = os.path.join(cartella, f"nontrovato-{nome}")
    with open(percorso + ".txt", "w", encoding="utf-8") as f:
        if radice is None:
            f.write(f"schermata non leggibile: {ultimo_errore}\n")
        else:
            f.write("\n".join(testi_visibili(radice)) + "\n")
    if radice is not None:
        ET.ElementTree(radice).write(percorso + ".xml", encoding="utf-8")


def main():
    args = sys.argv[1:]
    if "--solo-dialoghi" in args:
        radice = leggi()
        if radice is not None:
            chiudi_dialoghi_di_sistema(radice)
        return 0
    if "--elenco" in args:
        radice = schermo()
        print("sullo schermo: " + (" | ".join(testi_visibili(radice)) if radice is not None else f"non leggibile ({ultimo_errore})"))
        return 0
    solo_descrizione = "--descrizione" in args
    verifica = "--esiste" in args
    tentativi = 1 if "--subito" in args else 12
    args = [a for a in args if not a.startswith("--")]
    testo = args[0]
    radice = None
    for _ in range(tentativi):
        radice = schermo()
        # le verifiche (usate per capire in che schermata siamo) vogliono il testo identico
        nodo = cerca(radice, testo, solo_descrizione, esatto=verifica) if radice is not None else None
        if nodo is not None:
            if verifica:
                return 0
            x, y = centro(nodo)
            tap(x, y)
            print(f"toccato '{testo}' in {x},{y}")
            return 0
        time.sleep(1.5)
    if verifica:
        return 1
    print(f"NON TROVATO: '{testo}'" + (f" (schermata non leggibile: {ultimo_errore})" if radice is None else ""))
    salva_diagnosi(testo, radice)
    return 1


if __name__ == "__main__":
    sys.exit(main())
