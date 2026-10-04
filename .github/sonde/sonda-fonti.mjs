// Sonda delle fonti in tempo reale: interroga l'Osservaprezzi del MIMIT e la Piattaforma
// Unica Nazionale delle colonnine (PUN) e salva esempi di risposta, tempi e freschezza.
// Serve a conoscere formati e limiti prima di usarle nell'app. Node 20, nessuna dipendenza.
//
//   node .github/sonde/sonda-fonti.mjs /tmp/sonde

import { createHash, createHmac } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';

const USCITA = process.argv[2] || 'sonde';
mkdirSync(USCITA, { recursive: true });
const AGENTE = 'Goccia/0.1 (+https://github.com/0Thinker0/goccia)';
const righe = [];
const nota = (s) => { righe.push(s); console.log(s); };
const salva = (nome, dati) => writeFileSync(`${USCITA}/${nome}`, typeof dati === 'string' ? dati : JSON.stringify(dati, null, 2));

async function prova(nome, fn) {
  const t0 = Date.now();
  try {
    const r = await fn();
    nota(`${nome}: ok in ${Date.now() - t0} ms`);
    return r;
  } catch (e) {
    nota(`${nome}: ERRORE ${e.message}`);
    return null;
  }
}

async function json(risposta) {
  const testo = await risposta.text();
  if (!risposta.ok) throw new Error(`HTTP ${risposta.status}: ${testo.slice(0, 300)}`);
  try {
    return JSON.parse(testo);
  } catch {
    throw new Error(`non JSON: ${testo.slice(0, 300)}`);
  }
}

// ------------------------------------------------------------------ Osservaprezzi

const OSPZ = 'https://carburanti.mise.gov.it/ospzApi/';

async function ospz(percorso, corpo) {
  const r = await fetch(OSPZ + percorso, {
    method: corpo ? 'POST' : 'GET',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'User-Agent': AGENTE },
    body: corpo ? JSON.stringify(corpo) : undefined,
  });
  return json(r);
}

function freschezza(risultati, campo = 'insertDate') {
  const ora = Date.now();
  const ore = risultati.map((r) => (ora - Date.parse(r[campo])) / 3_600_000).filter((x) => Number.isFinite(x)).sort((a, b) => a - b);
  if (!ore.length) return 'nessuna data';
  const quota = (limite) => `${Math.round((ore.filter((x) => x <= limite).length / ore.length) * 100)}%`;
  return `${ore.length} impianti; comunicato entro 6 ore ${quota(6)}, entro 24 ore ${quota(24)}, entro 3 giorni ${quota(72)}, ` +
    `entro 8 giorni ${quota(192)}, entro 30 giorni ${quota(720)}; mediana ${ore[Math.floor(ore.length / 2)].toFixed(1)} ore`;
}

async function osservaprezzi() {
  const bologna = { lat: 44.4938, lng: 11.3426 };
  for (const raggio of [1, 5, 10]) {
    const zona = await prova(`ospz search/zone raggio ${raggio}`, () =>
      ospz('search/zone', { points: [bologna], radius: raggio, priceOrder: 'asc', fuelType: '0-x' }));
    if (!zona) continue;
    nota(`  risultati: ${zona.results?.length}; freschezza: ${freschezza(zona.results || [])}`);
    if (raggio === 5) {
      salva('ospz-zona-5km.json', { ...zona, results: (zona.results || []).slice(0, 6), conteggio: zona.results?.length });
      const id = zona.results?.[0]?.id;
      if (id) {
        const dettaglio = await prova(`ospz registry/servicearea/${id}`, () => ospz(`registry/servicearea/${id}`));
        if (dettaglio) salva('ospz-dettaglio.json', dettaglio);
      }
      // un'area di servizio autostradale, per vedere i servizi dichiarati
      const autostrada = (zona.results || []).find((r) => /autostrada/i.test(r.address || ''));
      if (autostrada) {
        const d = await prova(`ospz servicearea autostradale ${autostrada.id}`, () => ospz(`registry/servicearea/${autostrada.id}`));
        if (d) salva('ospz-dettaglio-autostrada.json', d);
      }
    }
  }
  // una zona fatta da piu punti (il corridoio di un viaggio?)
  const corridoio = await prova('ospz search/zone tre punti', () => ospz('search/zone', {
    points: [{ lat: 44.4938, lng: 11.3426 }, { lat: 44.40, lng: 11.26 }, { lat: 44.30, lng: 11.23 }],
    radius: 2, priceOrder: 'asc', fuelType: '2-1',
  }));
  if (corridoio) nota(`  tre punti, gasolio self: ${corridoio.results?.length} risultati`);
  const servizi = await prova('ospz registry/services', () => ospz('registry/services'));
  if (servizi) salva('ospz-servizi.json', servizi);
  const carburanti = await prova('ospz registry/fuels', () => ospz('registry/fuels'));
  if (carburanti) salva('ospz-carburanti.json', carburanti);
}

