#!/usr/bin/env node
// Pipeline giornaliera dei prezzi.
//
//   node src/index.js --uscita ../public --sito ../site --precedente-url https://utente.github.io/goccia/
//
// Opzioni utili:
//   --anagrafica file --prezzi file   usa CSV locali (anche .gz) invece di scaricarli
//   --archivio-giorni 35              ricostruisce lo storico dagli ultimi N giorni dell'archivio pubblico
//   --precedente-cartella dir         legge lo stato pubblicato da una cartella invece che dal sito
//   --forza                           rielabora anche se l'estrazione e gia pubblicata
//   --senza-minimi                    salta i controlli sul numero minimo di righe (solo per i test)
//   --colonnine-forza                 scarica di nuovo le colonnine anche se quelle pubblicate sono di oggi
//   --senza-colonnine                 non pubblica le colonnine (solo per i test)

import { appendFile, readFile } from 'node:fs/promises';
import { parseArgs } from 'node:util';
import { gunzipSync } from 'node:zlib';
import { leggiEstrazione, parseAnagrafica, parsePrezzi } from './csv.js';
import { costruisci, statoDa } from './elabora.js';
import { daArchivio, daMimit, dateFinoA, ultimaDataArchivio } from './fonti.js';
import { aggiornaColonnine } from './colonnine.js';
import { caricaPrecedente, scriviSito } from './stato.js';

// Soglie prudenziali: un giorno normale ha ~24.000 impianti e ~93.000 prezzi.
const MINIMI = { impianti: 15_000, prezzi: 50_000 };

const { values: opz } = parseArgs({
  options: {
    uscita: { type: 'string', default: 'public' },
    sito: { type: 'string' },
    'precedente-url': { type: 'string' },
    'precedente-cartella': { type: 'string' },
    anagrafica: { type: 'string' },
    prezzi: { type: 'string' },
    'archivio-giorni': { type: 'string' },
    forza: { type: 'boolean', default: false },
    'senza-minimi': { type: 'boolean', default: false },
    'colonnine-forza': { type: 'boolean', default: false },
    'senza-colonnine': { type: 'boolean', default: false }
  }
});

async function leggiFile(percorso) {
  const byte = await readFile(percorso);
  return new TextDecoder('utf-8').decode(percorso.endsWith('.gz') ? gunzipSync(byte) : byte);
}

async function uscitaGithub(chiave, valore) {
  if (process.env.GITHUB_OUTPUT) await appendFile(process.env.GITHUB_OUTPUT, `${chiave}=${valore}\n`);
}

function elabora(fonte, stato) {
  const anagrafica = parseAnagrafica(fonte.anagrafica);
  const prezzi = parsePrezzi(fonte.prezzi);
  if (!opz['senza-minimi']) {
    if (anagrafica.impianti.size < MINIMI.impianti) {
      throw new Error(`Anagrafica sospetta: solo ${anagrafica.impianti.size} impianti (${fonte.fonte}). Non pubblico.`);
    }
    if (prezzi.righe.length < MINIMI.prezzi) {
      throw new Error(`Prezzi sospetti: solo ${prezzi.righe.length} righe (${fonte.fonte}). Non pubblico.`);
    }
  }
  if (anagrafica.estrazione !== prezzi.estrazione) {
    console.warn(`Attenzione: anagrafica del ${anagrafica.estrazione}, prezzi del ${prezzi.estrazione}`);
  }
  return costruisci({ anagrafica, prezzi, precedente: stato });
}

const kb = (byte) => `${(byte / 1024).toFixed(0)} KB`;

