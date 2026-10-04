package it.goccia.app.logica

import it.goccia.app.dati.Carburante
import it.goccia.app.dati.ClasseRicarica
import it.goccia.app.dati.Colonnina
import it.goccia.app.dati.Connettore
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.Presa
import it.goccia.app.dati.Prezzo
import it.goccia.app.dati.Speciale
import it.goccia.app.dati.StimeTariffe
import it.goccia.app.dati.TariffeColonnine
import it.goccia.app.dati.TariffeGestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TempoRealeTest {
    private val adesso = Esempi.ADESSO_SECONDI

    private fun live(id: Long, vararg prezzi: PrezzoLive) = ImpiantoLive(id, prezzi.toList(), adesso)

    @Test
    fun ilPrezzoPiuRecenteSostituisceQuelloDelMattino() {
        // prezzo del mattino comunicato ieri, quello in tempo reale stamattina
        val d = Esempi.distributore(1, gasolioSelf = 1_800, gasolioServito = 1_950, giorniFa = 1)
        val nuovo = TempoReale.applica(d, live(1, PrezzoLive("Gasolio", true, 1_779, adesso - 600)))
        assertEquals(Prezzo(1_779, adesso - 600), nuovo.prezzi[Carburante.GASOLIO]?.self)
        // il servito non c'era nella risposta: resta quello del mattino
        assertEquals(1_950, nuovo.prezzi[Carburante.GASOLIO]?.servito?.millesimi)
    }

    @Test
    fun prezziVecchiAssurdiOSaltiNonPassano() {
        val d = Esempi.distributore(2, gasolioSelf = 1_800, giorniFa = 0)
        val prima = d.prezzi[Carburante.GASOLIO]?.self
        // comunicato prima di quello che abbiamo gia
        assertEquals(prima, TempoReale.applica(d, live(2, PrezzoLive("Gasolio", true, 1_700, adesso - 30 * 3_600))).prezzi[Carburante.GASOLIO]?.self)
        // errore di battitura: 18 euro al litro
        assertEquals(prima, TempoReale.applica(d, live(2, PrezzoLive("Gasolio", true, 18_000, adesso))).prezzi[Carburante.GASOLIO]?.self)
        // salto del 40% rispetto a un prezzo di poche ore fa
        assertEquals(prima, TempoReale.applica(d, live(2, PrezzoLive("Gasolio", true, 1_080, adesso))).prezzi[Carburante.GASOLIO]?.self)
        // un altro distributore non lo tocca
        assertEquals(d, TempoReale.applica(d, live(3, PrezzoLive("Gasolio", true, 1_790, adesso))))
    }

    @Test
    fun unPrezzoFermoDaMesiSiAggiornaAncheSeCambiaMolto() {
        val d = Esempi.distributore(4, gasolioSelf = 1_500, giorniFa = 60)
        val nuovo = TempoReale.applica(d, live(4, PrezzoLive("Gasolio", true, 1_990, adesso)))
        assertEquals(1_990, nuovo.prezzi[Carburante.GASOLIO]?.self?.millesimi)
    }

    @Test
    fun carburantiNuoviESpeciali() {
        val d = Esempi.distributore(5, gasolioSelf = 1_800).copy(
            speciali = listOf(Speciale("Blue Diesel", Carburante.GASOLIO, Prezzo(1_950, adesso - 3 * Esempi.GIORNO), true)),
        )
        val nuovo = TempoReale.applica(
            d,
            live(
                5,
                PrezzoLive("Benzina", true, 1_869, adesso),
                PrezzoLive("blue diesel", true, 1_939, adesso),
                PrezzoLive("HiQ Perform+", true, 2_049, adesso),
            ),
        )
        assertEquals(1_869, nuovo.prezzi[Carburante.BENZINA]?.self?.millesimi)
        assertEquals(1_939, nuovo.speciali.first { it.nome == "Blue Diesel" }.prezzo.millesimi)
        val hiq = nuovo.speciali.first { it.nome == "HiQ Perform+" }
        assertEquals(Carburante.BENZINA, hiq.famiglia)
        assertEquals(2, nuovo.speciali.size)
    }

    @Test
    fun dueLettureSiUnisconoTenendoLaPiuRecente() {
        val a = ImpiantoLive(7, listOf(PrezzoLive("Gasolio", true, 1_800, 100), PrezzoLive("Benzina", true, 1_900, 100)), 1_000)
        val b = ImpiantoLive(7, listOf(PrezzoLive("gasolio", true, 1_780, 200)), 2_000)
        val u = TempoReale.unisci(a, b)
        assertEquals(2, u.prezzi.size)
        assertEquals(1_780, u.prezzi.first { it.nome.lowercase() == "gasolio" }.millesimi)
        assertEquals(2_000L, u.letto)
    }

    @Test
    fun laSchedaTieneLaDataVeraDiOgniPrezzo() {
        // scheda: la benzina e stata comunicata tre giorni fa, il gasolio stamattina
        val scheda = ImpiantoLive(
            8,
            listOf(PrezzoLive("Benzina", true, 1_900, adesso - 3 * Esempi.GIORNO), PrezzoLive("Gasolio", true, 1_800, adesso - 3_600)),
            adesso - 60,
            dettagliato = true,
        )
        // la ricerca per zona da a tutti la data dell'ultima comunicazione
        val ricerca = ImpiantoLive(8, listOf(PrezzoLive("Benzina", true, 1_900, adesso - 3_600), PrezzoLive("Gasolio", true, 1_790, adesso)), adesso)
        val u = TempoReale.unisci(scheda, ricerca)
        assertEquals(adesso - 3 * Esempi.GIORNO, u.prezzi.first { it.nome == "Benzina" }.comunicato)
        assertEquals(PrezzoLive("Gasolio", true, 1_790, adesso), u.prezzi.first { it.nome == "Gasolio" })
        // una scheda nuova sostituisce tutto
        assertEquals(scheda, TempoReale.unisci(ricerca, scheda))
    }

    @Test
    fun distributoriFermiDaUnMeseSonoNascosti() {
        val distributori = listOf(
            Esempi.distributore(1, gasolioSelf = 1_800, giorniFa = 0),
            Esempi.distributore(2, gasolioSelf = 1_700, giorniFa = 45),
            // il self e fermo, ma il servito e stato aggiornato
            Esempi.distributore(3, gasolioSelf = 1_600, giorniFa = 45).let { d ->
                d.copy(prezzi = mapOf(Carburante.GASOLIO to d.prezzi.getValue(Carburante.GASOLIO).copy(servito = Prezzo(1_900, adesso - 3_600))))
            },
        )
        val zona = Convenienza.zona(
            distributori, Carburante.GASOLIO, true, Coordinate(Esempi.BO_LAT, Esempi.BO_LON), 5, false, 40.0, 6.0, adesso, null,
        )
        assertEquals(listOf(1L, 3L), zona.tutte.map { it.distributore.id }.sorted())
        assertEquals(1, zona.nascosti)
        val terzo = zona.tutte.first { it.distributore.id == 3L }
        assertEquals(1_900, terzo.prezzo.millesimi)
        assertTrue(!terzo.self)
    }

    @Test
    fun firmaComeLaPipeline() {
        // stessi dati del calcolo con pun.js (la firma della pipeline e quella provata sulla PUN)
        val h = FirmaAws.intestazioni(
            url = "https://api.pun.piattaformaunicanazionale.it/v1/chargepoints/group",
            corpo = "[\"IT*ACE*E*ST*C*RSFCCT230900303*3\",\"IT*DUF*EK0018*1\"]",
            credenziali = CredenzialiAws("ASIAESEMPIO", "segreto/di+prova", "gettone-di-sessione", 0),
            regione = "eu-south-1",
            adessoMillis = java.time.Instant.parse("2026-10-04T12:30:15Z").toEpochMilli(),
        )
        assertEquals("20261004T123015Z", h["X-Amz-Date"])
        assertEquals("gettone-di-sessione", h["X-Amz-Security-Token"])
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=ASIAESEMPIO/20261004/eu-south-1/execute-api/aws4_request, " +
                "SignedHeaders=content-type;host;x-amz-date;x-amz-security-token, " +
                "Signature=8c549d814bf9c76022d28a4ac822492f7bf7e10a1c85d31c988820cd21a4d343",
            h["Authorization"],
        )
    }

    @Test
    fun tariffaTuaPoiDelGestorePoiStima() {
        val stime = StimeTariffe(ac = 0.64, dc = 0.73, hpc = 0.76)
        val veloce = Colonnina("p1", 44.5, 11.3, null, "Gestore", 100.0, listOf(Connettore(Presa.CCS2, 2, 100.0)))
        assertEquals(ClasseRicarica.DC, veloce.classe)
        assertEquals(TariffaUsata(0.73, OrigineTariffa.STIMA), Elettrico.tariffa(veloce, TariffeColonnine(), stime))
        val delGestore = veloce.copy(tariffeGestore = TariffeGestore(0.59, 0.79, null))
        assertEquals(TariffaUsata(0.79, OrigineTariffa.GESTORE), Elettrico.tariffa(delGestore, TariffeColonnine(), stime))
        assertEquals(TariffaUsata(0.45, OrigineTariffa.TUA), Elettrico.tariffa(delGestore, TariffeColonnine(dc = 0.45), stime))
        // solo il prezzo ultraveloce: vale anche per la veloce dello stesso gestore
        assertEquals(0.89, TariffeGestore(null, null, 0.89).per(ClasseRicarica.DC))
        assertNull(TariffeGestore(null, 0.79, null).per(ClasseRicarica.AC))
    }

    @Test
    fun nomiBreviDelleBandiere() {
        assertEquals("Eni", Bandiere.breve("Agip Eni", "Eni Station"))
        assertEquals("IP", Bandiere.breve("Api-Ip", "Bologna Via Toscana"))
        assertEquals("IP", Bandiere.breve("IP", "x"))
        assertEquals("Q8", Bandiere.breve("Q8", "x"))
        assertEquals("Sassomet", Bandiere.breve("Pompa Bianca", "Sassomet Srl"))
        assertEquals("Esso", Bandiere.breve("Esso", "x"))
        assertEquals("Petrolsud", Bandiere.breve("", "Petrolsud"))
        // una parola sola e lunga: si tronca
        assertEquals("Carburan.", Bandiere.breve("", "Carburantiexpress"))
        assertNull(Bandiere.breve("", ""))
    }
}
