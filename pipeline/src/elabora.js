// Dai CSV del giorno (piu lo stato pubblicato ieri) ai file JSON che legge l'app.
//
// Tutti i prezzi sono in millesimi di euro (1,689 €/l -> 1689) per evitare
// problemi di arrotondamento; l'app li divide per 1000.

import { FAMIGLIE, GIORNI_VECCHIO, famigliaBase, famigliaSpeciale, plausibile } from './carburanti.js';
import { istanteEstrazione } from './csv.js';
import { nomeProvincia } from './province.js';

export const VERSIONE = 1;
export const GIORNI_MEDIE = 400;
export const GIORNI_IMPIANTI = 35;
export const MESI_IMPIANTI = 12;

const millesimi = (p) => Math.round(p * 1000);
const quattro = (n) => Math.round(n * 1e4) / 1e4;

export function distanzaKm(la1, lo1, la2, lo2) {
  const r = Math.PI / 180;
  const a =
    Math.sin(((la2 - la1) * r) / 2) ** 2 +
    Math.cos(la1 * r) * Math.cos(la2 * r) * Math.sin(((lo2 - lo1) * r) / 2) ** 2;
  return 12742 * Math.asin(Math.sqrt(a));
}

const mediana = (valori) => {
  const v = [...valori].sort((a, b) => a - b);
  const m = v.length >> 1;
  return v.length % 2 ? v[m] : (v[m - 1] + v[m]) / 2;
};

/**
 * Coordinate palesemente sbagliate (un impianto di Bologna segnato a Roma): lontano dal centro
 * della sua provincia e senza nessun altro impianto della stessa provincia vicino.
 * Le isole minori (Lampedusa, Pantelleria) restano, perche li gli impianti sono piu di uno.
 */
export function coordinateDubbie(impianti, { lontano = 100, isolato = 60 } = {}) {
  if (impianti.length < 5) return new Set();
  const cLa = mediana(impianti.map((i) => i.la));
  const cLo = mediana(impianti.map((i) => i.lo));
  const dubbi = new Set();
  for (const imp of impianti) {
    if (distanzaKm(cLa, cLo, imp.la, imp.lo) <= lontano) continue;
    const vicino = impianti.some((altro) => altro !== imp && distanzaKm(imp.la, imp.lo, altro.la, altro.lo) <= isolato);
    if (!vicino) dubbi.add(imp.id);
  }
  return dubbi;
}

export function statoVuoto() {
  return { indice: null, storico: {}, cronologia: {} };
}

function unisciDate(precedenti, oggi, massimo) {
  const tutte = new Set(precedenti ?? []);
  tutte.add(oggi);
  return [...tutte].sort().slice(-massimo);
}

/** Riallinea una serie storica alle nuove date, sovrascrivendo il valore di oggi. */
function allinea(datePrecedenti, valoriPrecedenti, dateNuove, oggi, valoreOggi) {
  const mappa = new Map();
  (datePrecedenti ?? []).forEach((d, i) => {
    const v = valoriPrecedenti?.[i];
    if (v != null) mappa.set(d, v);
  });
  if (valoreOggi == null) mappa.delete(oggi);
  else mappa.set(oggi, valoreOggi);
  return dateNuove.map((d) => mappa.get(d) ?? null);
}

const soloNull = (serie) => serie.every((v) => v == null);

/**
 * Quanto puo scostarsi un prezzo dalla mediana italiana dello stesso carburante e modalita.
 * Fuori da qui e quasi sempre un errore di battitura o un listino vecchio rimasto nel sistema
 * (un gasolio a 1,38 quando la mediana e 2,39): meglio non mostrarlo che consigliarlo.
 * GPL e metano variano molto di piu tra zone e autostrade.
 */
export const SCOSTAMENTO = { B: [0.8, 1.3], G: [0.8, 1.3], L: [0.7, 1.5], M: [0.65, 1.5] };
const MINIMO_PER_MEDIANA = 50;

/**
 * Toglie i prezzi assurdi rispetto al resto d'Italia. Lavora sui prezzi raccolti per impianto
 * ({p: {famiglia: {s, st, v, vt}}, x: Map di speciali}) e restituisce quanti ne ha scartati.
 */
