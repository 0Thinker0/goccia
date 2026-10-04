// Colonnine dalla Piattaforma Unica Nazionale dei punti di ricarica (PUN, GSE e MASE).
//
// E il registro in cui i gestori devono inserire ogni punto di ricarica pubblico, con prese,
// potenza, stato e tariffe. Non c'e un download ufficiale: il portale espone un'API REST a
// cui si accede come ospite (credenziali temporanee di AWS Cognito, nessun login) firmando le
// richieste con SigV4, come fa anche il cruscotto di AgID. Licenza non dichiarata: secondo AgID
// i dati pubblici senza licenza valgono come CC BY 4.0, citando il GSE.
//
// La PUN elenca i singoli punti (EVSE): li raggruppiamo per luogo (locationId) nelle stesse
// colonnine compatte che usiamo per OpenStreetMap, piu gli identificativi dei punti (per lo
// stato in tempo reale nell'app) e le tariffe dichiarate dal gestore.

import { createHash, createHmac } from 'node:crypto';
import { conLimite } from './stato.js';
import { titolo } from './testo.js';

export const SITO = 'https://www.piattaformaunicanazionale.it';
export const API = 'https://api.pun.piattaformaunicanazionale.it';
const REGIONE_NOTA = 'eu-south-1';
const POOL_NOTO = 'eu-south-1:e3b2ab05-2046-43dd-8ed0-c0f14c69d507';
const PAGINA = 1000;
const LOTTO = 100;
/** Sotto questa soglia la risposta e incompleta: meglio i dati del giorno prima. */
export const MINIMO_PUNTI = 20_000;

const AGENTE = `Goccia/1.0 (app gratuita prezzi carburanti e colonnine; https://github.com/${process.env.GITHUB_REPOSITORY ?? 'goccia'})`;

/** Standard OCPI -> codici delle prese dell'app (vedi colonnine.js). */
const PRESE = {
  IEC_62196_T2_COMBO: 'C',
  IEC_62196_T2: 'T',
  CHADEMO: 'H',
  DOMESTIC_F: 'S',
  DOMESTIC_L: 'S',
  IEC_60309_2_single_16: 'S',
  IEC_60309_2_three_16: 'A',
  IEC_60309_2_three_32: 'A',
  IEC_60309_2_three_64: 'A',
  IEC_62196_T3A: 'A',
  IEC_62196_T3C: 'A',
  IEC_62196_T1: 'A',
  IEC_62196_T1_COMBO: 'A',
  TESLA_R: 'X',
  TESLA_S: 'X',
  NACS: 'A'
};

/** Stati di punti che non contano (dismessi o non ancora costruiti). */
const ESCLUSI = new Set(['REMOVED', 'PLANNED']);

const arrotonda = (n, cifre) => Math.round(n * 10 ** cifre) / 10 ** cifre;
const sha256 = (s) => createHash('sha256').update(s, 'utf8').digest('hex');
const hmac = (chiave, s) => createHmac('sha256', chiave).update(s, 'utf8').digest();

async function json(risposta) {
  const testo = await risposta.text();
  if (!risposta.ok) throw new Error(`HTTP ${risposta.status}: ${testo.slice(0, 200)}`);
  return JSON.parse(testo);
}

/** Firma SigV4 per l'API Gateway (servizio execute-api). Esportata per i test. */
export function firma({ url, corpo, credenziali, regione, adesso = new Date() }) {
  const u = new URL(url);
  const amz = adesso.toISOString().replace(/[:-]|\.\d{3}/g, '');
  const giorno = amz.slice(0, 8);
  const intestazioni = {
    'content-type': 'application/json',
    host: u.host,
    'x-amz-date': amz,
    'x-amz-security-token': credenziali.SessionToken
  };
  const nomi = Object.keys(intestazioni).sort();
  const canonica = ['POST', u.pathname, '', nomi.map((k) => `${k}:${intestazioni[k]}\n`).join(''), nomi.join(';'), sha256(corpo)].join('\n');
  const ambito = `${giorno}/${regione}/execute-api/aws4_request`;
  const daFirmare = ['AWS4-HMAC-SHA256', amz, ambito, sha256(canonica)].join('\n');
  let k = hmac(`AWS4${credenziali.SecretKey}`, giorno);
  k = hmac(k, regione);
  k = hmac(k, 'execute-api');
  k = hmac(k, 'aws4_request');
  const firmaHex = createHmac('sha256', k).update(daFirmare, 'utf8').digest('hex');
  const { host, ...resto } = intestazioni;
  return {
    ...resto,
    Authorization: `AWS4-HMAC-SHA256 Credential=${credenziali.AccessKeyId}/${ambito}, SignedHeaders=${nomi.join(';')}, Signature=${firmaHex}`
  };
}

async function cognito(regione, operazione, corpo) {
  const r = await fetch(`https://cognito-identity.${regione}.amazonaws.com/`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-amz-json-1.1', 'X-Amz-Target': `AWSCognitoIdentityService.${operazione}` },
    body: JSON.stringify(corpo),
    signal: AbortSignal.timeout(30_000)
  });
  return json(r);
}

