// Colonnine di ricarica da OpenStreetMap (© OpenStreetMap contributors, licenza ODbL).
//
// Una volta alla settimana le scarichiamo da Overpass, teniamo solo quelle che un'auto puo
// usare (non private, non solo per bici o monopattini) e le dividiamo in tessere di mezzo
// grado: l'app scarica solo la zona che le serve, attorno a te o lungo un viaggio.
// Negli altri giorni ripubblichiamo le tessere della settimana; se Overpass non risponde
// teniamo quelle vecchie.

import { join } from 'node:path';
import { RADICE_DATI, conLimite, lettore, scriviJson } from './stato.js';

export const VERSIONE_COLONNINE = 1;
export const PASSO = 0.5;
export const GIORNI_VALIDITA = 7;
/** Sotto questa soglia la risposta di Overpass e incompleta: meglio i dati della settimana prima. */
export const MINIMO_COLONNINE = 5000;

export const SERVER_OVERPASS = ['https://overpass-api.de/api/interpreter', 'https://overpass.kumi.systems/api/interpreter'];

export const QUERY = `[out:json][timeout:300];
area["ISO3166-1"="IT"]["admin_level"="2"]->.italia;
nwr["amenity"="charging_station"](area.italia);
out center tags qt;`;

/**
 * Stime iniziali delle tariffe, mostrate finche l'utente non inserisce le sue.
 * Stanno qui (e non nell'app) per poterle aggiornare senza pubblicare una nuova versione.
 */
export const TARIFFE = {
  casa: { p: 0.24, nota: 'media nazionale per le famiglie, tutto compreso (III trimestre 2026)' },
  colonnine: { ac: 0.64, dc: 0.73, hpc: 0.76, nota: 'prezzi medi a consumo (Osservatorio Adiconsum, agosto 2026)' }
};

/**
 * Tipi di presa: C = CCS2 (Combo 2), T = Type 2, H = CHAdeMO, S = presa domestica o industriale,
 * X = Supercharger solo Tesla, A = altro (Type 1, Type 3...).
 */
const PRESE = {
  type2_combo: 'C',
  tesla_supercharger_ccs: 'C',
  type2: 'T',
  type2_cable: 'T',
  chademo: 'H',
  schuko: 'S',
  typee: 'S',
  cee_blue: 'S',
  tesla_supercharger: 'X',
  type1: 'A',
  type1_combo: 'A',
  type3a: 'A',
  type3c: 'A',
  cee_red_16a: 'A',
  cee_red_32a: 'A',
  cee_red_63a: 'A',
  cee_red_125a: 'A',
  nacs: 'A'
};

/** Potenza tipica quando OSM non la indica (kW). */
const POTENZA_TIPICA = { C: 50, H: 50, X: 120, T: 22, S: 3.7, A: 7 };

/** Tipi di presa con cui ricarica un'auto (le prese domestiche servono anche a bici e monopattini). */
const DA_AUTO = new Set(['C', 'T', 'H', 'X', 'A']);

export const BIT = { H24: 1, CLIENTI: 2, GRATUITA: 4, POTENZA_STIMATA: 8 };

const arrotonda = (n, cifre) => Math.round(n * 10 ** cifre) / 10 ** cifre;

/** "22 kW", "7,4 kW", "11 kW;22 kW", "22000 W", "50" -> massimo in kW (null se non si capisce). */
export function potenzaKw(valore) {
  if (valore == null) return null;
  let massimo = null;
  for (const parte of String(valore).split(/[;/|]/)) {
    const testo = parte.replace(/(\d),(\d)/g, '$1.$2').toLowerCase();
    const m = testo.match(/(\d+(?:\.\d+)?)\s*(kw|kva|w|va)?\b/);
    if (!m) continue;
    let kw = Number(m[1]);
    const unita = m[2];
    if (unita === 'w' || unita === 'va' || (!unita && kw > 1000)) kw /= 1000;
    if (!(kw >= 1 && kw <= 1000)) continue;
    massimo = massimo == null ? kw : Math.max(massimo, kw);
  }
  return massimo == null ? null : arrotonda(massimo, 1);
}

/** "2" -> 2, "yes" -> 1, "no"/"0" -> 0. */
function quante(valore) {
  const testo = String(valore).trim().toLowerCase();
  if (testo === 'no') return 0;
  const n = parseInt(testo, 10);
  if (Number.isFinite(n)) return Math.max(0, Math.min(n, 50));
  return 1;
}

