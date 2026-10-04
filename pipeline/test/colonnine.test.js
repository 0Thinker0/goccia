import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { BIT, aggiornaColonnine, chiaveTessera, elaboraColonnine, normalizza, potenzaKw } from '../src/colonnine.js';
import { elaboraPun, firma, nomeGestore, orariBrevi } from '../src/pun.js';

const nodo = (id, lat, lon, tags) => ({ type: 'node', id, lat, lon, tags: { amenity: 'charging_station', ...tags } });

const ESEMPI = [
  // colonnina veloce con potenze dichiarate
  nodo(1, 44.50811, 11.37622, {
    name: 'Voltaria Hub',
    operator: 'Voltaria',
    'socket:type2_combo': '4',
    'socket:type2_combo:output': '150 kW',
    'socket:type2': '2',
    'socket:type2:output': '22 kW',
    opening_hours: '24/7',
    'addr:street': 'Viale Europa',
    'addr:housenumber': '120',
    'addr:city': 'Bologna'
  }),
  // senza potenze: stimate dal tipo di presa
  nodo(2, 44.4901, 11.3102, { network: 'Ricarica Più', 'socket:type2': 'yes', 'socket:chademo': '1' }),
  // privata: esclusa
  nodo(3, 44.49, 11.34, { access: 'private', 'socket:type2': '1' }),
  // solo bici: esclusa
  nodo(4, 44.491, 11.341, { bicycle: 'yes', 'socket:schuko': '4' }),
  // area con centro, clienti, gratuita, potenza scritta all'italiana
  { type: 'way', id: 5, center: { lat: 41.90251, lon: 12.49636 }, tags: { amenity: 'charging_station', access: 'customers', fee: 'no', 'socket:type2': '2', 'socket:type2:output': '7,4 kW', opening_hours: 'Mo-Sa 08:00-20:00' } },
  // nessuna presa indicata, ma la potenza della stazione si
  nodo(6, 45.4642, 9.19, { operator: 'Ionity', 'charging_station:output': '350 kW' }),
  // auto vietate: esclusa
  nodo(7, 45.07, 7.68, { motorcar: 'no', 'socket:type2': '2' }),
  // doppione dello stesso elemento
  nodo(1, 44.50811, 11.37622, { name: 'Voltaria Hub', 'socket:type2_combo': '4' })
];

test('potenze scritte in tanti modi', () => {
  assert.equal(potenzaKw('22 kW'), 22);
  assert.equal(potenzaKw('22kW'), 22);
  assert.equal(potenzaKw('7,4 kW'), 7.4);
  assert.equal(potenzaKw('11 kW;22 kW'), 22);
  assert.equal(potenzaKw('22000 W'), 22);
  assert.equal(potenzaKw('50'), 50);
  assert.equal(potenzaKw('3.7'), 3.7);
  assert.equal(potenzaKw('22 kVA'), 22);
  assert.equal(potenzaKw('boh'), null);
  assert.equal(potenzaKw(null), null);
  assert.equal(potenzaKw('0 kW'), null);
});

test('colonnina normalizzata', () => {
  const c = normalizza(ESEMPI[0]);
  assert.deepEqual(c, [
    'n1', 44.50811, 11.37622, 'Voltaria Hub', 'Voltaria', 150,
    [['C', 4, 150], ['T', 2, 22]],
    BIT.H24, 'Viale Europa 120, Bologna', null
  ]);
});

test('potenze stimate, operatore dalla rete', () => {
  const c = normalizza(ESEMPI[1]);
  assert.equal(c[4], 'Ricarica Più');
  assert.deepEqual(c[6], [['H', 1, 50], ['T', 1, 22]]);
  assert.equal(c[5], 50);
  assert.equal(c[7] & BIT.POTENZA_STIMATA, BIT.POTENZA_STIMATA);
});

test('escluse private, solo bici e auto vietate', () => {
  assert.equal(normalizza(ESEMPI[2]), null);
  assert.equal(normalizza(ESEMPI[3]), null);
  assert.equal(normalizza(ESEMPI[6]), null);
});

test('aree, clienti, gratuite e orari', () => {
  const c = normalizza(ESEMPI[4]);
  assert.equal(c[0], 'w5');
  assert.equal(c[1], 41.90251);
  assert.equal(c[5], 7.4);
  assert.equal(c[7], BIT.CLIENTI | BIT.GRATUITA);
  assert.equal(c[9], 'Mo-Sa 08:00-20:00');
});

