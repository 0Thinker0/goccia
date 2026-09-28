// Lettura e scrittura dello stato pubblicato (indice, storico medie, cronologia impianti).

import { mkdir, readFile, writeFile, cp } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { gzipSync } from 'node:zlib';
import { statoVuoto } from './elabora.js';

export const RADICE_DATI = 'dati/v1/';

export async function conLimite(elementi, limite, lavoro) {
  const coda = [...elementi];
  const operai = Array.from({ length: Math.min(limite, coda.length) }, async () => {
    while (coda.length) await lavoro(coda.shift());
  });
  await Promise.all(operai);
}

/** Legge un file JSON pubblicato, dal sito o da una cartella; null se manca o non si legge. */
export function lettore({ url, cartella }) {
  return async (percorso) => {
    if (!url && !cartella) return null;
    try {
      if (cartella) return JSON.parse(await readFile(join(cartella, percorso), 'utf8'));
      const risposta = await fetch(new URL(percorso, url), { signal: AbortSignal.timeout(60_000) });
      if (!risposta.ok) return null;
      return await risposta.json();
    } catch {
      return null;
    }
  };
}

/**
 * Carica lo stato pubblicato l'ultima volta, da un sito (GitHub Pages) o da una cartella.
 * Se non c'e nulla (primo avvio) restituisce uno stato vuoto.
 */
export async function caricaPrecedente({ url, cartella }) {
  const leggi = lettore({ url, cartella });
  if (!url && !cartella) return statoVuoto();
  const indice = await leggi(`${RADICE_DATI}indice.json`);
  if (!indice) return statoVuoto();
  const storico = {};
  const cronologia = {};
  await conLimite(Object.keys(indice.province ?? {}), 8, async (sigla) => {
    const s = await leggi(`${RADICE_DATI}s/${sigla}.json`);
    const h = await leggi(`${RADICE_DATI}h/${sigla}.json`);
    if (s) storico[sigla] = s;
    if (h) cronologia[sigla] = h;
  });
  return { indice, storico, cronologia };
}

export async function scriviJson(percorso, oggetto) {
  await mkdir(dirname(percorso), { recursive: true });
  const testo = JSON.stringify(oggetto);
  await writeFile(percorso, testo);
  return { byte: Buffer.byteLength(testo), gzip: gzipSync(testo).length };
}

/** Scrive il sito completo: pagine statiche + dati. */
export async function scriviSito(risultato, { uscita, sito }) {
  if (sito) await cp(sito, uscita, { recursive: true });
  const base = join(uscita, RADICE_DATI);
  const dimensioni = { indice: await scriviJson(join(base, 'indice.json'), risultato.indice), p: 0, s: 0, h: 0, pGz: 0, hGz: 0 };
  dimensioni.comuni = await scriviJson(join(base, 'comuni.json'), risultato.comuni);
  for (const [sigla, dati] of risultato.province) {
    const p = await scriviJson(join(base, 'p', `${sigla}.json`), dati.prezzi);
    const s = await scriviJson(join(base, 's', `${sigla}.json`), dati.storico);
    const h = await scriviJson(join(base, 'h', `${sigla}.json`), dati.cronologia);
    dimensioni.p += p.byte;
    dimensioni.pGz += p.gzip;
    dimensioni.s += s.byte;
    dimensioni.h += h.byte;
    dimensioni.hGz += h.gzip;
  }
  return dimensioni;
}
