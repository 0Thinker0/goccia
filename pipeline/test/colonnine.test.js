import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { BIT, aggiornaColonnine, chiaveTessera, elaboraColonnine, normalizza, potenzaKw } from '../src/colonnine.js';

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

test('aggiornamento settimanale e riuso delle tessere pubblicate', async () => {
  const cartella = await mkdtemp(join(tmpdir(), 'goccia-ev-'));
  const primo = join(cartella, 'primo');
  let chiamate = 0;
  const scarica = async () => {
    chiamate++;
    return { elements: tante(6000) };
  };
  const a = await aggiornaColonnine({ uscita: primo, scarica, adesso: new Date('2026-09-28T06:40:00Z') });
  assert.equal(a.origine, 'overpass');
  assert.equal(a.conteggio, 6000);
  assert.equal(chiamate, 1);

  // il giorno dopo: niente Overpass, ricopiamo le tessere
  const secondo = join(cartella, 'secondo');
  const b = await aggiornaColonnine({ uscita: secondo, cartella: primo, scarica, adesso: new Date('2026-09-29T06:40:00Z') });
  assert.equal(b.origine, 'precedenti');
  assert.equal(b.conteggio, 6000);
  assert.equal(chiamate, 1);
  const indice = JSON.parse(await readFile(join(secondo, 'dati/v1/ev/indice.json'), 'utf8'));
  assert.equal(indice.generato, '2026-09-28T06:40:00.000Z');
  assert.equal((await readdir(join(secondo, 'dati/v1/ev/t'))).length, a.tessere);

  // dopo una settimana Overpass risponde male: teniamo quelle vecchie
  const terzo = join(cartella, 'terzo');
  const guasto = async () => {
    throw new Error('HTTP 504');
  };
  const c = await aggiornaColonnine({ uscita: terzo, cartella: secondo, scarica: guasto, adesso: new Date('2026-10-06T06:40:00Z') });
  assert.equal(c.origine, 'precedenti');
  assert.equal(c.avviso, 'HTTP 504');
  assert.equal(c.conteggio, 6000);

  // risposta troppo piccola: sospetta, teniamo quelle vecchie
  const quarto = join(cartella, 'quarto');
  const poche = async () => ({ elements: tante(10) });
  const d = await aggiornaColonnine({ uscita: quarto, cartella: secondo, scarica: poche, adesso: new Date('2026-10-06T06:40:00Z') });
  assert.equal(d.origine, 'precedenti');

  // primo avvio senza Overpass: nessuna colonnina, ma nessun errore
  const vuoto = await aggiornaColonnine({ uscita: join(cartella, 'vuoto'), scarica: guasto });
  assert.equal(vuoto.origine, 'nessuna');
});