export function scartaPrezziAnomali(perImpianto, riferimento) {
  const recente = (ts) => ts != null && riferimento - ts <= GIORNI_VECCHIO * 86400;
  const valori = {};
  for (const voce of perImpianto.values()) {
    for (const [fam, slot] of Object.entries(voce.p)) {
      if (slot.s != null && recente(slot.st)) ((valori[fam] ??= { s: [], v: [] }).s).push(slot.s);
      if (slot.v != null && recente(slot.vt)) ((valori[fam] ??= { s: [], v: [] }).v).push(slot.v);
    }
  }
  const mediane = {};
  for (const [fam, m] of Object.entries(valori)) {
    for (const k of ['s', 'v']) {
      if (m[k].length >= MINIMO_PER_MEDIANA) (mediane[fam] ??= {})[k] = mediana(m[k]);
    }
  }
  const fuori = (fam, k, prezzo, extra = 0) => {
    const m = mediane[fam]?.[k];
    const limiti = SCOSTAMENTO[fam];
    if (!m || !limiti) return false;
    return prezzo < m * limiti[0] || prezzo > m * (limiti[1] + extra);
  };
  let scartati = 0;
  for (const voce of perImpianto.values()) {
    for (const [fam, slot] of Object.entries(voce.p)) {
      for (const [k, kt] of [['s', 'st'], ['v', 'vt']]) {
        if (slot[k] != null && fuori(fam, k, slot[k])) {
          delete slot[k];
          delete slot[kt];
          scartati++;
        }
      }
      if (slot.s == null && slot.v == null) delete voce.p[fam];
    }
    // i carburanti speciali costano di solito un po' di piu: margine piu largo verso l'alto
    for (const [chiave, x] of voce.x) {
      if (x.f && fuori(x.f, x.s ? 's' : 'v', x.p, 0.15)) {
        voce.x.delete(chiave);
        scartati++;
      }
    }
  }
  return scartati;
}

/** Medie per famiglia e modalita (s = self, v = servito) sugli impianti stradali con prezzo recente. */
export function calcolaMedie(impianti, riferimento) {
  const recente = (ts) => ts != null && riferimento - ts <= GIORNI_VECCHIO * 86400;
  const medie = {};
  for (const f of FAMIGLIE) {
    const somma = { s: 0, v: 0 };
    const conta = { s: 0, v: 0 };
    for (const imp of impianti) {
      if (imp.t !== 'S') continue;
      const p = imp.p[f];
      if (!p) continue;
      if (p.s != null && recente(p.st)) {
        somma.s += p.s;
        conta.s++;
      }
      if (p.v != null && recente(p.vt)) {
        somma.v += p.v;
        conta.v++;
      }
    }
    const m = {};
    for (const k of ['s', 'v']) {
      if (conta[k] > 0) m[k] = { p: Math.round(somma[k] / conta[k]), n: conta[k] };
    }
    if (Object.keys(m).length) medie[f] = m;
  }
  return medie;
}

function serieMedie(precedente, date, oggi, medieOggi) {
  const serie = {};
  for (const f of FAMIGLIE) {
    for (const k of ['s', 'v']) {
      const valori = allinea(precedente?.giorni, precedente?.medie?.[f]?.[k], date, oggi, medieOggi[f]?.[k]?.p);
      if (!soloNull(valori)) (serie[f] ??= {})[k] = valori;
    }
  }
  return serie;
}

/** Prezzo di riferimento di un impianto per una famiglia: self se c'e, altrimenti servito. */
const riferimentoImpianto = (p) => (p ? (p.s ?? p.v ?? null) : null);