async function main() {
  console.log('Leggo lo stato pubblicato...');
  let stato = await caricaPrecedente({ url: opz['precedente-url'], cartella: opz['precedente-cartella'] });
  const giaPubblicata = stato.indice?.estrazione ?? null;
  console.log(giaPubblicata ? `Ultima estrazione pubblicata: ${giaPubblicata}` : 'Nessun dato pubblicato finora');

  let risultato = null;

  // Al primo avvio (nulla di pubblicato) ricostruiamo da soli l'ultimo mese di storico.
  const giorniArchivio = opz['archivio-giorni'] != null ? Number(opz['archivio-giorni']) : giaPubblicata ? 0 : 35;
  const locali = Boolean(opz.anagrafica && opz.prezzi);

  if (giorniArchivio > 0 && !locali) {
    const fine = await ultimaDataArchivio();
    console.log(`Ricostruisco lo storico degli ultimi ${giorniArchivio} giorni (fino al ${fine}) dall'archivio pubblico...`);
    for (const data of dateFinoA(fine, giorniArchivio)) {
      if (giaPubblicata && data <= giaPubblicata && !opz.forza) continue;
      let fonteArchivio;
      try {
        fonteArchivio = await daArchivio(data);
      } catch (errore) {
        console.warn(`  ${data}: non disponibile (${errore.message})`);
        continue;
      }
      risultato = elabora(fonteArchivio, stato);
      stato = statoDa(risultato);
      console.log(`  ${data}: ${risultato.statistiche.impiantiPubblicati} impianti`);
    }
  }

  let fonte;
  if (locali) {
    fonte = { anagrafica: await leggiFile(opz.anagrafica), prezzi: await leggiFile(opz.prezzi), fonte: 'file locali' };
  } else {
    try {
      console.log('Scarico i CSV dal MIMIT...');
      fonte = await daMimit();
    } catch (errore) {
      console.warn(`MIMIT non raggiungibile (${errore.message}): uso l'archivio pubblico`);
      fonte = await daArchivio(await ultimaDataArchivio());
    }
  }

  const estrazione = leggiEstrazione(fonte.prezzi.slice(0, 100));
  const ultimaElaborata = stato.indice?.estrazione ?? null;
  if (estrazione && ultimaElaborata && estrazione < ultimaElaborata) {
    console.log(`Il MIMIT pubblica ancora il ${estrazione}, abbiamo gia il ${ultimaElaborata}.`);
  } else if (estrazione && estrazione === ultimaElaborata && !opz.forza) {
    if (!risultato) {
      console.log(`L'estrazione del ${estrazione} e gia pubblicata: niente da fare.`);
      await uscitaGithub('nuovo', 'false');
      return;
    }
  } else {
    risultato = elabora(fonte, stato);
  }

  if (!risultato) {
    console.log('Nessun dato nuovo.');
    await uscitaGithub('nuovo', 'false');
    return;
  }

  const dimensioni = await scriviSito(risultato, { uscita: opz.uscita, sito: opz.sito });
  // le colonnine viaggiano con i prezzi: ogni pubblicazione del sito deve contenerle
  const colonnine = opz['senza-colonnine']
    ? null
    : await aggiornaColonnine({
        uscita: opz.uscita,
        url: opz['precedente-url'],
        cartella: opz['precedente-cartella'],
        forza: opz['colonnine-forza']
      });
  const s = risultato.statistiche;
  console.log(`\nEstrazione ${risultato.estrazione}`);
  console.log(`  impianti in anagrafica ${s.impiantiAnagrafica}, pubblicati ${s.impiantiPubblicati}, senza prezzi ${s.impiantiSenzaPrezzi}`);
  console.log(`  righe prezzi ${s.righePrezzi}, senza impianto ${s.prezziSenzaImpianto}, fuori scala ${s.prezziFuoriScala}, lontani dalla mediana ${s.prezziAnomali}`);
  console.log(`  scarti anagrafica ${JSON.stringify(s.scartiAnagrafica)}, scarti prezzi ${JSON.stringify(s.scartiPrezzi)}`);
  console.log(`  province ${risultato.province.size}, impianti con coordinate sbagliate esclusi ${s.coordinateDubbie}`);
  console.log(`  comuni ${risultato.comuni.comuni.length} (${kb(dimensioni.comuni.byte)}, gzip ${kb(dimensioni.comuni.gzip)})`);
  console.log(`  dimensioni: indice ${kb(dimensioni.indice.byte)}, prezzi ${kb(dimensioni.p)} (gzip ${kb(dimensioni.pGz)}), storico ${kb(dimensioni.s)}, cronologia ${kb(dimensioni.h)} (gzip ${kb(dimensioni.hGz)})`);
  if (colonnine) {
    console.log(
      `  colonnine ${colonnine.conteggio} (${colonnine.origine}${colonnine.generato ? `, del ${colonnine.generato.slice(0, 10)}` : ''}), ` +
        `tessere ${colonnine.tessere}, ${kb(colonnine.byte)} (gzip ${kb(colonnine.gzip)})` +
        (colonnine.avviso ? `, avviso: ${colonnine.avviso}` : '')
    );
  }
  await uscitaGithub('nuovo', 'true');
  await uscitaGithub('estrazione', risultato.estrazione);
}

main().catch(async (errore) => {
  console.error(`\nErrore: ${errore.message}`);
  process.exitCode = 1;
});
