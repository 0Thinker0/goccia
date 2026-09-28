#!/bin/bash
# Giro breve dedicato alle mappe: prezzi attorno a te (anche dopo aver spostato la
# mappa), viaggio con le soste e colonnine. Serve a confrontare come l'emulatore
# disegna i segnaposto con le varie modalita di rendering.
#
#   bash scripts/prova-mappe.sh percorso/app.apk /tmp/prova

APK="$1"
USCITA="${2:-/tmp/prova}"
QUI="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$USCITA"
PASSI="$USCITA/passi.txt"
: > "$PASSI"
export PROVA_USCITA="$USCITA"

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
esiste() { python3 "$QUI/tocca.py" --esiste "$1" > /dev/null 2>&1; }
indietro() { adb shell input keyevent KEYCODE_BACK; sleep 1.5; }
nota() { echo "== $*" >> "$PASSI"; }
# un piccolo spostamento della mappa, per vedere se i segnaposto compaiono dopo un nuovo disegno
sposta() { adb shell input swipe 540 900 640 980 400; sleep 1; }

adb logcat -c
adb shell input keyevent KEYCODE_HOME
sleep 15
python3 "$QUI/tocca.py" --esiste "Wait" >> "$PASSI" 2>&1 && python3 "$QUI/tocca.py" "Wait" >> "$PASSI" 2>&1

adb shell cmd location set-location-enabled true >> "$PASSI" 2>&1
nota "geo fix: $(adb emu geo fix 11.3426 44.4938 2>&1 | tr '\n' ' ')"
( while true; do adb emu geo fix 11.3426 44.4938 > /dev/null 2>&1; sleep 2; done ) &
GEO=$!
nota "rendering: $(adb shell getprop ro.hardware.egl) $(adb shell getprop ro.boot.qemu.gles 2>/dev/null) $(adb shell dumpsys SurfaceFlinger 2>/dev/null | grep -m1 -iE 'GLES:' | tr -s ' ')"

adb install -r -g "$APK" >> "$PASSI" 2>&1
adb shell am start -n it.goccia.app/.MainActivity >> "$PASSI" 2>&1
sleep 3

nota "introduzione"
tocca "Iniziamo"
tocca "Continua"
tocca "Gasolio"
tocca "Continua"
tocca "Salva la posizione attuale come Casa"
sleep 10
tocca "Fatto, andiamo!"
sleep 20

nota "mappa dei prezzi"
tocca "Mappa"
foto mappa 15
sposta
foto mappa-spostata 8
foto mappa-dopo 10

nota "viaggio"
for _ in 1 2 3; do esiste "Viaggio" && break; indietro; done
tocca "Viaggio"
sleep 3
tocca "Dove vai?"
sleep 2
tocca "Cerca un comune"
adb shell input text "Roma"
sleep 3
tocca "Roma (RM)"
sleep 2
tocca "Calcola soste"
foto viaggio 30
adb shell input swipe 400 400 480 460 400
foto viaggio-spostata 8

nota "colonnine"
indietro
for _ in 1 2 3; do esiste "Mappa" && break; indietro; done
tocca "Mappa"
sleep 3
tocca "Colonnine"
foto colonnine 15
sposta
foto colonnine-spostata 8

kill "$GEO" 2> /dev/null
adb logcat -d -b crash > "$USCITA/crash.txt" 2>&1
adb logcat -d -v time > "$USCITA/logcat-completo.txt" 2>&1
grep -iE "goccia|AndroidRuntime|FATAL|maplibre|mbgl|System.err|EGL|gles|swiftshader|angle" "$USCITA/logcat-completo.txt" \
  | grep -vE "uiautomator|Mbgl-HttpRequest" | tail -n 800 > "$USCITA/logcat.txt"
rm -f "$USCITA/logcat-completo.txt"

echo "fine" >> "$PASSI"
exit 0
