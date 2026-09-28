// Famiglie di carburante usate dall'app: B benzina, G gasolio, L GPL, M metano.

export const FAMIGLIE = ['B', 'G', 'L', 'M'];

const BASE = new Map([
  ['benzina', 'B'],
  ['gasolio', 'G'],
  ['gpl', 'L'],
  ['metano', 'M']
]);

/** Carburante standard ("Benzina", "Gasolio", "GPL", "Metano") -> famiglia, altrimenti null. */
export function famigliaBase(desc) {
  return BASE.get(String(desc).trim().toLowerCase()) ?? null;
}

/** Carburanti speciali (Blue Diesel, HVO, V-Power...) -> famiglia di appartenenza, se riconoscibile. */
export function famigliaSpeciale(desc) {
  const d = String(desc).toLowerCase();
  if (/diesel|gasolio|hvo/.test(d)) return 'G';
  if (/benzina|super|ottani|v.?power|perform|wr ?100|f101|plus 98|excellium/.test(d)) return 'B';
  if (/l-gnc|metano|gnc/.test(d)) return 'M';
  if (/gpl/.test(d)) return 'L';
  return null;
}

/** Prezzi plausibili (euro al litro, al kg per il metano). Fuori da qui e un errore di battitura. */
export const LIMITI = {
  B: [0.9, 3.9],
  G: [0.9, 3.9],
  L: [0.3, 1.6],
  M: [0.5, 3.9],
  altro: [0.3, 5]
};

export function plausibile(famiglia, prezzo) {
  const [min, max] = LIMITI[famiglia] ?? LIMITI.altro;
  return prezzo >= min && prezzo <= max;
}

/** Oltre questi giorni un prezzo e "vecchio": resta visibile ma fuori da medie e consigli. */
export const GIORNI_VECCHIO = 8;
