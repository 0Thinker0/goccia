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
# tocca.py salva qui cosa c'era sullo schermo quando non trova qualcosa
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
# come esiste, ma guarda lo schermo una volta sola
subito() { python3 "$QUI/tocca.py" --esiste --subito "$1" > /dev/null 2>&1; }
indietro() { adb shell input keyevent KEYCODE_BACK; sleep 1.5; }
scorri() { adb shell input swipe 540 1900 540 700 500; sleep 1.5; }
scrivi() { adb shell input text "$1"; sleep 1.5; }
nota() { echo "== $*" >> "$PASSI"; }
# scorre la schermata finche il testo non compare, poi lo tocca
trova() {
  for _ in 1 2 3 4 5; do subito "$1" && break; scorri; done
  tocca "$1"
}
# conferma la richiesta di aggiungere un widget, qualunque sia il testo del launcher
conferma_widget() {
  sleep 3
  for t in "Add to home screen" "Add automatically" "ADD AUTOMATICALLY" "Aggiungi a schermata Home" "Aggiungi automaticamente"; do
    if subito "$t"; then tocca "$t"; return 0; fi
  done
  echo "!! nessun pulsante per aggiungere il widget" >> "$PASSI"
  python3 "$QUI/tocca.py" --elenco >> "$PASSI" 2>&1
  return 1
}

adb logcat -c
# l'emulatore appena avviato e lento: lasciamo stabilizzare il launcher
adb shell input keyevent KEYCODE_HOME
sleep 15
python3 "$QUI/tocca.py" --esiste "Wait" >> "$PASSI" 2>&1 && python3 "$QUI/tocca.py" "Wait" >> "$PASSI" 2>&1

# posizione finta: piazza Maggiore a Bologna, ripetuta ogni due secondi perche il GPS
# dell'emulatore la comunica solo quando la mandiamo
adb shell cmd location set-location-enabled true >> "$PASSI" 2>&1
nota "localizzazione accesa: $(adb shell cmd location is-location-enabled 2>&1)"
nota "geo fix: $(adb emu geo fix 11.3426 44.4938 2>&1 | tr '\n' ' ')"
( while true; do adb emu geo fix 11.3426 44.4938 > /dev/null 2>&1; sleep 2; done ) &
GEO=$!

adb install -r -g "$APK" >> "$PASSI" 2>&1
adb shell am start -n it.goccia.app/.MainActivity >> "$PASSI" 2>&1
sleep 3

nota "introduzione"
foto benvenuto 5
tocca "Iniziamo"
foto permessi
tocca "Continua"
foto auto
tocca "Gasolio"
tocca "Continua"
foto luoghi
tocca "Salva la posizione attuale come Casa"
foto luoghi-casa 10
tocca "Fatto, andiamo!"

nota "home"
foto home 25
scorri
foto home-2
scorri
foto home-3
scorri
foto home-4

nota "mappa e lista"
tocca "Mappa"
foto mappa 15
tocca --descrizione "Vedi come lista"
foto lista 5

nota "dettaglio e rifornimento"
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
# torniamo alle schede, da qualunque punto siamo arrivati
for _ in 1 2 3; do esiste "Viaggio" && break; indietro; done

nota "viaggio"
tocca "Viaggio"
foto viaggio 3
tocca "Dove vai?"
sleep 2
# il campo si apre gia pronto per scrivere; lo tocchiamo comunque per sicurezza
tocca "Cerca un comune"
scrivi "Roma"
foto viaggio-cerca 3
tocca "Roma (RM)"
foto viaggio-pronto
tocca "Calcola soste"
foto viaggio-risultato 30
adb shell input swipe 540 2000 540 1300 500
foto viaggio-risultato-2 2

nota "modalita autostrada"
trova "Modalità autostrada"
foto autostrada 10
# simuliamo il viaggio: i punti del percorso e della sosta consigliata li scrive l'app nel log
kill "$GEO" 2> /dev/null
posiziona() { for _ in 1 2 3 4 5; do adb emu geo fix "$1" "$2" > /dev/null 2>&1; sleep 2; done; }
coordinate() { echo "$1" | sed -n "s/.*$2 = \([-0-9.]*\),\([-0-9.]*\).*/\2 \1/p" | head -n 1; }
PERCORSO=$(adb logcat -d -s Goccia:I | grep "guida: percorso" | tail -n 1)
nota "log: ${PERCORSO#*Goccia}"
C=$(coordinate "$PERCORSO" "km 60")
if [ -n "$C" ]; then
  posiziona $C
  foto autostrada-km60 4
