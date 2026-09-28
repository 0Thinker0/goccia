import assert from 'node:assert/strict';
import { test } from 'node:test';
import { famigliaSpeciale } from '../src/carburanti.js';
import { istanteEstrazione, parseAnagrafica, parsePrezzi, romaEpoch } from '../src/csv.js';
import { coordinateDubbie, costruisci, scartaPrezziAnomali, statoDa } from '../src/elabora.js';
import { nomeBandiera, pulisciIndirizzo, pulisciNome, titolo } from '../src/testo.js';
import * as fx from './fixtures.js';

test('titoli leggibili dal maiuscolo', () => {
  assert.equal(titolo('VIA GIUSEPPE AIRENTI 61'), 'Via Giuseppe Airenti 61');
  assert.equal(titolo('VIALE DEGLI OLEANDRI'), 'Viale degli Oleandri');
  assert.equal(titolo("VIA DELL'INDUSTRIA"), "Via dell'Industria");
  assert.equal(titolo('CASTEL SAN PIETRO TERME'), 'Castel San Pietro Terme');
  assert.equal(titolo('Via Dalmine 23'), 'Via Dalmine 23');
  assert.equal(titolo('Via triumvirato 87/a'), 'Via Triumvirato 87/A');
  assert.equal(titolo('Mc service di fava giuseppe'), 'Mc Service di Fava Giuseppe');
  assert.equal(titolo('Autostrada A1 MILANO-NAPOLI, Km. 83'), 'Autostrada A1 Milano-Napoli, Km. 83');
  assert.equal(titolo('Eni'), 'Eni');
  assert.equal(pulisciIndirizzo('SS.189 KM. 64+649 - C.DA SAN MICHELE  S.N.C  '), 'SS.189 km. 64+649 - C.da San Michele snc');
  assert.equal(pulisciIndirizzo('VIA EMILIA LEVANTE 214  40139'), 'Via Emilia Levante 214');
  assert.equal(pulisciNome('19829 AGRIGENTO'), 'Agrigento');
  assert.equal(pulisciNome('19834\tMONTALLEGRO'), 'Montallegro');
  assert.equal(nomeBandiera('Pompe Bianche'), 'Pompa bianca');
  assert.equal(nomeBandiera('KEROPETROL'), 'Keropetrol');
  assert.equal(nomeBandiera('Q8'), 'Q8');
  assert.equal(pulisciIndirizzo('Via Via De Gasperi 1 40065, Pianoro (bo) 1'), 'Via De Gasperi 1');
  assert.equal(
    pulisciIndirizzo('Autostrada A13 BOLOGNA-PADOVA, Km. 11+700, dir. Sud - 40010'),
    'Autostrada A13 Bologna-Padova, Km. 11+700, dir. Sud'
  );
});

test('coordinate sbagliate escluse, isole minori conservate', () => {
  const vicino = (id, la, lo) => ({ id, la, lo });
  const provincia = [
    vicino(1, 44.49, 11.34), vicino(2, 44.5, 11.3), vicino(3, 44.45, 11.4), vicino(4, 44.52, 11.36),
    vicino(5, 44.47, 11.33), vicino(6, 41.9, 12.49), // a Roma: sbagliato
    vicino(7, 35.5, 12.6), vicino(8, 35.51, 12.61) // due impianti isolati ma vicini tra loro (isola)
  ];
  assert.deepEqual([...coordinateDubbie(provincia)], [6]);
});

test('anagrafica tollerante', () => {
  const a = parseAnagrafica(fx.anagrafica());
  assert.equal(a.estrazione, '2026-09-27');
  assert.equal(a.impianti.size, 6);
  assert.equal(a.scarti.coordinate, 1);
  assert.equal(a.scarti.campi, 1);
  const stoil = a.impianti.get(101);
  assert.equal(stoil.bandiera, 'Pompa bianca');
  assert.equal(stoil.nome, 'Stoil Simple');
  assert.equal(stoil.indirizzo, 'Str. Prov.le 82 Spinetta Sale');
  assert.equal(stoil.prov, 'AL');
  const pradelli = a.impianti.get(102);
  assert.equal(pradelli.bandiera, 'Pompa bianca');
  assert.equal(pradelli.tipo, 'S');
  assert.equal(pradelli.comune, 'Zocca');
  assert.equal(a.impianti.get(103).nome, 'Casalecchio');
  assert.equal(a.impianti.get(103).comune, 'Casalecchio di Reno');
  assert.equal(a.impianti.get(104).tipo, 'A');
  assert.equal(a.impianti.get(106).bandiera, 'IP');
});

test('ora italiana con ora legale e solare', () => {
  assert.equal(romaEpoch('25/09/2026 20:00:07'), Date.UTC(2026, 8, 25, 18, 0, 7) / 1000);
  assert.equal(romaEpoch('15/01/2026 10:00:00'), Date.UTC(2026, 0, 15, 9, 0, 0) / 1000);
  assert.equal(romaEpoch('29/03/2026 03:00:00'), Date.UTC(2026, 2, 29, 1, 0, 0) / 1000);
  assert.equal(romaEpoch('non una data'), null);
  assert.equal(istanteEstrazione('2026-09-27'), Date.UTC(2026, 8, 27, 6, 0, 0) / 1000);
});

test('famiglie dei carburanti speciali', () => {
  assert.equal(famigliaSpeciale('Blue Diesel'), 'G');
  assert.equal(famigliaSpeciale('HVOlution'), 'G');
  assert.equal(famigliaSpeciale('Blue Super'), 'B');
  assert.equal(famigliaSpeciale('HiQ Perform+'), 'B');
  assert.equal(famigliaSpeciale('Diesel Shell V Power'), 'G');
  assert.equal(famigliaSpeciale('L-GNC'), 'M');
  assert.equal(famigliaSpeciale('GNL'), null);
});

