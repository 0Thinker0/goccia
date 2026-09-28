package it.goccia.app.logica

import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Coordinate
import it.goccia.app.logica.Esempi.ADESSO_SECONDI
import it.goccia.app.logica.Esempi.BO_LAT
import it.goccia.app.logica.Esempi.BO_LON
import it.goccia.app.logica.Esempi.distributore
import it.goccia.app.logica.Esempi.estDi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvenienzaTest {
    private val centro = Coordinate(BO_LAT, BO_LON)

    @Test
    fun distanzaBolognaModena() {
        val km = Geo.distanzaKm(44.4938, 11.3426, 44.6471, 10.9252)
        assertEquals(37.3, km, 0.5)
    }

    @Test
    fun preferisceSelfMaTieneIlServito() {
        val soloServito = distributore(1, gasolioServito = 1859)
        val (prezzo, self) = Convenienza.prezzoPer(soloServito, Carburante.GASOLIO, preferisciSelf = true)!!
        assertEquals(1859, prezzo.millesimi)
        assertFalse(self)
        val entrambi = distributore(2, gasolioSelf = 1689, gasolioServito = 1859)
        assertEquals(1689, Convenienza.prezzoPer(entrambi, Carburante.GASOLIO, true)!!.first.millesimi)
        assertEquals(1859, Convenienza.prezzoPer(entrambi, Carburante.GASOLIO, false)!!.first.millesimi)
        assertNull(Convenienza.prezzoPer(entrambi, Carburante.GPL, true))
    }

    @Test
    fun sogliePerIlColore() {
        assertEquals(Tono.CONVENIENTE, Convenienza.tono(-3))
        assertEquals(Tono.MEDIA, Convenienza.tono(-2))
        assertEquals(Tono.MEDIA, Convenienza.tono(2))
        assertEquals(Tono.CARO, Convenienza.tono(3))
        assertEquals(Tono.MEDIA, Convenienza.tono(null))
        assertEquals(-5, Convenienza.differenzaCent(1689, 1739))
        assertEquals(1, Convenienza.differenzaCent(1745, 1739))
    }

    @Test
    fun mediaLocaleEscludeAutostradeEPrezziVecchi() {
        val lista = listOf(
            distributore(1, gasolioSelf = 1700),
            distributore(2, gasolioSelf = 1710),
            distributore(3, gasolioSelf = 1720),
            distributore(4, gasolioSelf = 1730),
            distributore(5, gasolioSelf = 1740),
            distributore(6, gasolioSelf = 1990, autostradale = true),
            distributore(7, gasolioSelf = 1500, giorniFa = 20),
            distributore(8, gasolioServito = 1900),
        )
        assertEquals(1720, Convenienza.mediaLocale(lista, Carburante.GASOLIO, true, centro, 10.0, ADESSO_SECONDI))
        // con meno di 5 prezzi la media non e affidabile
        assertNull(Convenienza.mediaLocale(lista.take(4), Carburante.GASOLIO, true, centro, 10.0, ADESSO_SECONDI))
    }

    @Test
    fun ilRaggioSiAllargaSeCiSonoPochiDistributori() {
        val lista = (1..6).map { i ->
            val (lat, lon) = estDi(i * 3.0)
            distributore(i.toLong(), lat, lon, gasolioSelf = 1700 + i)
        }
        val zona = Convenienza.zona(lista, Carburante.GASOLIO, true, centro, 5, false, 40.0, 5.0, ADESSO_SECONDI, null)
        assertEquals(20, zona.raggioUsatoKm)
        assertEquals(6, zona.offerte.size)
        assertEquals(6, zona.tutte.size)
    }

    @Test
    fun convenienzaRealeContaIlTragitto() {
        val (latLontano, lonLontano) = estDi(12.0)
        val vicino = distributore(1, gasolioSelf = 1700)
        val lontano = distributore(2, latLontano, lonLontano, gasolioSelf = 1690)
        val altri = (3..7).map { distributore(it.toLong(), gasolioSelf = 1720) }
        val zona = Convenienza.zona(listOf(vicino, lontano) + altri, Carburante.GASOLIO, true, centro, 15, false, 40.0, 6.0, ADESSO_SECONDI, null)
        val perPrezzo = Convenienza.ordina(zona.offerte, Ordinamento.PREZZO)
        assertEquals(2L, perPrezzo.first().distributore.id)
        // 1 cent in meno non vale 24 km tra andata e ritorno
        val perCosto = Convenienza.ordina(zona.offerte, Ordinamento.CONVENIENZA)
        assertEquals(1L, perCosto.first().distributore.id)
        assertTrue(zona.mediaLocale)
    }

    @Test
    fun iPrezziVecchiFinisconoInFondoEFuoriDaiConsigli() {
        val lista = listOf(
            distributore(1, gasolioSelf = 1600, giorniFa = 12),
            distributore(2, gasolioSelf = 1700),
            distributore(3, gasolioSelf = 1710),
            distributore(4, gasolioSelf = 1720, autostradale = true),
        )
        val zona = Convenienza.zona(lista, Carburante.GASOLIO, true, centro, 5, false, 40.0, 5.0, ADESSO_SECONDI, 1750)
        assertEquals(1L, Convenienza.ordina(zona.offerte, Ordinamento.PREZZO).last().distributore.id)
        assertEquals(listOf(2L, 3L), Convenienza.consigliati(zona).map { it.distributore.id })
        assertFalse(zona.mediaLocale)
        assertEquals(1750, zona.media)
    }
}