test('potenza della stazione quando mancano le prese', () => {
  const c = normalizza(ESEMPI[5]);
  assert.deepEqual(c[6], []);
  assert.equal(c[5], 350);
  assert.equal(c[7] & BIT.POTENZA_STIMATA, 0);
});

test('tessere di mezzo grado e niente doppioni', () => {
  assert.equal(chiaveTessera(44.50811, 11.37622), '89_22');
  assert.equal(chiaveTessera(41.90251, 12.49636), '83_24');
  const { indice, tessere, scartate } = elaboraColonnine(ESEMPI, new Date('2026-09-28T06:40:00Z'));
  assert.equal(indice.conteggio, 4);
  assert.equal(scartate, 3);
  assert.deepEqual(indice.tessere, { '83_24': 1, '88_22': 1, '89_22': 1, '90_18': 1 });
  assert.equal(indice.generato, '2026-09-28T06:40:00.000Z');
  assert.equal(tessere.get('89_22').c[0][3], 'Voltaria Hub');
  assert.ok(indice.tariffe.casa.p > 0);
});

// tante colonnine finte, per superare la soglia minima
function tante(n) {
  return Array.from({ length: n }, (_, i) => nodo(1000 + i, 38 + (i % 80) / 10, 8 + Math.floor(i / 80) / 10, { 'socket:type2': '2' }));
}

const punGuasta = async () => {
  throw new Error('PUN non raggiungibile');
};

test('senza la PUN: OpenStreetMap una volta a settimana e riuso delle tessere pubblicate', async () => {
  const cartella = await mkdtemp(join(tmpdir(), 'goccia-ev-'));
  const primo = join(cartella, 'primo');
  let chiamate = 0;
  const scarica = async () => {
    chiamate++;
    return { elements: tante(6000) };
  };
  const comune = { scaricaDallaPun: punGuasta };
  const a = await aggiornaColonnine({ ...comune, uscita: primo, scarica, adesso: new Date('2026-09-28T06:40:00Z') });
  assert.equal(a.origine, 'overpass');
  assert.equal(a.conteggio, 6000);
  assert.equal(chiamate, 1);

  // il giorno dopo: la PUN ancora non risponde, niente Overpass, ricopiamo le tessere
  const secondo = join(cartella, 'secondo');
  const b = await aggiornaColonnine({ ...comune, uscita: secondo, cartella: primo, scarica, adesso: new Date('2026-09-29T06:40:00Z') });
  assert.equal(b.origine, 'precedenti');
  assert.equal(b.conteggio, 6000);
  assert.equal(chiamate, 1);
  const indice = JSON.parse(await readFile(join(secondo, 'dati/v1/ev/indice.json'), 'utf8'));
  assert.equal(indice.generato, '2026-09-28T06:40:00.000Z');
  assert.equal(indice.fonte, 'OpenStreetMap');
  assert.equal((await readdir(join(secondo, 'dati/v1/ev/t'))).length, a.tessere);

  // dopo una settimana Overpass risponde male: teniamo quelle vecchie
  const terzo = join(cartella, 'terzo');
  const guasto = async () => {
    throw new Error('HTTP 504');
  };
  const c = await aggiornaColonnine({ ...comune, uscita: terzo, cartella: secondo, scarica: guasto, adesso: new Date('2026-10-06T06:40:00Z') });
  assert.equal(c.origine, 'precedenti');
  assert.match(c.avviso, /PUN non raggiungibile/);
  assert.match(c.avviso, /HTTP 504/);
  assert.equal(c.conteggio, 6000);

  // risposta troppo piccola: sospetta, teniamo quelle vecchie
  const quarto = join(cartella, 'quarto');
  const poche = async () => ({ elements: tante(10) });
  const d = await aggiornaColonnine({ ...comune, uscita: quarto, cartella: secondo, scarica: poche, adesso: new Date('2026-10-06T06:40:00Z') });
  assert.equal(d.origine, 'precedenti');

  // primo avvio senza PUN e senza Overpass: nessuna colonnina, ma nessun errore
  const vuoto = await aggiornaColonnine({ ...comune, uscita: join(cartella, 'vuoto'), scarica: guasto });
  assert.equal(vuoto.origine, 'nessuna');
});

