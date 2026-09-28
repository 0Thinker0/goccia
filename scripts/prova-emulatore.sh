#!/bin/bash
# Prova di Goccia sull'emulatore: installa l'APK, attraversa le schermate principali
# e salva screenshot, errori e log in una cartella.
#
#   bash scripts/prova-emulatore.sh percorso/app.apk /tmp/prova

APK="$1"
USCITA="${2:-/tmp/prova}"
QUI="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$USCITA"
PASSI="$USCITA/passi.txt"
: > "$PASSI"

n=0
foto() {
  python3 "$QUI/tocca.py" --solo-dialoghi >> "$PASSI" 2>&1
  n=$((n + 1))
  local nome
  nome=$(printf "%02d-%s" "$n" "$1")
  sleep "${2:-2}"
  adb exec-out screencap -p > "$USCITA/$nome.png"
  echo "foto $nome" >> "$PASSI"
}
tocca() {
  if python3 "$QUI/tocca.py" "$@" >> "$PASSI" 2>&1; then return 0; fi
  echo "!! non trovato: $*" >> "$PASSI"
  return 1
}
indietro() { adb shell input keyevent KEYCODE_BACK; sleep 1.5; }
scorri() { adb shell input swipe 540 1900 540 700 500; sleep 1.5; }
scrivi() { adb shell input text "$1"; sleep 1.5; }

adb logcat -c
# l'emulatore appena avviato e lento: lasciamo stabilizzare il launcher
adb shell input keyevent KEYCODE_HOME
sleep 15
python3 "$QUI/tocca.py" --esiste "Wait" >> "$PASSI" 2>&1 && python3 "$QUI/tocca.py" "Wait" >> "$PASSI" 2>&1
adb install -r -g "$APK" >> "$PASSI" 2>&1
# posizione finta: piazza Maggiore a Bologna
adb emu geo fix 11.3426 44.4938
adb shell am start -n it.goccia.app/.MainActivity >> "$PASSI" 2>&1
sleep 3
adb emu geo fix 11.3426 44.4938

foto benvenuto 5
tocca "Iniziamo"
foto permessi
tocca "Continua"
foto auto
tocca "Gasolio"
tocca "Continua"
foto luoghi
tocca "Salva la posizione attuale come Casa"
foto luoghi-casa 6
tocca "Fatto, andiamo!"
foto home 25
scorri
foto home-2
scorri
foto home-3
scorri
foto home-4

tocca --descrizione "Mappa" || tocca "Mappa"
foto mappa 15
tocca --descrizione "Vedi come lista"
foto lista 5
tocca "cent vs media" || tocca "Nella media"
foto dettaglio 6
scorri
foto dettaglio-2 4
tocca "Ho fatto il pieno qui"
foto rifornimento 3
tocca "50 €"
foto rifornimento-50
tocca "Salva rifornimento"
foto salvato 3
tocca "Fatto"
sleep 2
indietro
indietro

tocca "Viaggio"
foto viaggio 3
tocca "Dove vai?"
tocca "Cerca un comune"
scrivi "Roma"
foto viaggio-cerca 3
tocca "Roma (RM)"
foto viaggio-pronto
tocca "Calcola soste"
foto viaggio-risultato 30
adb shell input swipe 540 2000 540 1300 500
foto viaggio-risultato-2 2
indietro

tocca "Preferiti"
foto preferiti 3
tocca "Avvisi"
foto avvisi 2
tocca "Crea il primo avviso" || tocca "Nuovo avviso"
foto nuovo-avviso 8
tocca "Crea avviso"
foto avvisi-dopo 4

tocca "Profilo"
foto profilo 3
tocca --descrizione "Impostazioni"
foto impostazioni 2
scorri
foto impostazioni-2

# la Home dopo un rifornimento
tocca "Home"
foto home-finale 4

adb logcat -d -b crash > "$USCITA/crash.txt" 2>&1
adb logcat -d -v time > "$USCITA/logcat-completo.txt" 2>&1
grep -E "it\.goccia|AndroidRuntime|FATAL|MapLibre|mbgl|W/System.err" "$USCITA/logcat-completo.txt" | tail -n 400 > "$USCITA/logcat.txt"
rm -f "$USCITA/logcat-completo.txt"

# immagini piu leggere da consultare
if command -v convert >/dev/null 2>&1; then
  for f in "$USCITA"/*.png; do
    convert "$f" -resize 45% -quality 82 "${f%.png}.jpg" && rm "$f"
  done
fi
echo "fine" >> "$PASSI"
exit 0