function indirizzo(t) {
  const via = [t['addr:street'] ?? t['addr:place'], t['addr:housenumber']].filter(Boolean).join(' ');
  const citta = t['addr:city'];
  const tutto = [via, citta].filter(Boolean).join(', ');
  return tutto || null;
}

/**
 * Un elemento di Overpass -> colonnina compatta, o null se non serve a un'auto.
 * [id, lat, lon, nome, operatore, kW massimi, [[presa, quante, kW]], bit, indirizzo, orari]
 */
export function normalizza(el) {
  const t = el.tags ?? {};
  const la = el.lat ?? el.center?.lat;
  const lo = el.lon ?? el.center?.lon;
  if (la == null || lo == null) return null;
  const accesso = (t.access ?? '').toLowerCase();
  if (['private', 'no', 'delivery'].includes(accesso)) return null;
  if ((t.motorcar ?? '').toLowerCase() === 'no' || t.disused === 'yes' || t.operational_status === 'closed') return null;

  const prese = new Map();
  for (const [chiave, valore] of Object.entries(t)) {
    const m = chiave.match(/^socket:([a-z0-9_]+)$/);
    if (!m) continue;
    const tipo = PRESE[m[1]];
    if (!tipo) continue;
    const n = quante(valore);
    if (n === 0) continue;
    const kw = potenzaKw(t[`socket:${m[1]}:output`]);
    const prima = prese.get(tipo);
    if (prima) {
      prima.n += n;
      if (kw != null) prima.kw = Math.max(prima.kw ?? 0, kw);
    } else {
      prese.set(tipo, { n, kw });
    }
  }
  const perAuto = [...prese.keys()].some((tipo) => DA_AUTO.has(tipo));
  const soloDueRuote = ['bicycle', 'scooter', 'motorcycle', 'moped'].some((v) => (t[v] ?? '').toLowerCase() === 'yes');
  if (!perAuto && soloDueRuote && (t.motorcar ?? '').toLowerCase() !== 'yes') return null;

  let stimata = false;
  const connettori = [...prese.entries()].map(([tipo, p]) => {
    if (p.kw == null) stimata = true;
    return [tipo, p.n, p.kw ?? POTENZA_TIPICA[tipo]];
  });
  connettori.sort((a, b) => b[2] - a[2] || a[0].localeCompare(b[0]));
  let kw = connettori.length ? Math.max(...connettori.map((c) => c[2])) : null;
  const dichiarata = potenzaKw(t['charging_station:output']);
  if (dichiarata != null && (kw == null || stimata)) {
    kw = dichiarata;
    stimata = false;
  }
  if (kw == null) stimata = false;

  let bit = 0;
  const orari = (t.opening_hours ?? '').trim();
  if (orari === '24/7') bit |= BIT.H24;
  if (['customers', 'permit', 'destination'].includes(accesso)) bit |= BIT.CLIENTI;
  if ((t.fee ?? '').toLowerCase() === 'no') bit |= BIT.GRATUITA;
  if (stimata) bit |= BIT.POTENZA_STIMATA;

  const id = `${el.type?.[0] ?? 'n'}${el.id}`;
  const nome = (t.name ?? '').trim() || null;
  const operatore = (t.operator ?? t.network ?? t.brand ?? '').trim() || null;
  return [
    id,
    arrotonda(la, 5),
    arrotonda(lo, 5),
    nome,
    operatore,
    kw,
    connettori,
    bit,
    indirizzo(t),
    orari && orari !== '24/7' ? orari.slice(0, 80) : null
  ];
}

export const chiaveTessera = (la, lo) => `${Math.floor(la / PASSO)}_${Math.floor(lo / PASSO)}`;

/** Elementi di Overpass -> indice e tessere pronte da pubblicare. */
export function elaboraColonnine(elementi, adesso = new Date()) {
  const tessere = new Map();
  const visti = new Set();
  let scartate = 0;
  for (const el of elementi) {
    const c = normalizza(el);
    if (!c) {
      scartate++;
      continue;
    }
    if (visti.has(c[0])) continue;
    visti.add(c[0]);
    const chiave = chiaveTessera(c[1], c[2]);
    if (!tessere.has(chiave)) tessere.set(chiave, []);
    tessere.get(chiave).push(c);
  }
  const conteggi = {};
  for (const [chiave, lista] of [...tessere.entries()].sort(([a], [b]) => a.localeCompare(b))) {
    lista.sort((a, b) => a[1] - b[1] || a[2] - b[2]);
    conteggi[chiave] = lista.length;
  }
  const indice = {
    v: VERSIONE_COLONNINE,
    generato: adesso.toISOString(),
    fonte: 'OpenStreetMap',
    licenza: 'ODbL 1.0',
    conteggio: visti.size,
    passo: PASSO,
    tessere: conteggi,
    tariffe: TARIFFE
  };
  return { indice, tessere: new Map([...tessere.entries()].map(([k, lista]) => [k, { v: VERSIONE_COLONNINE, c: lista }])), scartate };
}