/** Sessione verso l'API della PUN, con rinnovo delle credenziali di ospite (durano un'ora). */
export async function sessione() {
  let regione = REGIONE_NOTA;
  let pool = POOL_NOTO;
  try {
    const config = await json(await fetch(`${SITO}/config.json`, { headers: { 'User-Agent': AGENTE }, signal: AbortSignal.timeout(30_000) }));
    pool = JSON.stringify(config).match(/[a-z]{2}-[a-z]+-\d:[0-9a-f-]{36}/)?.[0] ?? pool;
    regione = pool.split(':')[0];
  } catch (errore) {
    console.warn(`  PUN config.json: ${errore.message}, uso i valori noti`);
  }
  let credenziali = null;
  let scadenza = 0;
  async function rinnova() {
    const { IdentityId } = await cognito(regione, 'GetId', { IdentityPoolId: pool });
    const { Credentials } = await cognito(regione, 'GetCredentialsForIdentity', { IdentityId });
    credenziali = Credentials;
    scadenza = Credentials.Expiration * 1000;
  }
  async function post(percorso, dati, tentativi = 3) {
    for (let i = 1; ; i++) {
      if (!credenziali || Date.now() > scadenza - 120_000) await rinnova();
      const url = `${API}${percorso}`;
      const corpo = JSON.stringify(dati);
      try {
        const r = await fetch(url, {
          method: 'POST',
          headers: { ...firma({ url, corpo, credenziali, regione }), 'User-Agent': AGENTE },
          body: corpo,
          signal: AbortSignal.timeout(60_000)
        });
        if (r.status === 401 || r.status === 403) credenziali = null;
        return await json(r);
      } catch (errore) {
        if (i >= tentativi) throw errore;
        await new Promise((ok) => setTimeout(ok, 1500 * i));
      }
    }
  }
  return { post, regione };
}

/** Tutti i punti di ricarica con i dettagli: prima l'elenco (paginato), poi i dettagli a lotti. */
export async function scaricaPun({ apri = sessione } = {}) {
  const s = await apri();
  const elenco = [];
  for (let pagina = 0; ; pagina++) {
    const r = await s.post('/v1/chargepoints/public/map/search', { page: pagina, size: PAGINA });
    elenco.push(...(r.content ?? []));
    if (r.last || !r.content?.length || pagina > 400) break;
  }
  const id = [...new Set(elenco.filter((e) => e.evse_id && !ESCLUSI.has(e.status)).map((e) => e.evse_id))];
  const lotti = [];
  for (let i = 0; i < id.length; i += LOTTO) lotti.push(id.slice(i, i + LOTTO));
  const dettagli = [];
  let falliti = 0;
  await conLimite(lotti, 4, async (lotto) => {
    try {
      const r = await s.post('/v1/chargepoints/group', lotto);
      if (Array.isArray(r)) dettagli.push(...r);
    } catch (errore) {
      // qualche lotto perso non rovina tutto; troppi si
      falliti++;
      if (falliti > Math.max(3, lotti.length * 0.05)) throw errore;
    }
  });
  if (falliti) console.warn(`  PUN: ${falliti} lotti di dettagli non scaricati su ${lotti.length}`);
  return { elenco: elenco.length, dettagli };
}

