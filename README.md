# Goccia

App Android gratuita per trovare il distributore di carburante più conveniente in Italia: prezzi ufficiali in tempo reale, mappa, viaggi con la sosta consigliata, avvisi di prezzo e, per le auto elettriche, colonnine con i punti liberi. Niente account, niente pubblicità, niente abbonamenti.

Pagina del progetto, termini d'uso e informativa privacy: <https://orlandogabriele.com/projects/goccia.html>.

## Cosa fa

- **Home**: i distributori più convenienti vicino a te, autonomia stimata della tua auto, risparmio del mese e andamento dei prezzi in provincia.
- **Prezzi in tempo reale**: quelli in vigore adesso, letti dal servizio pubblico del Ministero, con l'ora dell'ultimo aggiornamento di ogni distributore. Chi non comunica i prezzi da oltre un mese non compare.
- **Mappa e lista**: segnaposto con bandiera e prezzo (verdi i convenienti, blu quelli nella media o sopra, bianchi i prezzi vecchi); si cerca vicino a te, a un luogo salvato o in qualunque zona con «Cerca in quest'area»; lista ordinabile per prezzo, distanza o convenienza reale.
- **Viaggio**: calcola il percorso e dice dove e quanto fare rifornimento (o ricaricare) in base all'auto e al livello di partenza, confrontando le aree di servizio con i distributori a pochi minuti dall'autostrada, anche solo tra le bandiere che preferisci. Sulla mappa del viaggio compaiono solo le soste consigliate.
- **Modalità autostrada**: durante il viaggio segue la posizione con una notifica fissa, aggiorna km, autonomia e sosta consigliata, avvisa 10 km prima e chiede se hai fatto il pieno quando ti fermi a un distributore.
- **Preferiti e avvisi**: notifiche quando un preferito abbassa il prezzo o in zona si scende sotto la tua soglia, controllate circa ogni tre ore con i prezzi in vigore.
- **Auto elettriche**: colonnine dalla Piattaforma Unica Nazionale con i punti liberi in questo momento, prezzo dichiarato dal gestore, tariffa di casa (bolletta, fasce, fotovoltaico, perdite), costi e tempi di ricarica.
- **Widget** per la schermata Home: il più conveniente vicino a te (con altri due) e il livello stimato della tua auto.

## Scaricarla

- **Anteprima**: ogni modifica all'app produce una versione di prova. Dal telefono apri <https://github.com/0Thinker0/goccia/releases/tag/anteprima>, scarica **goccia.apk** e aprilo (Android chiede di consentire l'installazione da questa fonte). Le versioni successive si installano sopra la precedente senza perdere i dati.

## Sostenere il progetto