/** Un punto della PUN come lo restituisce /v1/chargepoints/group. */
function punto(evse, luogo, { lat = 44.5, lon = 11.35, standard = 'IEC_62196_T2', kw = 22, stato = 'AVAILABLE', tariffe = null, realTime = true, h24 = true } = {}) {
  return {
    location: {
      _id: luogo,
      address: 'VIA EMILIA 12',
      city: 'BOLOGNA',
      state: 'Bologna',
      coordinates: { latitude: lat, longitude: lon },
      party_id: 'XYZ',
      opening_times: h24 ? { twentyfourseven: true } : { twentyfourseven: false, regular_hours: [1, 2, 3, 4, 5].map((g) => ({ weekday: g, period_begin: '08:00', period_end: '20:00' })) }
    },
    locationId: luogo,
    businessName: 'ACEA ENERGIA S.P.A.',
    status: stato,
    connectors: [{ standard, max_electric_power: kw * 1000 }],
    coordinates: { latitude: lat, longitude: lon },
    publicationStatus: 'PUBLISHED',
    realTime,
    ...(tariffe ? { punTariffsDetails: tariffe } : {}),
    evse_id: evse
  };
}

test('punti della PUN raggruppati in colonnine', () => {
  const dettagli = [
    punto('IT*A*1', 'L1', { standard: 'IEC_62196_T2_COMBO', kw: 150, tariffe: { acTariff: null, dcTariff: { energy: 0.69 }, hpcTariff: { energy: 0.79 } } }),
    // un punto in continua con due cavi: conta una volta per tipo
    { ...punto('IT*A*2', 'L1', { standard: 'IEC_62196_T2_COMBO', kw: 150 }), connectors: [
      { standard: 'IEC_62196_T2_COMBO', max_electric_power: 150000 },
      { standard: 'CHADEMO', max_electric_power: 50000 }
    ] },
    punto('IT*A*3', 'L1', { standard: 'IEC_62196_T2', kw: 22 }),
    // dismesso: non conta
    punto('IT*A*4', 'L1', { stato: 'REMOVED' }),
    // un altro luogo, con orari e senza tempo reale
    punto('IT*B*1', 'L2', { lat: 41.9, lon: 12.5, kw: 7.4, realTime: false, h24: false, tariffe: { acTariff: { energy: 0.59, parking: 0.08 } } })
  ];
  const colonnine = elaboraPun(dettagli);
  assert.equal(colonnine.length, 2);
  const a = colonnine.find((c) => c[0] === 'pL1');
  assert.deepEqual(a.slice(0, 10), [
    'pL1', 44.5, 11.35, null, 'Acea Energia', 150,
    [['C', 2, 150], ['H', 1, 50], ['T', 1, 22]],
    BIT.H24, 'Via Emilia 12, Bologna', null
  ]);
  assert.deepEqual(a[10], ['IT*A*1', 'IT*A*2', 'IT*A*3']);
  assert.deepEqual(a[11], [null, 0.69, 0.79]);
  assert.equal(a[12], 1);
  const b = colonnine.find((c) => c[0] === 'pL2');
  assert.equal(b[7] & BIT.H24, 0);
  assert.equal(b[9], 'Lun-Ven 08:00-20:00');
  assert.deepEqual(b[11], [0.59, null, null]);
  assert.equal(b[12], 0);
});

test('colonnine gratuite solo se non si paga niente', () => {
  const gratis = elaboraPun([punto('IT*G*1', 'G1', { kw: 11, tariffe: { acTariff: { energy: 0 } } })]);
  assert.equal(gratis[0][7] & BIT.GRATUITA, BIT.GRATUITA);
  assert.deepEqual(gratis[0][11], [0, null, null]);
  // energia a zero ma si paga al minuto: il prezzo al kWh non si sa e non e gratuita
  const aTempo = elaboraPun([punto('IT*T*1', 'T1', { kw: 11, tariffe: { acTariff: { energy: 0, time: 0.05 } } })]);
  assert.equal(aTempo[0][7] & BIT.GRATUITA, 0);
  assert.equal(aTempo[0][11], null);
});