function cronologiaProvincia(precedente, impianti, oggi) {
  const giorni = unisciDate(precedente?.giorni, oggi, GIORNI_IMPIANTI);
  const mese = oggi.slice(0, 7);
  const mesi = unisciDate(precedente?.mesi, mese, MESI_IMPIANTI);
  const oggiPerId = new Map(impianti.map((imp) => [String(imp.id), imp]));
  const ids = new Set([...oggiPerId.keys(), ...Object.keys(precedente?.impianti ?? {})]);
  const risultato = {};

  for (const id of [...ids].sort((a, b) => Number(a) - Number(b))) {
    const imp = oggiPerId.get(id);
    const prec = precedente?.impianti?.[id];
    const voce = {};
    for (const f of FAMIGLIE) {
      const giornaliero = allinea(precedente?.giorni, prec?.g?.[f], giorni, oggi, riferimentoImpianto(imp?.p?.[f]));
      // media del mese corrente dai valori giornalieri disponibili
      const delMese = giornaliero.filter((v, i) => v != null && giorni[i].startsWith(mese));
      const mediaMese = delMese.length ? Math.round(delMese.reduce((a, b) => a + b, 0) / delMese.length) : null;
      const mensile = allinea(precedente?.mesi, prec?.m?.[f], mesi, mese, mediaMese);
      if (!soloNull(giornaliero) || !soloNull(mensile)) {
        (voce.g ??= {})[f] = giornaliero;
        (voce.m ??= {})[f] = mensile;
      }
    }
    if (Object.keys(voce).length) risultato[id] = voce;
  }
  return { v: VERSIONE, giorni, mesi, impianti: risultato };
}

/**
 * @param {{anagrafica: ReturnType<import('./csv.js').parseAnagrafica>,
 *          prezzi: ReturnType<import('./csv.js').parsePrezzi>,
 *          precedente?: {indice: any, storico: Record<string, any>, cronologia: Record<string, any>},
 *          generato?: string}} input
 */