/** "A2A E.MOBILITY S.R.L." -> "A2A E.Mobility"; le sigle corte restano maiuscole ("IP Services"). */
export function nomeGestore(s) {
  if (!s) return null;
  const senzaForma = String(s)
    .replace(/["“”]/g, '')
    .replace(/[\s,]+(s\.?\s?r\.?\s?l\.?s?|s\.?\s?p\.?\s?a\.?|s\.?\s?n\.?\s?c\.?|s\.?\s?a\.?\s?s\.?|soc\.?\s?coop\.?|gmbh|ag|bv|b\.v\.|sa|s\.a\.|ltd|inc)\.?\s*$/i, '')
    .trim();
  if (!senzaForma) return null;
  const parole = senzaForma.split(/\s+/);
  const scritto = titolo(senzaForma).split(/\s+/);
  return scritto
    .map((p, i) => {
      const originale = parole[i] ?? p;
      if (originale.length <= 3 && /^[A-Z0-9]+$/.test(originale)) return originale;
      return p.replace(/\.(\p{Ll})/gu, (_, c) => `.${c.toUpperCase()}`);
    })
    .join(' ');
}

const GIORNI = ['', 'Lun', 'Mar', 'Mer', 'Gio', 'Ven', 'Sab', 'Dom'];

/** Orari OCPI in breve: "Lun-Ven 08:00-20:00; Sab 09:00-13:00". Null se h24 o non indicati. */
export function orariBrevi(orari) {
  if (!orari || orari.twentyfourseven) return null;
  const fasce = (orari.regular_hours ?? []).filter((f) => f && f.weekday >= 1 && f.weekday <= 7);
  if (!fasce.length) return null;
  const perGiorno = new Map();
  for (const f of fasce) {
    const testo = `${f.period_begin}-${f.period_end}`;
    perGiorno.set(f.weekday, [...(perGiorno.get(f.weekday) ?? []), testo]);
  }
  const gruppi = [];
  for (let g = 1; g <= 7; g++) {
    const testo = (perGiorno.get(g) ?? []).sort().join(', ');
    if (!testo) continue;
    const ultimo = gruppi[gruppi.length - 1];
    if (ultimo && ultimo.testo === testo && ultimo.a === g - 1) ultimo.a = g;
    else gruppi.push({ da: g, a: g, testo });
  }
  return gruppi
    .map((x) => `${GIORNI[x.da]}${x.a > x.da ? `-${GIORNI[x.a]}` : ''} ${x.testo}`)
    .join('; ')
    .slice(0, 80) || null;
}

/**
 * Punti della PUN -> colonnine compatte, raggruppate per luogo.
 * [id, lat, lon, nome, operatore, kW massimi, [[presa, punti, kW]], bit, indirizzo, orari,
 *  [id dei punti], [tariffa AC, DC, HPC in euro/kWh], tempo reale 0/1]
 */
export function elaboraPun(dettagli) {
  const luoghi = new Map();
  for (const p of dettagli) {
    if (!p?.evse_id || ESCLUSI.has(p.status)) continue;
    if (p.publicationStatus && p.publicationStatus !== 'PUBLISHED') continue;
    const chiave = p.locationId ?? p.location?._id ?? p.evse_id;
    if (!luoghi.has(chiave)) luoghi.set(chiave, []);
    luoghi.get(chiave).push(p);
  }
  const risultato = [];
  for (const [chiave, punti] of luoghi) {
    const loc = punti[0].location ?? {};
    const coord = loc.coordinates ?? punti[0].coordinates ?? {};
    const la = Number(coord.latitude);
    const lo = Number(coord.longitude);
    if (!Number.isFinite(la) || !Number.isFinite(lo) || la < 35 || la > 48 || lo < 6 || lo > 19) continue;

    const prese = new Map();
    for (const p of punti) {
      const tipi = new Map();
      for (const c of p.connectors ?? []) {
        const tipo = PRESE[c.standard] ?? 'A';
        const kw = Number(c.max_electric_power) / 1000;
        tipi.set(tipo, Math.max(tipi.get(tipo) ?? 0, Number.isFinite(kw) ? kw : 0));
      }
      // ogni punto conta una volta per tipo di presa (un punto in continua puo avere piu cavi)
      for (const [tipo, kw] of tipi) {
        const prima = prese.get(tipo) ?? { n: 0, kw: 0 };
        prese.set(tipo, { n: prima.n + 1, kw: Math.max(prima.kw, kw) });
      }
    }
    const connettori = [...prese.entries()]
      .map(([tipo, x]) => [tipo, x.n, arrotonda(x.kw, 1)])
      .sort((a, b) => b[2] - a[2] || a[0].localeCompare(b[0]));
    const kw = connettori.length ? Math.max(...connettori.map((c) => c[2])) || null : null;

    let bit = 0;
    if (loc.opening_times?.twentyfourseven) bit |= 1;
    const tariffa = (classe) => {
      const valori = punti
        .map((p) => {
          const t = p.punTariffsDetails?.[classe];
          const energia = t?.energy;
          if (typeof energia !== 'number' || energia < 0 || energia >= 3) return null;
          // energia a zero ma si paga a tempo o all'avvio: non e gratis, il prezzo al kWh non si sa
          if (energia === 0 && [t.time, t.activation].some((v) => typeof v === 'number' && v > 0)) return null;
          return energia;
        })
        .filter((v) => v != null);
      if (!valori.length) return null;
      return arrotonda(Math.max(...valori), 3);
    };
    const tariffe = [tariffa('acTariff'), tariffa('dcTariff'), tariffa('hpcTariff')];
    const gratuita = tariffe.some((t) => t === 0) && tariffe.every((t) => t == null || t === 0);
    if (gratuita) bit |= 4;

    const via = (loc.address ?? '').trim();
    const citta = titolo((loc.city ?? '').trim());
    const indirizzo = [titolo(via, { indirizzo: true }), citta].filter(Boolean).join(', ') || null;
    const nome = typeof loc.name === 'string' && loc.name.trim() ? titolo(loc.name.trim()) : null;
    risultato.push([
      `p${chiave}`,
      arrotonda(la, 5),
      arrotonda(lo, 5),
      nome,
      nomeGestore(punti[0].businessName),
      kw,
      connettori,
      bit,
      indirizzo,
      orariBrevi(loc.opening_times),
      punti.map((p) => p.evse_id),
      tariffe.some((t) => t != null) ? tariffe : null,
      punti.some((p) => p.realTime) ? 1 : 0
    ]);
  }
  return risultato;
}
