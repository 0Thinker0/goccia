package it.goccia.app.logica

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatiTest {
    @Test
    fun prezziENumeri() {
        assertEquals("1,689", Formati.prezzo(1689))
        assertEquals("0,719", Formati.prezzo(719))
        assertEquals("1.234,50 €", Formati.euro(1234.5))
        assertEquals("8,40 €", Formati.euro(8.4))
        assertEquals("−2,00 €", Formati.euro(-2.0))
        assertEquals("1,2 km", Formati.km(1.234))
        assertEquals("15 km", Formati.km(14.6))
        assertEquals("37,5 l", Formati.litri(37.5))
    }

    @Test
    fun differenzeDallaMedia() {
        assertEquals("−5 cent vs media", Formati.differenza(-5))
        assertEquals("+6 cent vs media", Formati.differenza(6))
        assertEquals("Nella media · −1 cent", Formati.differenza(-1))
        assertEquals("Nella media", Formati.differenza(0))
        assertEquals("−1,2 cent", Formati.variazioneCent(-12))
        assertEquals("+2 cent", Formati.variazioneCent(20))
    }

    @Test
    fun letturaDiQuelloCheScriveLUtente() {
        assertEquals(1689, Formati.leggiPrezzo("1,689"))
        assertEquals(1689, Formati.leggiPrezzo("1.689"))
        assertEquals(1689, Formati.leggiPrezzo("1689"))
        assertNull(Formati.leggiPrezzo("abc"))
        assertEquals(37.5, Formati.leggiNumero("37,5")!!, 1e-9)
        assertEquals(37.5, Formati.leggiNumero("37.5")!!, 1e-9)
        assertEquals(1250.0, Formati.leggiNumero("1.250,00")!!, 1e-9)
        assertEquals(84312, Formati.leggiIntero("84.312"))
    }

    @Test
    fun dateInItaliano() {
        assertEquals("27 set", Formati.dataIso("2026-09-27"))
        assertEquals("Set 2026", Formati.meseIso("2026-09"))
        assertEquals("settembre", Formati.meseLungo(YearMonth.of(2026, 9)))
        val adesso = Esempi.ADESSO_MILLIS
        assertEquals("aggiornato oggi", Formati.aggiornamento(Esempi.ADESSO_SECONDI - 3600, adesso))
        assertEquals("aggiornato ieri", Formati.aggiornamento(Esempi.ADESSO_SECONDI - Esempi.GIORNO, adesso))
        assertEquals("5 giorni fa", Formati.aggiornamento(Esempi.ADESSO_SECONDI - 5 * Esempi.GIORNO, adesso))
        assertEquals("oggi alle 08:00", Formati.quando(Esempi.ADESSO_SECONDI - 3600, adesso))
    }
}