test('nomi dei gestori e orari in breve', () => {
  assert.equal(nomeGestore('A2A E.MOBILITY S.R.L.'), 'A2A E.Mobility');
  assert.equal(nomeGestore('"IP SERVICES S.R.L."'), 'IP Services');
  assert.equal(nomeGestore('Enel X Way Italia srl'), 'Enel X Way Italia');
  assert.equal(nomeGestore(null), null);
  assert.equal(orariBrevi({ twentyfourseven: true }), null);
  assert.equal(orariBrevi({ regular_hours: [
    { weekday: 1, period_begin: '07:00', period_end: '21:00' },
    { weekday: 2, period_begin: '07:00', period_end: '21:00' },
    { weekday: 6, period_begin: '08:00', period_end: '13:00' }
  ] }), 'Lun-Mar 07:00-21:00; Sab 08:00-13:00');
});

test('firma SigV4 delle richieste alla PUN', () => {
  const intestazioni = firma({
    url: 'https://api.pun.piattaformaunicanazionale.it/v1/chargepoints/group',
    corpo: '["IT*A*1"]',
    credenziali: { AccessKeyId: 'ASIAESEMPIO', SecretKey: 'segreto', SessionToken: 'gettone' },
    regione: 'eu-south-1',
    adesso: new Date('2026-10-04T12:00:00Z')
  });
  assert.equal(intestazioni['x-amz-date'], '20261004T120000Z');
  assert.equal(intestazioni['x-amz-security-token'], 'gettone');
  assert.match(intestazioni.Authorization,
    /^AWS4-HMAC-SHA256 Credential=ASIAESEMPIO\/20261004\/eu-south-1\/execute-api\/aws4_request, SignedHeaders=content-type;host;x-amz-date;x-amz-security-token, Signature=[0-9a-f]{64}$/);
  assert.equal(intestazioni.host, undefined);
});

/** Tanti punti finti in luoghi diversi, per superare la soglia minima. */
function tantiPunti(n) {
  return Array.from({ length: n }, (_, i) => punto(`IT*T*${i}`, `T${Math.floor(i / 2)}`, { lat: 38 + (i % 900) / 100, lon: 8 + Math.floor(i / 900) / 10 }));
}

test('ogni giorno dalla PUN, e senza la PUN restano le tessere del giorno prima', async () => {
  const cartella = await mkdtemp(join(tmpdir(), 'goccia-pun-'));
  let chiamatePun = 0;
  let chiamateOsm = 0;
  const pun = async () => {
    chiamatePun++;
    return { elenco: 21000, dettagli: tantiPunti(21000) };
  };
  const osm = async () => {
    chiamateOsm++;
    return { elements: tante(6000) };
  };
  const primo = join(cartella, 'primo');
  const a = await aggiornaColonnine({ uscita: primo, scarica: osm, scaricaDallaPun: pun, adesso: new Date('2026-10-04T06:40:00Z') });
  assert.equal(a.origine, 'pun');
  assert.equal(a.conteggio, 10500);
  assert.equal(chiamatePun, 1);
  assert.equal(chiamateOsm, 0);
  const indice = JSON.parse(await readFile(join(primo, 'dati/v1/ev/indice.json'), 'utf8'));
  assert.equal(indice.fonte, 'PUN');
  assert.equal(indice.punti, 21000);

  // secondo passaggio dello stesso giorno: riusa
  const secondo = join(cartella, 'secondo');
  const b = await aggiornaColonnine({ uscita: secondo, cartella: primo, scarica: osm, scaricaDallaPun: pun, adesso: new Date('2026-10-04T11:05:00Z') });
  assert.equal(b.origine, 'precedenti');
  assert.equal(chiamatePun, 1);

  // il giorno dopo la PUN non risponde: niente OpenStreetMap, restano quelle di ieri
  const terzo = join(cartella, 'terzo');
  const c = await aggiornaColonnine({ uscita: terzo, cartella: primo, scarica: osm, scaricaDallaPun: punGuasta, adesso: new Date('2026-10-05T06:40:00Z') });
  assert.equal(c.origine, 'precedenti');
  assert.equal(chiamateOsm, 0);

  // risposta della PUN troppo piccola: sospetta
  const quarto = join(cartella, 'quarto');
  const poca = async () => ({ elenco: 100, dettagli: tantiPunti(100) });
  const d = await aggiornaColonnine({ uscita: quarto, cartella: primo, scarica: osm, scaricaDallaPun: poca, adesso: new Date('2026-10-05T06:40:00Z') });
  assert.equal(d.origine, 'precedenti');
  assert.match(d.avviso, /incompleta/);
});
