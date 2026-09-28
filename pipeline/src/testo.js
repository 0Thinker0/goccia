// Pulizia dei testi dell'anagrafica MIMIT: tabulazioni, "|" dentro i nomi,
// suffissi di servizio, maiuscolo urlato trasformato in un titolo leggibile.

const MINUSCOLE = new Set([
  'di', 'del', 'della', 'dello', 'dei', 'degli', 'delle', 'e', 'ed', 'a', 'al', 'alla', 'allo',
  'ai', 'agli', 'alle', 'da', 'dal', 'dalla', 'dai', 'in', 'nel', 'nella', 'per', 'su', 'sul',
  'sulla', 'con', 'tra', 'fra', 'lo', 'la', 'le', 'il', 'gli', 'sotto', 'sopra'
]);

const SIGLE_STRADE = /^(s\.?s\.?|s\.?p\.?|s\.?r\.?|s\.?c\.?|s\.?s\.?p\.?)$/;

const cap = (w) => w.charAt(0).toUpperCase() + w.slice(1);

/** Rimuove tabulazioni, riferimenti al portale gestori e spazi doppi. */
export function pulisci(s) {
  return String(s ?? '')
    .replace(/\t/g, ' ')
    .replace(/\|?\s*gestori\.prezzibenzina\.it/gi, '')
    .replace(/\s*\|\s*$/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/[\s,;|-]+$/, '')
    .trim();
}

/**
 * Converte un testo tutto maiuscolo in un titolo leggibile.
 * Se il testo contiene gia minuscole lo lascia com'e.
 */
export function titolo(s, { indirizzo = false } = {}) {
  if (!s) return s;
  // testi gia scritti con maiuscole e minuscole restano com'erano
  if (/[a-zà-ÿ]/.test(s) && /[A-ZÀ-Þ]/.test(s)) return s;
  const parole = s.toLowerCase().split(' ');
  return parole
    .map((p, i) => {
      if (!p) return p;
      if (/\d/.test(p)) return p.toUpperCase();
      const lettere = p.replace(/[^a-zà-ÿ]/g, '');
      if (indirizzo && (lettere === 'snc' || lettere === 'sn')) return 'snc';
      if (indirizzo && lettere === 'km') return p;
      if (SIGLE_STRADE.test(p)) return p.toUpperCase();
      if (i > 0 && MINUSCOLE.has(p)) return p;
      const apostrofo = /^([a-zà-ÿ]{1,4}['’])(.+)$/.exec(p);
      if (apostrofo) return (i === 0 ? cap(apostrofo[1]) : apostrofo[1]) + cap(apostrofo[2]);
      return p
        .split('-')
        .map((pezzo) => cap(pezzo))
        .join('-');
    })
    .join(' ');
}

/**
 * Indirizzo leggibile: senza CAP e comune ripetuti in coda, con maiuscole normali.
 * "Via Via De Gasperi 1 40065, Pianoro (bo) 1" -> "Via De Gasperi 1"
 * "Autostrada A13 Bologna-Padova, Km. 11+700, dir. Sud - 40010" -> "... dir. Sud"
 */
export function pulisciIndirizzo(s) {
  const base = pulisci(s)
    .replace(/\s+\d{5}$/, '')
    .replace(/\s+\d{5},\s*[^,()]+\(\s*[a-z]{2}\s*\)(\s*\S+)?$/i, '')
    .replace(/\s+\d{5}$/, '')
    .replace(/^(\p{L}+)\s+\1\b/iu, '$1')
    .replace(/[\s,;|-]+$/, '')
    .trim();
  return titolo(base, { indirizzo: true });
}

/** Nome impianto senza codici numerici iniziali ("19829 AGRIGENTO" -> "Agrigento"). */
export function pulisciNome(s) {
  const base = pulisci(s).replace(/^\d{3,}\s*[-–]?\s*/, '');
  return titolo(base);
}

const BANDIERE = new Map([
  ['pompe bianche', 'Pompa bianca'],
  ['pompa bianca', 'Pompa bianca'],
  ['api-ip', 'IP'],
  ['bpetrol', 'B Petrol']
]);

/** Nome della bandiera come lo mostriamo nell'app. */
export function nomeBandiera(s) {
  const base = pulisci(s);
  const noto = BANDIERE.get(base.toLowerCase());
  if (noto) return noto;
  if (!base) return 'Pompa bianca';
  if (base.length > 3 && base === base.toUpperCase()) return titolo(base);
  return base;
}

export function comune(s) {
  return titolo(pulisci(s));
}