// ------------------------------------------------------------------ PUN

const SITO_PUN = 'https://www.piattaformaunicanazionale.it';
const API_PUN = 'https://api.pun.piattaformaunicanazionale.it';
const REGIONE_NOTA = 'eu-south-1';
const POOL_NOTO = 'eu-south-1:e3b2ab05-2046-43dd-8ed0-c0f14c69d507';

const sha256 = (s) => createHash('sha256').update(s, 'utf8').digest('hex');
const hmac = (chiave, s) => createHmac('sha256', chiave).update(s, 'utf8').digest();

async function cognito(regione, operazione, corpo) {
  const r = await fetch(`https://cognito-identity.${regione}.amazonaws.com/`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-amz-json-1.1', 'X-Amz-Target': `AWSCognitoIdentityService.${operazione}` },
    body: JSON.stringify(corpo),
  });
  return json(r);
}

/** POST firmato SigV4 verso l'API Gateway della PUN. */
async function postFirmato(percorso, dati, credenziali, regione) {
  const url = new URL(API_PUN + percorso);
  const corpo = JSON.stringify(dati);
  const amz = new Date().toISOString().replace(/[:-]|\.\d{3}/g, '');
  const giorno = amz.slice(0, 8);
  const intestazioni = {
    'content-type': 'application/json',
    host: url.host,
    'x-amz-date': amz,
    'x-amz-security-token': credenziali.SessionToken,
  };
  const nomi = Object.keys(intestazioni).sort();
  const canonica = ['POST', url.pathname, '', nomi.map((k) => `${k}:${intestazioni[k]}\n`).join(''), nomi.join(';'), sha256(corpo)].join('\n');
  const ambito = `${giorno}/${regione}/execute-api/aws4_request`;
  const daFirmare = ['AWS4-HMAC-SHA256', amz, ambito, sha256(canonica)].join('\n');
  let k = hmac(`AWS4${credenziali.SecretKey}`, giorno);
  k = hmac(k, regione);
  k = hmac(k, 'execute-api');
  k = hmac(k, 'aws4_request');
  const firma = createHmac('sha256', k).update(daFirmare, 'utf8').digest('hex');
  const { host, ...senzaHost } = intestazioni;
  return fetch(url, {
    method: 'POST',
    headers: {
      ...senzaHost,
      'User-Agent': AGENTE,
      Authorization: `AWS4-HMAC-SHA256 Credential=${credenziali.AccessKeyId}/${ambito}, SignedHeaders=${nomi.join(';')}, Signature=${firma}`,
    },
    body: corpo,
  });
}

