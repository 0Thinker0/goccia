package it.goccia.app.logica

import it.goccia.app.dati.Avviso
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.Preferito
import it.goccia.app.logica.Esempi.ADESSO_MILLIS
import it.goccia.app.logica.Esempi.ADESSO_SECONDI
import it.goccia.app.logica.Esempi.distributore
import it.goccia.app.logica.Esempi.estDi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValutaAvvisiTest {
    private val casa = Coordinate(Esempi.BO_LAT, Esempi.BO_LON)

    @Test
    fun ilMiglioreNelRaggioConPrezzoRecente() {
        val (lat, lon) = estDi(8.0)
        val lista = listOf(
            distributore(1, gasolioSelf = 1720),
            distributore(2, gasolioSelf = 1650, giorniFa = 6), // troppo vecchio
            distributore(3, lat, lon, gasolioSelf = 1600), // fuori raggio
            distributore(4, gasolioSelf = 1699),
        )
        val t = ValutaAvvisi.migliore(lista, Carburante.GASOLIO, true, casa, 5, false, ADESSO_SECONDI)
        assertEquals(4L, t!!.distributore.id)
        assertNull(ValutaAvvisi.migliore(lista, Carburante.GPL, true, casa, 5, false, ADESSO_SECONDI))
    }

    @Test
    fun sogliaNotificaUnaVoltaEPoiSoloSeScendeAncora() {
        var avviso = Avviso(id = "a", carburante = "G", soglia = 1700, raggioKm = 5)
        fun controlla(prezzo: Int): Boolean {
            val t = ValutaAvvisi.migliore(listOf(distributore(1, gasolioSelf = prezzo)), Carburante.GASOLIO, true, casa, 5, false, ADESSO_SECONDI)
            val d = ValutaAvvisi.decidiSoglia(avviso, t, "2026-09-27", ADESSO_MILLIS)
            avviso = d.avviso
            return d.notifica
        }
        assertFalse(controlla(1710))
        assertEquals(1710, avviso.minimoVisto)
        assertTrue(controlla(1699))
        assertEquals("Bandiera 1 a 1,699", avviso.ultimoEsito)
        assertFalse("stesso prezzo: niente doppioni", controlla(1699))
        assertTrue("scende ancora", controlla(1690))
        assertFalse(controlla(1720))
        assertNull(avviso.ultimoMinimo)
        assertTrue("di nuovo sotto soglia: si riparte", controlla(1695))
    }

    @Test
    fun caloDiUnPreferito() {
        val iniziale = Preferito(id = 1, provincia = "BO", titolo = "Prova", lat = 44.5, lon = 11.3)
        val primo = ValutaAvvisi.decidiCalo(iniziale, 1700, Carburante.GASOLIO, "2026-09-26")
        assertFalse("la prima volta salva solo il riferimento", primo.notifica)
        val stessoGiorno = ValutaAvvisi.decidiCalo(primo.preferito, 1690, Carburante.GASOLIO, "2026-09-26")
        assertFalse(stessoGiorno.notifica)
        val giornoDopo = ValutaAvvisi.decidiCalo(primo.preferito, 1690, Carburante.GASOLIO, "2026-09-27")
        assertTrue(giornoDopo.notifica)
        assertEquals(10, giornoDopo.caloMillesimi)
        val sale = ValutaAvvisi.decidiCalo(giornoDopo.preferito, 1695, Carburante.GASOLIO, "2026-09-28")
        assertFalse(sale.notifica)
        assertEquals(1695, sale.preferito.ultimoPrezzo)
        val altroCarburante = ValutaAvvisi.decidiCalo(sale.preferito, 1500, Carburante.BENZINA, "2026-09-29")
        assertFalse("cambiato carburante: nuovo riferimento", altroCarburante.notifica)
    }

    @Test
    fun orarioSilenzioso() {
        assertTrue(ValutaAvvisi.silenzioso(22))
        assertTrue(ValutaAvvisi.silenzioso(6))
        assertFalse(ValutaAvvisi.silenzioso(7))
        assertFalse(ValutaAvvisi.silenzioso(20))
    }
}
