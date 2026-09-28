// Download dei CSV: sorgente ufficiale MIMIT, con l'archivio pubblico su GitHub come riserva
// (e come fonte per ricostruire lo storico degli ultimi giorni al primo avvio).

import { gunzipSync } from 'node:zlib';

export const MIMIT = 'https://www.mimit.gov.it/images/exportCSV/';
export const ARCHIVIO = 'https://raw.githubusercontent.com/LucaDDDD/benzina-data/main/data/';
const AGENTE = 'Goccia/1.0 (app gratuita prezzi carburanti; dati MIMIT IODL 2.0)';

const attendi = (ms) => new Promise((r) => setTimeout(r, ms));

export async function scarica(url, { tentativi = 3, timeoutMs = 180_000 } = {}) {
  let ultimoErrore;
  for (let i = 1; i <= tentativi; i++) {
    try {
      const risposta = await fetch(url, {
        headers: { 'User-Agent': AGENTE },
        signal: AbortSignal.timeout(timeoutMs)
      });
      if (!risposta.ok) throw new Error(`HTTP ${risposta.status}`);
      const byte = Buffer.from(await risposta.arrayBuffer());
      return url.endsWith('.gz') ? gunzipSync(byte) : byte;
    } catch (errore) {
      ultimoErrore = errore;
      console.warn(`  tentativo ${i}/${tentativi} fallito per ${url}: ${errore.message}`);
      if (i < tentativi) await attendi(5000 * i);
    }
  }
  throw new Error(`Impossibile scaricare ${url}: ${ultimoErrore?.message}`);
}

const testo = (byte) => new TextDecoder('utf-8').decode(byte);

export async function daMimit() {
  const anagrafica = testo(await scarica(`${MIMIT}anagrafica_impianti_attivi.csv`));
  const prezzi = testo(await scarica(`${MIMIT}prezzo_alle_8.csv`));
  return { anagrafica, prezzi, fonte: 'mimit' };
}

export async function ultimaDataArchivio() {
  const stato = JSON.parse(testo(await scarica(`${ARCHIVIO}last_update.json`)));
  return stato.ultima_estrazione;
}

export async function daArchivio(dataISO) {
  const [anno, mese] = dataISO.split('-');
  const compatta = dataISO.replaceAll('-', '');
  const base = `${ARCHIVIO}${anno}/${mese}/`;
  const anagrafica = testo(await scarica(`${base}anagrafica_impianti_attivi-${compatta}.csv.gz`, { tentativi: 2 }));
  const prezzi = testo(await scarica(`${base}prezzo_alle_8-${compatta}.csv.gz`, { tentativi: 2 }));
  return { anagrafica, prezzi, fonte: `archivio ${dataISO}` };
}

/** Elenco di date ISO da `giorni` fa fino a `fine` inclusa. */
export function dateFinoA(fine, giorni) {
  const [a, m, g] = fine.split('-').map(Number);
  const date = [];
  for (let i = giorni - 1; i >= 0; i--) {
    const d = new Date(Date.UTC(a, m - 1, g - i));
    date.push(d.toISOString().slice(0, 10));
  }
  return date;
}