async function pun() {
  let regione = REGIONE_NOTA;
  let pool = POOL_NOTO;
  const config = await prova('pun config.json', async () => json(await fetch(`${SITO_PUN}/config.json`, { headers: { 'User-Agent': AGENTE } })));
  if (config) {
    salva('pun-config.json', config);
    const testo = JSON.stringify(config);
    pool = testo.match(/[a-z]{2}-[a-z]+-\d:[0-9a-f-]{36}/)?.[0] || pool;
    regione = pool.split(':')[0];
  }
  nota(`  regione ${regione}, pool ${pool}`);
  const id = await prova('pun cognito GetId', () => cognito(regione, 'GetId', { IdentityPoolId: pool }));
  if (!id) return;
  const cred = await prova('pun cognito GetCredentialsForIdentity', () => cognito(regione, 'GetCredentialsForIdentity', { IdentityId: id.IdentityId }));
  if (!cred) return;
  const c = cred.Credentials;
  nota(`  credenziali fino a ${new Date(c.Expiration * 1000).toISOString()}`);

  const pagina = await prova('pun map/search pagina 0 da 50', async () => json(await postFirmato('/v1/chargepoints/public/map/search', { page: 0, size: 50 }, c, regione)));
  if (!pagina) return;
  salva('pun-map-search.json', { ...pagina, content: (pagina.content || []).slice(0, 8) });
  nota(`  totale ${pagina.totalElements} elementi in ${pagina.totalPages} pagine; chiavi dell'elemento: ${Object.keys(pagina.content?.[0] || {}).join(', ')}`);

  // filtri per zona? confrontiamo il totale con alcune varianti
  const varianti = {
    raggio: { page: 0, size: 5, latitude: 44.4938, longitude: 11.3426, radius: 5000 },
    distanza: { page: 0, size: 5, lat: 44.4938, lon: 11.3426, distance: 5 },
    riquadro: { page: 0, size: 5, minLatitude: 44.45, maxLatitude: 44.53, minLongitude: 11.28, maxLongitude: 11.40 },
    bounds: { page: 0, size: 5, bounds: { north: 44.53, south: 44.45, east: 11.40, west: 11.28 } },
    bbox: { page: 0, size: 5, bbox: [11.28, 44.45, 11.40, 44.53] },
    citta: { page: 0, size: 5, city: 'Bologna' },
  };
  for (const [nome, corpo] of Object.entries(varianti)) {
    const r = await prova(`pun map/search variante ${nome}`, async () => json(await postFirmato('/v1/chargepoints/public/map/search', corpo, c, regione)));
    if (r) nota(`  ${nome}: totale ${r.totalElements}`);
  }

  const ids = (pagina.content || []).map((e) => e.evse_id || e.evseId || e.id).filter(Boolean).slice(0, 20);
  if (ids.length) {
    const gruppo = await prova('pun group 20', async () => json(await postFirmato('/v1/chargepoints/group', ids, c, regione)));
    if (gruppo) {
      salva('pun-group.json', gruppo.slice ? gruppo.slice(0, 6) : gruppo);
      const stati = {};
      for (const g of gruppo) stati[`${g.status}${g.realTime ? ' (tempo reale)' : ''}`] = (stati[`${g.status}${g.realTime ? ' (tempo reale)' : ''}`] || 0) + 1;
      nota(`  stati: ${JSON.stringify(stati)}`);
      nota(`  chiavi del record: ${Object.keys(gruppo[0] || {}).join(', ')}`);
    }
  }

  // quanto costa scaricare tutto: una pagina da 1000
  const grande = await prova('pun map/search pagina da 1000', async () => json(await postFirmato('/v1/chargepoints/public/map/search', { page: 0, size: 1000 }, c, regione)));
  if (grande) {
    const conCoordinate = (grande.content || []).filter((e) => e.coordinates || e.latitude || e.location).length;
    nota(`  pagina da 1000: ${grande.content?.length} elementi, con coordinate ${conCoordinate}`);
  }
}

await osservaprezzi();
await pun();
salva('riassunto.txt', righe.join('\n') + '\n');