fi
SOSTA=$(adb logcat -d -s Goccia:I | grep "guida: consigliata" | tail -n 1)
nota "log: ${SOSTA#*Goccia}"
C=$(coordinate "$SOSTA" "8 km prima")
if [ -n "$C" ]; then
  posiziona $C
  foto autostrada-preavviso 3
  adb shell cmd statusbar expand-notifications
  foto autostrada-notifiche 3
  adb shell cmd statusbar collapse
  sleep 1
fi
C=$(echo "$SOSTA" | sed -n 's/.*al km [0-9]* = \([-0-9.]*\),\([-0-9.]*\).*/\2 \1/p')
if [ -n "$C" ]; then
  # fermi al distributore per quasi due minuti: l'app chiede se abbiamo fatto il pieno
  for _ in $(seq 1 26); do adb emu geo fix $C > /dev/null 2>&1; sleep 4; done
  foto autostrada-domanda 2
  tocca "Sì, il pieno"
  foto autostrada-pieno 3
fi
trova "Termina la modalità autostrada"
sleep 3
( while true; do adb emu geo fix 11.3426 44.4938 > /dev/null 2>&1; sleep 2; done ) &
GEO=$!
foto autostrada-fine 3

indietro
for _ in 1 2 3; do esiste "Preferiti" && break; indietro; done

nota "preferiti e avvisi"
tocca "Preferiti"
foto preferiti 3
tocca "Avvisi"
foto avvisi 2
tocca "Crea il primo avviso" || tocca "Nuovo avviso"
foto nuovo-avviso 8
tocca "Crea avviso"
foto avvisi-dopo 4
for _ in 1 2 3; do esiste "Profilo" && break; indietro; done

nota "profilo e impostazioni"
tocca "Profilo"
foto profilo 3
tocca --descrizione "Impostazioni"
foto impostazioni 2
scorri
foto impostazioni-2

nota "widget"
trova "Prezzi vicino a te"
foto widget-richiesta 3
conferma_widget
sleep 2
trova "La tua auto"
conferma_widget
sleep 2
adb shell input keyevent KEYCODE_HOME
foto widget-home 8
adb shell am start -n it.goccia.app/.MainActivity >> "$PASSI" 2>&1
sleep 3
for _ in 1 2 3; do esiste "Home" && break; indietro; done

nota "home dopo il rifornimento"
tocca "Home"
foto home-finale 4

nota "auto elettrica: nuova auto"
tocca "Profilo"
sleep 2
tocca --descrizione "Aggiungi auto"
foto ev-nuova-auto 3
tocca "Elettrica"
foto ev-auto-elettrica 2
tocca "Salva"
sleep 3
for _ in 1 2 3; do esiste "Home" && break; indietro; done

nota "auto elettrica: home"
tocca "Home"
foto ev-home 15
scorri
foto ev-home-2
scorri
foto ev-home-3

nota "auto elettrica: colonnine"
tocca "Mappa"
foto ev-mappa 15
tocca "Dettagli e costi"
foto ev-colonnina 5
scorri
foto ev-colonnina-2 2
scorri
foto ev-colonnina-3 2
indietro
for _ in 1 2 3; do esiste "Viaggio" && break; indietro; done

nota "auto elettrica: viaggio"
tocca "Viaggio"
foto ev-viaggio 3
tocca "Calcola soste"
foto ev-viaggio-risultato 40
adb shell input swipe 540 2000 540 1100 500
foto ev-viaggio-risultato-2 2
adb shell input swipe 540 2000 540 1100 500
foto ev-viaggio-risultato-3 2
trova "Modalità autostrada"
foto ev-autostrada 10
trova "Termina la modalità autostrada"
sleep 3
indietro
for _ in 1 2 3; do esiste "Profilo" && break; indietro; done

nota "auto elettrica: tariffa di casa"
tocca "Profilo"
tocca --descrizione "Impostazioni"
tocca "Tariffa di casa"
foto ev-tariffa 3
scorri
foto ev-tariffa-2
scorri
foto ev-tariffa-3

nota "widget con l'auto elettrica"
adb shell input keyevent KEYCODE_HOME
foto widget-home-ev 8

kill "$GEO" 2> /dev/null

adb logcat -d -b crash > "$USCITA/crash.txt" 2>&1
adb logcat -d -v time > "$USCITA/logcat-completo.txt" 2>&1
# senza il rumore di uiautomator, che parte a ogni tocco
grep -vE "uiautomator|RuntimeInit uid 2000|Using default boot image|Leaving lock profiling|Calling main entry|Shutting down VM" "$USCITA/logcat-completo.txt" \
  | grep -iE "goccia|AndroidRuntime|FATAL|maplibre|mbgl|System.err|glance|appwidget" \
  | tail -n 600 > "$USCITA/logcat.txt"
rm -f "$USCITA/logcat-completo.txt"

echo "fine" >> "$PASSI"
exit 0
