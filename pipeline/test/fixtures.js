// Piccoli CSV con i casi difficili trovati nei file veri del MIMIT.

export function anagrafica(data = '2026-09-27') {
  return [
    `Estrazione del ${data}`,
    'idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine',
    '100|ROSSI CARBURANTI SRL|Agip Eni|Stradale|19829 BOLOGNA EMILIA|VIA EMILIA LEVANTE 214  40139|BOLOGNA|BO|44.4891|11.3790',
    '101|STOIL SIMPLE|Pompe Bianche|Stradale|STOIL SIMPLE | gestori.prezzibenzina.it|STR. PROV.LE 82 SPINETTA SALE  15122|ALESSANDRIA|AL|44.917|8.7006',
    '102|PRADELLI - MONTEOMBRARO | gestori.prezzibenzina.it|Pompe Bianche|Stradale|PRADELLI - MONTEOMBRARO | gestori.prezzibenzina.it|Via dei Martiri 255 41059|ZOCCA|MO|44.379|11.0046',
    '103|ENIMOOV S.P.A.|Q8|Stradale|19834\tCASALECCHIO|VIA PORRETTANA 7|CASALECCHIO DI RENO|BO|44.4760|11.2800',
    '104|AUTOGRILL|Esso|Autostradale|AREA PIOPPA EST|A14 KM 12|BOLOGNA|BO|44.5300|11.4200',
    '105|SENZA POSTO|Tamoil|Stradale|ERRATO|VIA NESSUNA|NESSUNO|BO|0|0',
    '106|VECCHIO SRL|Api-Ip|Stradale|IP VECCHIO|VIA DEI MILLE 7|BOLOGNA|BO|44.5010|11.3500',
    '107|TROPPO CORTA|Esso|Stradale|RIGA|SPEZZATA',
    ''
  ].join('\n');
}

export function prezzi(data = '2026-09-27', { gasolioAurora = '1.689' } = {}) {
  return [
    `Estrazione del ${data}`,
    'idImpianto|descCarburante|prezzo|isSelf|dtComu',
    `100|Gasolio|${gasolioAurora}|1|26/09/2026 07:42:11`,
    '100|Gasolio|1.859|0|26/09/2026 07:42:11',
    '100|Gasolio|1.999|1|20/09/2026 07:00:00',
    '100|Benzina|1.779|1|26/09/2026 07:42:11',
    '100|Blue Diesel|1.839|1|26/09/2026 07:42:11',
    '100|GPL|0.719|0|26/09/2026 07:42:11',
    '103|Gasolio|1.729|1|25/09/2026 18:00:00',
    '103|Metano|0.1|0|25/09/2026 18:00:00',
    '104|Gasolio|1.889|1|26/09/2026 06:00:00',
    '106|Gasolio|1.699|1|01/06/2026 09:00:00',
    '999|Gasolio|1.700|1|26/09/2026 07:00:00',
    '101|Gasolio|1.650|1|26/09/2026 08:00:00',
    '102|Benzina|1.800|1|26/09/2026 08:00:00',
    ''
  ].join('\n');
}