const AGENTE = `Goccia/1.0 (app gratuita prezzi carburanti e colonnine; https://github.com/${process.env.GITHUB_REPOSITORY ?? 'goccia'})`;

export async function scaricaOverpass({ server = SERVER_OVERPASS, query = QUERY } = {}) {
  let ultimo;
  for (const url of server) {
    try {
      const risposta = await fetch(url, {
        method: 'POST',
        headers: { 'User-Agent': AGENTE, 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ data: query }),
        signal: AbortSignal.timeout(420_000)
      });
      if (!risposta.ok) throw new Error(`HTTP ${risposta.status}`);
      const json = await risposta.json();
      if (!Array.isArray(json.elements)) throw new Error('risposta senza elementi');
      // con un errore a meta strada Overpass risponde 200 con una nota e dati parziali
      if (json.remark && /error/i.test(json.remark)) throw new Error(json.remark);
      return json;
    } catch (errore) {
      ultimo = errore;
      console.warn(`  Overpass ${url}: ${errore.message}`);
    }
  }
  throw new Error(`Overpass non disponibile (${ultimo?.message})`);
}

/**
 * Scrive in uscita le colonnine: nuove da Overpass se quelle pubblicate hanno piu di una
 * settimana (o se forza), altrimenti quelle pubblicate. Non blocca mai la pipeline dei prezzi.
 */
export async function aggiornaColonnine({ uscita, url, cartella, forza = false, scarica = scaricaOverpass, adesso = new Date() }) {
  const leggi = lettore({ url, cartella });
  const base = `${RADICE_DATI}ev/`;
  const precedente = await leggi(`${base}indice.json`);
  const giorni = precedente?.generato ? (adesso - new Date(precedente.generato)) / 86_400_000 : Infinity;
  const valido = precedente && precedente.v === VERSIONE_COLONNINE && giorni < GIORNI_VALIDITA;

  let risultato = null;
  let origine = null;
  let avviso = null;
  if (forza || !valido) {
    try {
      const grezzo = await scarica();
      const nuovo = elaboraColonnine(grezzo.elements, adesso);
      if (nuovo.indice.conteggio < MINIMO_COLONNINE) throw new Error(`solo ${nuovo.indice.conteggio} colonnine, risposta incompleta`);
      risultato = nuovo;
      origine = 'overpass';
    } catch (errore) {
      avviso = errore.message;
      console.warn(`Colonnine: ${errore.message}. Tengo quelle pubblicate.`);
    }
  }
  if (!risultato && precedente && precedente.v === VERSIONE_COLONNINE) {
    const chiavi = Object.keys(precedente.tessere ?? {});
    const tessere = new Map();
    await conLimite(chiavi, 8, async (k) => {
      const t = await leggi(`${base}t/${k}.json`);
      if (t) tessere.set(k, t);
    });
    const conteggi = {};
    for (const k of chiavi) if (tessere.has(k)) conteggi[k] = precedente.tessere[k];
    const conteggio = Object.values(conteggi).reduce((a, b) => a + b, 0);
    if (tessere.size < chiavi.length) console.warn(`Colonnine: ${chiavi.length - tessere.size} tessere precedenti non leggibili`);
    if (tessere.size > 0) {
      risultato = { indice: { ...precedente, tessere: conteggi, conteggio, tariffe: TARIFFE }, tessere };
      origine = 'precedenti';
    }
  }
  if (!risultato) return { origine: 'nessuna', conteggio: 0, tessere: 0, byte: 0, gzip: 0, avviso };

  const cartellaEv = join(uscita, base);
  let byte = 0;
  let gzip = 0;
  for (const [k, tessera] of risultato.tessere) {
    const d = await scriviJson(join(cartellaEv, 't', `${k}.json`), tessera);
    byte += d.byte;
    gzip += d.gzip;
  }
  const d = await scriviJson(join(cartellaEv, 'indice.json'), risultato.indice);
  return {
    origine,
    conteggio: risultato.indice.conteggio,
    tessere: risultato.tessere.size,
    generato: risultato.indice.generato,
    byte: byte + d.byte,
    gzip: gzip + d.gzip,
    avviso
  };
}
