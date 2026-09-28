package it.goccia.app.logica

import it.goccia.app.dati.Alimentazione
import it.goccia.app.dati.Auto
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsiglioTest {
    private val giorno = 86_400_000L

    private fun auto(livello: Double, indicatoGiorniFa: Int = 0) = Auto(
        id = "a",
        nome = "La Tipo",
        alimentazione = Alimentazione.GASOLIO,
        capienza = 50.0,
        consumo = 5.0,
        livello = livello,
        livelloIl = Esempi.ADESSO_MILLIS - indicatoGiorniFa * giorno,
    )

    @Test
    fun ilLivelloScendeConIGiorni() {
        val oggi = Consiglio.serbatoio(auto(0.5), emptyList(), Esempi.ADESSO_MILLIS)
        assertEquals(0.5, oggi.livello, 1e-9)
        assertEquals(500, oggi.autonomiaKm)
        // 35 km al giorno per 4 giorni = 140 km = 7 litri = 0,14 del serbatoio
        val dopo = Consiglio.serbatoio(auto(0.5, indicatoGiorniFa = 4), emptyList(), Esempi.ADESSO_MILLIS)
        assertEquals(0.36, dopo.livello, 1e-9)
    }

    @Test
    fun quandoFareIlPieno() {
        val domenica = LocalDate.of(2026, 9, 27)
        val quarto = Consiglio.serbatoio(auto(0.25), emptyList(), Esempi.ADESSO_MILLIS)
        // (0,25 - 0,1) * 50 l / 5 l ogni 100 km = 150 km -> 4 giorni da 35 km
        assertEquals(4, quarto.giorniRimasti)
        val c = Consiglio.consiglio(quarto, null, null, domenica)
        assertEquals(Urgenza.PRESTO, c.urgenza)
        assertEquals("Fai il pieno entro giovedì", c.titolo)
        assertTrue(c.testo.startsWith("Ti restano circa 4 giorni"))

        val riserva = Consiglio.serbatoio(auto(0.08), emptyList(), Esempi.ADESSO_MILLIS)
        assertEquals(Urgenza.RISERVA, Consiglio.consiglio(riserva, null, null, domenica).urgenza)
    }

    @Test
    fun tendenzaDeiPrezzi() {
        assertEquals(Tendenza.IN_CALO to -12, Consiglio.tendenza(listOf(1751, 1750, null, 1745, 1744, 1742, 1740, 1739)))
        assertEquals(Tendenza.STABILE, Consiglio.tendenza(listOf(1740, 1741, 1742))!!.first)
        assertEquals(Tendenza.IN_SALITA, Consiglio.tendenza(listOf(1700, 1705, 1712))!!.first)
        assertEquals(null, Consiglio.tendenza(listOf(null, 1700)))
    }
}
