#!/usr/bin/env python3
"""Tocca sull'emulatore l'elemento con un certo testo (o descrizione), usando uiautomator.

    python3 tocca.py "Iniziamo"            testo esatto, altrimenti contenuto
    python3 tocca.py --descrizione "Mappa"  solo content-desc
    python3 tocca.py --esiste "Roma"        esce con 0 se c'e, 1 se no (non tocca)
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def leggi():
    try:
        subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True, timeout=60)
        xml = subprocess.run(["adb", "shell", "cat", "/sdcard/ui.xml"], capture_output=True, text=True, timeout=30).stdout
    except subprocess.TimeoutExpired:
        return None
    if xml.strip().startswith("<?xml"):
        try:
            return ET.fromstring(xml)
        except ET.ParseError:
            return None
    return None


def chiudi_dialoghi_di_sistema(radice):
    """Sull'emulatore lento a volte compare "... isn't responding": tocchiamo "Wait"."""
    testi = [n.get("text", "") for n in radice.iter("node")]
    if not any("isn't responding" in t or "non risponde" in t for t in testi):
        return False
    for nodo in radice.iter("node"):
        if nodo.get("text", "") in ("Wait", "Attendi", "Close app", "Chiudi app"):
            x, y = centro(nodo)
            subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)], timeout=30)
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


def centro(nodo):
    b = [int(v) for v in re.findall(r"-?\d+", nodo.get("bounds", "[0,0][0,0]"))]
    return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2


def cerca(radice, testo, solo_descrizione=False):
    candidati = []
    for nodo in radice.iter("node"):
        valori = [nodo.get("content-desc", "")] if solo_descrizione else [nodo.get("text", ""), nodo.get("content-desc", "")]
        for v in valori:
            if not v:
                continue
            if v == testo:
                return nodo
            if testo.lower() in v.lower():
                candidati.append(nodo)
    return candidati[0] if candidati else None


def main():
    args = sys.argv[1:]
    if "--solo-dialoghi" in args:
        radice = leggi()
        if radice is not None:
            chiudi_dialoghi_di_sistema(radice)
        return 0
    solo_descrizione = "--descrizione" in args
    verifica = "--esiste" in args
    args = [a for a in args if not a.startswith("--")]
    testo = args[0]
    tentativi = 12
    for _ in range(tentativi):
        radice = schermo()
        nodo = cerca(radice, testo, solo_descrizione) if radice is not None else None
        if nodo is not None:
            if verifica:
                return 0
            x, y = centro(nodo)
            subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)], timeout=30)
            print(f"toccato '{testo}' in {x},{y}")
            return 0
        time.sleep(1.5)
    print(f"NON TROVATO: '{testo}'")
    return 1


if __name__ == "__main__":
    sys.exit(main())