test('costruzione dei file per provincia', () => {
  const risultato = costruisci({
    anagrafica: parseAnagrafica(fx.anagrafica()),
    prezzi: parsePrezzi(fx.prezzi()),
    generato: '2026-09-27T07:00:00Z'
  });
  const s = risultato.statistiche;
  assert.equal(s.prezziSenzaImpianto, 1);
  assert.equal(s.prezziFuoriScala, 1, 'il metano a 0,10 €/kg va scartato');

  const bo = risultato.province.get('BO');
  const aurora = bo.prezzi.impianti.find((i) => i.id === 100);
  assert.equal(aurora.p.G.s, 1689, 'vince la comunicazione piu recente');
  assert.equal(aurora.p.G.v, 1859);
  assert.equal(aurora.p.L.v, 719);
  assert.equal(aurora.x[0].d, 'Blue Diesel');
  assert.equal(aurora.x[0].f, 'G');
  assert.equal(aurora.b, 'Agip Eni');
  assert.equal(aurora.i, 'Via Emilia Levante 214');

  // media gasolio self BO: 1689 (100) e 1729 (103); esclusi autostrada (104) e prezzo vecchio (106)
  assert.deepEqual(bo.prezzi.medie.G.s, { p: 1709, n: 2 });
  assert.ok(!bo.prezzi.impianti.some((i) => i.id === 105), 'impianto senza coordinate escluso');

  assert.deepEqual(risultato.indice.province.BO.bbox, [44.476, 11.28, 44.53, 11.42]);
  assert.equal(risultato.indice.province.BO.nome, 'Bologna');
  assert.equal(risultato.indice.nazionale.medie.G.s.n, 3);
  assert.deepEqual(bo.storico.giorni, ['2026-09-27']);
  assert.deepEqual(bo.storico.medie.G.s, [1709]);
  assert.deepEqual(bo.cronologia.impianti['100'].g.G, [1689]);
  assert.deepEqual(bo.cronologia.mesi, ['2026-09']);

  // comuni per la ricerca: Bologna ha tre impianti (100, 104, 106), mediana delle coordinate
  const comuni = risultato.comuni.comuni;
  assert.deepEqual(comuni.find((c) => c[0] === 'Bologna'), ['Bologna', 'BO', 44.501, 11.379, 3]);
  assert.deepEqual(comuni.map((c) => c[0]), ['Alessandria', 'Bologna', 'Casalecchio di Reno', 'Zocca']);
});

test('lo storico si accumula giorno dopo giorno ed e idempotente', () => {
  const giorno1 = costruisci({
    anagrafica: parseAnagrafica(fx.anagrafica('2026-09-26')),
    prezzi: parsePrezzi(fx.prezzi('2026-09-26', { gasolioAurora: '1.709' }))
  });
  const giorno2 = costruisci({
    anagrafica: parseAnagrafica(fx.anagrafica('2026-09-27')),
    prezzi: parsePrezzi(fx.prezzi('2026-09-27')),
    precedente: statoDa(giorno1)
  });
  const bo = giorno2.province.get('BO');
  assert.deepEqual(bo.storico.giorni, ['2026-09-26', '2026-09-27']);
  assert.deepEqual(bo.cronologia.impianti['100'].g.G, [1709, 1689]);
  assert.deepEqual(bo.cronologia.impianti['100'].m.G, [1699]);
  assert.deepEqual(giorno2.indice.nazionale.giorni, ['2026-09-26', '2026-09-27']);

  // rielaborare lo stesso giorno non duplica le date
  const ripetuto = costruisci({
    anagrafica: parseAnagrafica(fx.anagrafica('2026-09-27')),
    prezzi: parsePrezzi(fx.prezzi('2026-09-27')),
    precedente: statoDa(giorno2)
  });
  assert.deepEqual(ripetuto.province.get('BO').storico.giorni, ['2026-09-26', '2026-09-27']);
  assert.deepEqual(ripetuto.province.get('BO').cronologia.impianti['100'].g.G, [1709, 1689]);
});

test('prezzi lontanissimi dalla mediana italiana scartati', () => {
  const adesso = 1_790_000_000;
  const perImpianto = new Map();
  // 60 gasoli self tra 2,339 e 2,398 (mediana 2,369): tutti normali
  for (let i = 0; i < 60; i++) {
    perImpianto.set(i, { p: { G: { s: 2339 + i, st: adesso - 3600 } }, x: new Map() });
  }
  // errore di battitura: 1,379 invece di 2,379
  perImpianto.set(100, { p: { G: { s: 1379, st: adesso - 3600, v: 2549, vt: adesso - 3600 } }, x: new Map() });
  // troppo caro
  perImpianto.set(101, { p: { G: { s: 3199, st: adesso - 3600 } }, x: new Map() });
  // speciale di famiglia gasolio: un po' piu caro va bene, a meta prezzo no
  perImpianto.set(102, {
    p: { G: { s: 2399, st: adesso - 3600 } },
    x: new Map([
      ['hvo|1', { d: 'HVO', f: 'G', p: 2799, s: 1, t: adesso }],
      ['blue diesel|1', { d: 'Blue Diesel', f: 'G', p: 1199, s: 1, t: adesso }]
    ])
  });
  const scartati = scartaPrezziAnomali(perImpianto, adesso);
  assert.equal(scartati, 3);
  assert.deepEqual(perImpianto.get(100).p, { G: { v: 2549, vt: adesso - 3600 } });
  assert.equal(perImpianto.get(101).p.G, undefined);
  assert.deepEqual([...perImpianto.get(102).x.keys()], ['hvo|1']);
  assert.equal(perImpianto.get(5).p.G.s, 2344);
});
