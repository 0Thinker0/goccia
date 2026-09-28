package it.goccia.app.logica

import it.goccia.app.dati.Abitudine
import it.goccia.app.dati.ClasseRicarica
import it.goccia.app.dati.Colonnina
import it.goccia.app.dati.Connettore
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.ModoTariffa
import it.goccia.app.dati.Presa
import it.goccia.app.dati.StimeTariffe
import it.goccia.app.dati.TariffaCasa
import it.goccia.app.dati.TariffeColonnine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElettricoTest {
    private val stime = StimeTariffe(casa = 0.24, ac = 0.64, dc = 0.73, hpc = 0.76)

    private fun colonnina(
        id: String,
        lat: Double = Esempi.BO_LAT,
        lon: Double = Esempi.BO_LON,
        prese: List<Pair<Presa, Double>> = emptyList(),
        kw: Double? = prese.maxOfOrNull { it.second },
    ) = Colonnina(
        id = id,
        lat = lat,
        lon = lon,
        nome = "Colonnina $id",
        operatore = null,
        kw = kw,
        connettori = prese.map { (p, potenza) -> Connettore(p, 2, potenza) },
    )

    @Test
    fun tessereComeLaPipeline() {
        assertEquals("89_22", Elettrico.chiaveTessera(44.50811, 11.37622))
        assertEquals("83_24", Elettrico.chiaveTessera(41.90251, 12.49636))
        val attorno = Elettrico.tessereAttorno(Coordinate(Esempi.BO_LAT, Esempi.BO_LON), 15.0)
        assertEquals(setOf("88_22", "88_23", "89_22", "89_23"), attorno)
        assertEquals(setOf("88_22"), Elettrico.tessereAttorno(Coordinate(44.25, 11.25), 1.0))
        // un rettilineo di 100 km verso est attraversa due gradi di longitudine
        val (lat, lonFine) = Esempi.estDi(100.0)
        val campioni = Tragitto.campiona(listOf(Coordinate(Esempi.BO_LAT, Esempi.BO_LON), Coordinate(lat, lonFine)))
        val lungo = Elettrico.tessereLungo(campioni, 2.0)
        assertTrue(lungo.containsAll(listOf("88_22", "88_23", "88_24", "88_25")))
    }

    @Test
    fun prezzoDiCasaSecondoLaTariffa() {
        assertEquals(PrezzoCasa(0.24, FonteTariffa.STIMA), Elettrico.prezzoCasa(TariffaCasa(), 0.24))
        assertEquals(0.262, Elettrico.prezzoCasa(TariffaCasa(modo = ModoTariffa.PREZZO, prezzo = 0.262), 0.24).euroKwh, 1e-9)
        val bioraria = TariffaCasa(modo = ModoTariffa.PREZZO, bioraria = true, f1 = 0.251, f23 = 0.268)
        assertEquals(0.268, Elettrico.prezzoCasa(bioraria, 0.24).euroKwh, 1e-9)
        assertEquals(0.251, Elettrico.prezzoCasa(bioraria.copy(abitudine = Abitudine.GIORNO), 0.24).euroKwh, 1e-9)
        val bolletta = TariffaCasa(modo = ModoTariffa.BOLLETTA, bollettaEuro = 84.20, bollettaKwh = 320.0)
        assertEquals(PrezzoCasa(84.20 / 320, FonteTariffa.BOLLETTA), Elettrico.prezzoCasa(bolletta, 0.24))
        // bolletta non compilata: resta la stima
        assertEquals(FonteTariffa.STIMA, Elettrico.prezzoCasa(TariffaCasa(modo = ModoTariffa.BOLLETTA), 0.24).fonte)
        val pannelli = TariffaCasa(abitudine = Abitudine.FOTOVOLTAICO, fotovoltaico = 0.08)
        assertEquals(PrezzoCasa(0.08, FonteTariffa.FOTOVOLTAICO), Elettrico.prezzoCasa(pannelli, 0.24))
    }

    @Test
    fun costoInBatteriaConLePerdite() {
        val kwh = Elettrico.costoKwhCasa(TariffaCasa(), 0.24)
        assertEquals(0.2667, kwh, 1e-4)
        // 16 kWh ogni 100 km: 4,27 € a casa
        assertEquals(4.27, Elettrico.costoPer100(16.0, kwh), 0.005)
        // dal 46% all'80% di una batteria da 60 kWh
        assertEquals(5.44, (0.80 - 0.46) * 60 * kwh, 0.005)
        assertEquals(22.22, Elettrico.kwhDallaRete(TariffaCasa(), 20.0), 0.01)
    }

    @Test
    fun tariffeDelleColonnine() {
        assertEquals(0.64, Elettrico.tariffaColonnina(ClasseRicarica.AC, TariffeColonnine(), stime), 1e-9)
        assertEquals(0.76, Elettrico.tariffaColonnina(ClasseRicarica.HPC, TariffeColonnine(), stime), 1e-9)
        val mie = TariffeColonnine(dc = 0.59)
        assertEquals(0.59, Elettrico.tariffaColonnina(ClasseRicarica.DC, mie, stime), 1e-9)
        assertTrue(Elettrico.tariffaPersonale(ClasseRicarica.DC, mie))
        assertFalse(Elettrico.tariffaPersonale(ClasseRicarica.AC, mie))
        assertEquals(TariffeColonnine(ac = 0.45, dc = 0.59), Elettrico.conTariffa(mie, ClasseRicarica.AC, 0.45))
        // classi dalla potenza
        assertEquals(ClasseRicarica.HPC, colonnina("a", prese = listOf(Presa.CCS2 to 150.0, Presa.TYPE2 to 22.0)).classe)
        assertEquals(ClasseRicarica.DC, colonnina("b", prese = listOf(Presa.CHADEMO to 50.0)).classe)
        assertEquals(ClasseRicarica.AC, colonnina("c", prese = listOf(Presa.TYPE2 to 22.0)).classe)
    }

    @Test
    fun potenzaUtileSecondoAutoEPrese() {
        val veloce = colonnina("v", prese = listOf(Presa.CCS2 to 150.0, Presa.TYPE2 to 22.0))
        val tutte = setOf(Presa.CCS2, Presa.TYPE2)
        assertEquals(Erogazione(100.0, true), Elettrico.erogazione(veloce, tutte, acKw = 11.0, dcKw = 100.0))
        assertEquals(Erogazione(11.0, false), Elettrico.erogazione(veloce, setOf(Presa.TYPE2), 11.0, 100.0))
        val soloChademo = colonnina("h", prese = listOf(Presa.CHADEMO to 50.0))
        assertNull(Elettrico.erogazione(soloChademo, tutte, 11.0, 100.0))
        val senzaPrese = colonnina("s", kw = 22.0)
        assertEquals(Erogazione(11.0, false), Elettrico.erogazione(senzaPrese, tutte, 11.0, 100.0))
    }

    @Test
    fun tempiDiRicarica() {
        val hpc = Erogazione(150.0, true)
        assertEquals(20, Elettrico.minutiRicarica(60.0, 0.2, 0.8, hpc))
        assertEquals(14, Elettrico.minutiRicarica(60.0, 0.2, 0.6, hpc))
        assertEquals(38, Elettrico.minutiRicarica(60.0, 0.2, 1.0, hpc))
        assertEquals(0, Elettrico.minutiRicarica(60.0, 0.8, 0.5, hpc))
        // a casa o in alternata a 11 kW: circa 3 ore e mezza
        assertEquals(207, Elettrico.minutiRicarica(60.0, 0.2, 0.8, Erogazione(11.0, false)))
    }

    @Test
    fun colonnineVicineConRaggioChePuoAllargarsi() {
        val lista = listOf(1.0, 3.0, 8.0, 12.0, 40.0).mapIndexed { i, km ->
            val (lat, lon) = Esempi.estDi(km)
            colonnina("n$i", lat, lon, listOf(Presa.TYPE2 to 22.0))
        } + colonnina("chademo", prese = listOf(Presa.CHADEMO to 50.0))
        val centro = Coordinate(Esempi.BO_LAT, Esempi.BO_LON)
        val zona = Elettrico.zona(lista, centro, raggioKm = 5, prese = setOf(Presa.CCS2, Presa.TYPE2), potenzaMinima = 0, minimo = 3)
        assertEquals(10, zona.raggioKm)
        assertEquals(listOf("n0", "n1", "n2"), zona.vicine.map { it.colonnina.id })
        assertEquals(1.0, zona.vicine.first().distanzaKm, 0.05)
        // filtro di potenza
        assertTrue(Elettrico.zona(lista, centro, 5, emptySet(), potenzaMinima = 50).vicine.all { it.colonnina.id == "chademo" })
    }

    /** Colonnine veloci lungo un rettilineo verso est, ai km indicati. */
    private fun sulRettilineo(vararg km: Double, potenza: Double = 150.0): List<Colonnina> = km.map { k ->
        val (lat, lon) = Esempi.estDi(k)
        colonnina("km${k.toInt()}", lat, lon, listOf(Presa.CCS2 to potenza, Presa.TYPE2 to 22.0))
    }

    private fun piano(lunghezza: Double, colonnine: List<Colonnina>, partenza: Double): PianoElettrico {
        val (lat, lonFine) = Esempi.estDi(lunghezza)
        val campioni = Tragitto.campiona(listOf(Coordinate(Esempi.BO_LAT, Esempi.BO_LON), Coordinate(lat, lonFine)))
        val lungo = Elettrico.lungoIlPercorso(colonnine, campioni, 1.0)
        return Elettrico.pianifica(
            lungo, lunghezza, capacitaKwh = 60.0, consumoKwh100 = 16.0, acKw = 11.0, dcKw = 150.0,
            prese = setOf(Presa.CCS2, Presa.TYPE2), partenza = partenza, arrivoMinimo = 0.2, tariffa = { 0.59 },
        )
    }

    @Test
    fun viaggioLungoConUnaSosta() {
        val p = piano(378.0, sulRettilineo(60.0, 120.0, 180.0, 214.0, 260.0, 330.0), partenza = 0.8)
        assertTrue(p.completo)
        assertEquals(1, p.soste.size)
        val sosta = p.soste.single()
        // nella seconda meta del tratto possibile, prima di scendere sotto la riserva
        assertTrue(sosta.punto.km in 150.0..300.0)
        assertTrue(sosta.arrivo >= Elettrico.RISERVA_VIAGGIO)
        assertTrue(sosta.ripartenza <= Elettrico.SOGLIA_LENTA + 1e-9)
        assertTrue(p.arrivo >= 0.2)
        assertEquals(sosta.kwh * 0.59, p.costo, 1e-9)
        assertTrue(p.minutiSoste in 10..40)
    }

    @Test
    fun viaggioBreveSenzaSoste() {
        val p = piano(100.0, sulRettilineo(50.0), partenza = 0.8)
        assertTrue(p.completo)
        assertTrue(p.soste.isEmpty())
        assertEquals(0.8 - 100 * 0.16 / 60, p.arrivo, 1e-9)
        // senza soste tutta l'energia arriva da casa
        val costi = Elettrico.costoViaggio(100.0, 16.0, p, 0.25)
        assertEquals(16.0, costi.kwhCasa, 1e-9)
        assertEquals(4.0, costi.totale, 1e-9)
    }

    @Test
    fun costoDelViaggioContaAncheLaCaricaDiCasa() {
        val p = piano(378.0, sulRettilineo(60.0, 120.0, 180.0, 214.0, 260.0, 330.0), partenza = 0.8)
        val costi = Elettrico.costoViaggio(378.0, 16.0, p, 0.25)
        assertEquals(378 * 0.16, costi.kwhViaggio, 1e-9)
        assertEquals(p.kwhRicaricati, costi.kwhSoste, 1e-9)
        assertEquals(costi.kwhViaggio - costi.kwhSoste, costi.kwhCasa, 1e-9)
        assertEquals(p.costo + costi.kwhCasa * 0.25, costi.totale, 1e-9)
    }

    @Test
    fun senzaColonnineNonSiArriva() {
        val p = piano(400.0, emptyList(), partenza = 0.8)
        assertFalse(p.completo)
        assertEquals(300.0, p.kmCritico ?: 0.0, 0.5)
    }

    @Test
    fun colonnineLenteNonContanoPerLeSoste() {
        val p = piano(378.0, sulRettilineo(150.0, 220.0, potenza = 22.0), partenza = 0.8)
        assertFalse(p.completo)
    }
}
