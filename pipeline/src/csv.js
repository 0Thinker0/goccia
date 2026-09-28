// Lettura dei due CSV MIMIT (separatore "|" dal 10 febbraio 2026).
// Il parser e tollerante: "|" dentro nomi e indirizzi, tabulazioni,
// coordinate assenti o fuori dall'Italia, righe spezzate.

import { comune, nomeBandiera, pulisci, pulisciIndirizzo, pulisciNome } from './testo.js';

const TIPI = { stradale: 'S', autostradale: 'A', altro: 'X' };

export function leggiEstrazione(riga) {
  const m = /Estrazione del (\d{4}-\d{2}-\d{2})/.exec(riga ?? '');
  return m ? m[1] : null;
}

export function numero(x) {
  const n = Number(String(x ?? '').trim().replace(',', '.'));
  return Number.isFinite(n) ? n : NaN;
}

const arrotonda = (n, cifre) => Math.round(n * 10 ** cifre) / 10 ** cifre;

function intestazione(linee, nomeFile, colonna) {
  const estrazione = leggiEstrazione(linee[0]);
  if (!estrazione) throw new Error(`${nomeFile}: manca la riga "Estrazione del AAAA-MM-GG"`);
  if (!new RegExp(colonna, 'i').test(linee[1] ?? '')) {
    throw new Error(`${nomeFile}: manca la riga con i nomi delle colonne`);
  }
  return estrazione;
}

/**
 * anagrafica_impianti_attivi.csv
 * idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine
 */
export function parseAnagrafica(testo) {
  const linee = testo.split(/\r?\n/);
  const estrazione = intestazione(linee, 'anagrafica', 'idImpianto');
  const impianti = new Map();
  const scarti = { campi: 0, id: 0, provincia: 0, coordinate: 0 };

  for (let i = 2; i < linee.length; i++) {
    const riga = linee[i];
    if (!riga.trim()) continue;
    const f = riga.split('|');
    const n = f.length;
    if (n < 10) {
      scarti.campi++;
      continue;
    }
    const id = Number.parseInt(f[0], 10);
    if (!Number.isFinite(id)) {
      scarti.id++;
      continue;
    }
    // Coda fissa: Indirizzo|Comune|Provincia|Latitudine|Longitudine
    const lon = numero(f[n - 1]);
    const lat = numero(f[n - 2]);
    const prov = pulisci(f[n - 3]).toUpperCase();
    // Testa: il "Tipo Impianto" fa da ancora per trovare bandiera e nome
    let t = -1;
    for (let k = 2; k <= n - 6; k++) {
      if (TIPI[pulisci(f[k]).toLowerCase()]) {
        t = k;
        break;
      }
    }
    if (t === -1) t = 3;
    const tipo = TIPI[pulisci(f[t]).toLowerCase()] ?? 'X';

    if (!/^[A-Z]{2}$/.test(prov)) {
      scarti.provincia++;
      continue;
    }
    if (!(lat > 35 && lat < 47.6 && lon > 6 && lon < 19)) {
      scarti.coordinate++;
      continue;
    }
    impianti.set(id, {
      id,
      bandiera: nomeBandiera(f[t - 1]),
      tipo,
      nome: pulisciNome(f.slice(t + 1, n - 5).join(' | ')),
      indirizzo: pulisciIndirizzo(f[n - 5]),
      comune: comune(f[n - 4]),
      prov,
      lat: arrotonda(lat, 6),
      lon: arrotonda(lon, 6)
    });
  }
  return { estrazione, impianti, scarti };
}

/**
 * prezzo_alle_8.csv
 * idImpianto|descCarburante|prezzo|isSelf|dtComu
 */
export function parsePrezzi(testo) {
  const linee = testo.split(/\r?\n/);
  const estrazione = intestazione(linee, 'prezzi', 'idImpianto');
  const righe = [];
  const scarti = { campi: 0, prezzo: 0, data: 0 };
  for (let i = 2; i < linee.length; i++) {
    const riga = linee[i];
    if (!riga.trim()) continue;
    const f = riga.split('|');
    if (f.length !== 5) {
      scarti.campi++;
      continue;
    }
    const id = Number.parseInt(f[0], 10);
    const prezzo = numero(f[2]);
    const ts = romaEpoch(f[4].trim());
    if (!Number.isFinite(id) || !Number.isFinite(prezzo) || prezzo <= 0) {
      scarti.prezzo++;
      continue;
    }
    if (ts == null) {
      scarti.data++;
      continue;
    }
    righe.push({ id, desc: pulisci(f[1]), prezzo, self: f[3].trim() === '1', ts });
  }
  return { estrazione, righe, scarti };
}

function ultimaDomenica(anno, mese) {
  const ultimo = new Date(Date.UTC(anno, mese, 0));
  return ultimo.getUTCDate() - ultimo.getUTCDay();
}

/** true se data e ora locali italiane cadono nell'ora legale (CEST). */
export function oraLegale(anno, mese, giorno, ora) {
  if (mese < 3 || mese > 10) return false;
  if (mese > 3 && mese < 10) return true;
  const domenica = ultimaDomenica(anno, mese);
  if (mese === 3) return giorno > domenica || (giorno === domenica && ora >= 2);
  return giorno < domenica || (giorno === domenica && ora < 3);
}

/** "25/09/2026 20:00:07" (ora italiana) -> secondi Unix. */
export function romaEpoch(s) {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})\s+(\d{2}):(\d{2}):(\d{2})$/.exec(s ?? '');
  if (!m) return null;
  const [giorno, mese, anno, ora, minuti, secondi] = m.slice(1).map(Number);
  const utc = Date.UTC(anno, mese - 1, giorno, ora, minuti, secondi);
  const offsetOre = oraLegale(anno, mese, giorno, ora) ? 2 : 1;
  return Math.floor(utc / 1000) - offsetOre * 3600;
}

/** Mezzanotte UTC del giorno di estrazione + 8 ore italiane: l'istante a cui si riferiscono i prezzi. */
export function istanteEstrazione(dataISO) {
  const [anno, mese, giorno] = dataISO.split('-').map(Number);
  const offsetOre = oraLegale(anno, mese, giorno, 8) ? 2 : 1;
  return Math.floor(Date.UTC(anno, mese - 1, giorno, 8, 0, 0) / 1000) - offsetOre * 3600;
}