export function costruisci({ anagrafica, prezzi, precedente = statoVuoto(), generato = new Date().toISOString() }) {
  const estrazione = prezzi.estrazione;
  const riferimento = istanteEstrazione(estrazione);
  const statistiche = {
    impiantiAnagrafica: anagrafica.impianti.size,
    righePrezzi: prezzi.righe.length,
    prezziSenzaImpianto: 0,
    prezziFuoriScala: 0,
    impiantiSenzaPrezzi: 0,
    impiantiPubblicati: 0,
    scartiAnagrafica: anagrafica.scarti,
    scartiPrezzi: prezzi.scarti
  };

  // 1. prezzi per impianto, tenendo la comunicazione piu recente
  const perImpianto = new Map();
  for (const r of prezzi.righe) {
    if (!anagrafica.impianti.has(r.id)) {
      statistiche.prezziSenzaImpianto++;
      continue;
    }
    let voce = perImpianto.get(r.id);
    if (!voce) {
      voce = { p: {}, x: new Map() };
      perImpianto.set(r.id, voce);
    }
    const fam = famigliaBase(r.desc);
    if (fam) {
      if (!plausibile(fam, r.prezzo)) {
        statistiche.prezziFuoriScala++;
        continue;
      }
      const slot = (voce.p[fam] ??= {});
      const [k, kt] = r.self ? ['s', 'st'] : ['v', 'vt'];
      if (slot[kt] == null || r.ts > slot[kt]) {
        slot[k] = millesimi(r.prezzo);
        slot[kt] = r.ts;
      }
    } else {
      const famS = famigliaSpeciale(r.desc);
      if (!plausibile(famS ?? 'altro', r.prezzo)) {
        statistiche.prezziFuoriScala++;
        continue;
      }
      const chiave = `${r.desc.toLowerCase()}|${r.self ? 1 : 0}`;
      const prec = voce.x.get(chiave);
      if (!prec || r.ts > prec.t) {
        voce.x.set(chiave, { d: r.desc, f: famS, p: millesimi(r.prezzo), s: r.self ? 1 : 0, t: r.ts });
      }
    }
  }

  // 1b. prezzi assurdi rispetto al resto d'Italia
  statistiche.prezziAnomali = scartaPrezziAnomali(perImpianto, riferimento);

  // 2. impianti raggruppati per provincia
  const perProvincia = new Map();
  for (const imp of anagrafica.impianti.values()) {
    const voce = perImpianto.get(imp.id);
    if (!voce || (Object.keys(voce.p).length === 0 && voce.x.size === 0)) {
      statistiche.impiantiSenzaPrezzi++;
      continue;
    }
    const riga = { id: imp.id, b: imp.bandiera, n: imp.nome, i: imp.indirizzo, c: imp.comune, t: imp.tipo, la: imp.lat, lo: imp.lon, p: voce.p };
    if (voce.x.size) riga.x = [...voce.x.values()].sort((a, b) => a.d.localeCompare(b.d, 'it') || b.s - a.s);
    if (!perProvincia.has(imp.prov)) perProvincia.set(imp.prov, []);
    perProvincia.get(imp.prov).push(riga);
    statistiche.impiantiPubblicati++;
  }

  // 3. file per provincia
  const province = new Map();
  const voceIndice = {};
  const tutti = [];
  const perComune = new Map();
  statistiche.coordinateDubbie = 0;
  for (const sigla of [...perProvincia.keys()].sort()) {
    const tuttiDellaProvincia = perProvincia.get(sigla);
    const dubbi = coordinateDubbie(tuttiDellaProvincia);
    statistiche.coordinateDubbie += dubbi.size;
    statistiche.impiantiPubblicati -= dubbi.size;
    const impianti = tuttiDellaProvincia.filter((i) => !dubbi.has(i.id)).sort((a, b) => a.id - b.id);
    tutti.push(...impianti);
    for (const imp of impianti) {
      if (!imp.c) continue;
      const chiave = `${sigla}|${imp.c}`;
      if (!perComune.has(chiave)) perComune.set(chiave, { la: [], lo: [] });
      perComune.get(chiave).la.push(imp.la);
      perComune.get(chiave).lo.push(imp.lo);
    }
    const medie = calcolaMedie(impianti, riferimento);
    const storicoPrec = precedente.storico?.[sigla];
    const giorniStorico = unisciDate(storicoPrec?.giorni, estrazione, GIORNI_MEDIE);
    const storico = {
      v: VERSIONE,
      prov: sigla,
      giorni: giorniStorico,
      medie: serieMedie(storicoPrec, giorniStorico, estrazione, medie)
    };
    const cronologia = { prov: sigla, ...cronologiaProvincia(precedente.cronologia?.[sigla], impianti, estrazione) };

    let minLa = 90, minLo = 180, maxLa = -90, maxLo = -180, sLa = 0, sLo = 0;
    for (const imp of impianti) {
      minLa = Math.min(minLa, imp.la);
      maxLa = Math.max(maxLa, imp.la);
      minLo = Math.min(minLo, imp.lo);
      maxLo = Math.max(maxLo, imp.lo);
      sLa += imp.la;
      sLo += imp.lo;
    }
    voceIndice[sigla] = {
      nome: nomeProvincia(sigla),
      n: impianti.length,
      bbox: [quattro(minLa), quattro(minLo), quattro(maxLa), quattro(maxLo)],
      centro: [quattro(sLa / impianti.length), quattro(sLo / impianti.length)],
      medie
    };
    province.set(sigla, {
      prezzi: { v: VERSIONE, prov: sigla, estrazione, medie, impianti },
      storico,
      cronologia
    });
  }

  // 4. indice con medie e storico nazionale
  const medieNazionali = calcolaMedie(tutti, riferimento);
  const nazPrec = precedente.indice?.nazionale;
  const giorniNaz = unisciDate(nazPrec?.giorni, estrazione, GIORNI_MEDIE);
  const indice = {
    v: VERSIONE,
    estrazione,
    generato,
    fonte: 'Ministero delle Imprese e del Made in Italy – Osservaprezzi carburanti (licenza IODL 2.0)',
    nazionale: {
      medie: medieNazionali,
      giorni: giorniNaz,
      storico: serieMedie({ giorni: nazPrec?.giorni, medie: nazPrec?.storico }, giorniNaz, estrazione, medieNazionali)
    },
    province: voceIndice
  };

  // 5. comuni con almeno un distributore: l'app li usa per cercare un posto anche offline
  const comuni = {
    v: VERSIONE,
    estrazione,
    comuni: [...perComune.entries()]
      .map(([chiave, c]) => {
        const [sigla, nome] = chiave.split('|');
        return [nome, sigla, quattro(mediana(c.la)), quattro(mediana(c.lo)), c.la.length];
      })
      .sort((a, b) => a[0].localeCompare(b[0], 'it') || a[1].localeCompare(b[1]))
  };

  return { estrazione, indice, province, comuni, statistiche };
}

/** Lo stato "pubblicato" che serve per elaborare il giorno successivo. */
export function statoDa(risultato) {
  const storico = {};
  const cronologia = {};
  for (const [sigla, dati] of risultato.province) {
    storico[sigla] = dati.storico;
    cronologia[sigla] = dati.cronologia;
  }
  return { indice: risultato.indice, storico, cronologia };
}