Goccia è gratis per tutti e lo resta. Nella versione del Play Store si può offrire un caffè da 1, 3 o 5 euro con Google Play (Profilo > Sostieni il progetto); non sblocca nulla. Si aiuta anche facendola conoscere, segnalando problemi e idee nelle [issue](https://github.com/0Thinker0/goccia/issues) o mettendo una stella al repository.

## Come funziona

```
MIMIT (CSV ogni mattina) ──────┐
                               ├─► GitHub Actions: pipeline/ ──► GitHub Pages: dati/v1/*.json ──► app Android
PUN del GSE (colonnine, ogni   │                                                                    ▲   ▲
giorno; riserva OpenStreetMap) ┘                       Osservaprezzi (prezzi in vigore adesso) ─────┘   │
                                                       PUN (punti liberi adesso) ───────────────────────┘
```

- **`pipeline/`**: script Node senza dipendenze. Due volte al giorno scarica i CSV del Ministero (anagrafica impianti e prezzi delle 8), scarta righe e coordinate sbagliate, calcola le medie e pubblica un file JSON per provincia, lo storico delle medie (400 giorni) e quello di ogni distributore (35 giorni e 12 mesi). Al primo avvio ricostruisce da solo l'ultimo mese da un archivio pubblico dei CSV. Scarta i prezzi lontanissimi dalla mediana italiana dello stesso carburante (errori di battitura). Ogni giorno scarica le colonnine dalla Piattaforma Unica Nazionale (PUN) del GSE, il registro obbligatorio dei punti di ricarica pubblici, con prese, potenze e prezzi a consumo dichiarati dai gestori, e le divide in tessere di mezzo grado; se la PUN non risponde usa OpenStreetMap o le tessere del giorno prima.
- **`site/`**: due pagine pubblicate insieme ai dati che rimandano alla [pagina del progetto](https://orlandogabriele.com/projects/goccia.html) e all'[informativa privacy](https://orlandogabriele.com/projects/goccia.html#informativa), così restano validi i link delle versioni già installate.
- **`app/`**: l'app in Kotlin e Jetpack Compose. Scarica solo le province che servono, le tiene in memoria per l'uso offline e fa tutti i calcoli sul telefono (distanze, media della zona, convenienza reale con il tragitto, autonomia, risparmio). Il file del Ministero ha i prezzi in vigore alle 8 del giorno prima: quando è in linea l'app chiede anche quelli di adesso all'API pubblica di Osservaprezzi (carburanti.mise.gov.it) e, aprendo una colonnina, quali punti sono liberi alla PUN. Nasconde i distributori che non comunicano i prezzi da oltre un mese.

Tutto gira su servizi gratuiti: GitHub Actions per elaborare i dati e compilare l'APK, GitHub Pages per distribuirli, OpenFreeMap per la mappa.

### File pubblicati

| Percorso | Contenuto |
| --- | --- |
| `dati/v1/indice.json` | data dell'estrazione, medie nazionali e per provincia, riquadro e centro di ogni provincia |
| `dati/v1/p/{PROV}.json` | distributori della provincia con i prezzi (in millesimi di euro) |
| `dati/v1/s/{PROV}.json` | medie giornaliere della provincia |
| `dati/v1/h/{PROV}.json` | storico di ogni distributore |
| `dati/v1/comuni.json` | comuni con almeno un distributore, per la ricerca |
| `dati/v1/ev/indice.json` | colonnine: data, fonte, tessere disponibili e stime delle tariffe (casa e colonnine) |
| `dati/v1/ev/t/{lat}_{lon}.json` | colonnine di una tessera di mezzo grado: prese, potenze, operatore, orari; dalla PUN anche i codici dei punti e i prezzi dei gestori |

## Sviluppo

- Pipeline: `cd pipeline && npm test`
- App: `cd app && ./gradlew testDebugUnitTest assembleDebug` (serve JDK 17 e l'SDK Android)
- Versione per Google Play: `./gradlew bundleRelease -Pgoccia.play -Pgoccia.tutteLeAbi`. Con `-Pgoccia.play` i caffè passano da Google Play Billing (prodotti `caffe_1`, `caffe_3`, `caffe_5`) e non c'è nessun link esterno per donare; senza, la versione di GitHub mostra il link di `goccia.donazioni` (`app/gradle.properties`) se c'è. La firma usa la chiave privata indicata da `GOCCIA_KEYSTORE_FILE` e dalle variabili `GOCCIA_KEYSTORE_PASSWORD`, `GOCCIA_KEY_ALIAS`, `GOCCIA_KEY_PASSWORD` (su GitHub arrivano dai secret del repository).
- Prova sull'emulatore: il workflow "Prova sull'emulatore" installa l'APK, attraversa le schermate (compresi widget e modalità autostrada con un viaggio simulato) e pubblica screenshot e log nel ramo `ci-prova`. Usa il rendering SwANGLE: con il vecchio SwiftShader MapLibre non disegna segnaposto ed etichette.

Nella build su GitHub l'indirizzo dei dati viene preso dalle Pages del repository; in locale da `app/gradle.properties` (`goccia.datiUrl`). Lo stesso file ha l'indirizzo dell'informativa privacy aperta dalle Impostazioni (`goccia.privacy`).

## Fonti e licenze

- **Prezzi e anagrafica dei distributori**: Ministero delle Imprese e del Made in Italy, Osservaprezzi carburanti, licenza [IODL 2.0](https://www.dati.gov.it/iodl/2.0/); prezzi in vigore e servizi dei distributori dall'API pubblica del sito Osservaprezzi.
- **Colonnine di ricarica**: GSE – [Piattaforma Unica Nazionale](https://www.piattaformaunicanazionale.it/) dei punti di ricarica. La piattaforma non dichiara una licenza: secondo le linee guida AgID i dati pubblici senza licenza si intendono CC BY 4.0, citando la fonte. Se la PUN non risponde: © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, licenza [ODbL](https://opendatacommons.org/licenses/odbl/) (in quel caso i file `dati/v1/ev/` sono un database derivato con la stessa licenza).
- **Stime delle tariffe**: media nazionale dell'energia per le famiglie (III trimestre 2026) e prezzi medi a consumo delle colonnine dell'Osservatorio Adiconsum (agosto 2026), in `pipeline/src/colonnine.js`.
- **Mappe**: © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, [OpenMapTiles](https://openmaptiles.org/), servite da [OpenFreeMap](https://openfreemap.org/).
- **Percorsi**: [OSRM](https://project-osrm.org/) sul servizio pubblico di [FOSSGIS](https://routing.openstreetmap.de/).
- **Carattere**: Plus Jakarta Sans, SIL Open Font License (`app/FONT_LICENSE_OFL.txt`).
- **Codice**: licenza MIT (`LICENSE`).

## Crediti

Creato da Gabriele Orlando, con la collaborazione di Claude Code.
