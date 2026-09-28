package it.goccia.app.logica

import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Coordinate
import it.goccia.app.logica.Esempi.ADESSO_SECONDI
import it.goccia.app.logica.Esempi.distributore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TragittoTest {

    @Test
    fun decodificaPolyline() {
        // esempio della documentazione di Google (precisione 5)
        val punti = Tragitto.decodifica("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precisione = 5)
        assertEquals(3, punti.size)
        assertEquals(38.5, punti[0].lat, 1e-9)
        assertEquals(-120.2, punti[0].lon, 1e-9)
        assertEquals(40.7, punti[1].lat, 1e-9)
        assertEquals(-120.95, punti[1].lon, 1e-9)
        assertEquals(43.252, punti[2].lat, 1e-9)
        assertEquals(-126.453, punti[2].lon, 1e-9)
    }

    /** Un percorso dritto verso est lungo [km] chilometri, partendo da Bologna. */
    private fun rettilineo(km: Double): List<Coordinate> {
        val (lat, lonFine) = Esempi.estDi(km)
        return listOf(Coordinate(lat, Esempi.BO_LON), Coordinate(lat, lonFine))
    }

    @Test
    fun campionamentoConIKmProgressivi() {
        val campioni = Tragitto.campiona(rettilineo(10.0), passoKm = 0.5)
        assertEquals(0.0, campioni.first().km, 1e-9)
        assertEquals(10.0, campioni.last().km, 0.05)
        assertTrue(campioni.size in 20..22)
        assertTrue(campioni.zipWithNext().all { (a, b) -> b.km - a.km <= 0.51 })
    }

    @Test
    fun distributoriLungoLaStrada() {
        val campioni = Tragitto.campiona(rettilineo(100.0))
        val (lat, lon30) = Esempi.estDi(30.0)
        val (_, lon60) = Esempi.estDi(60.0)
        val lista = listOf(
            distributore(1, lat, lon30, gasolioSelf = 1700), // sul percorso al km 30
            distributore(2, lat + 0.009, lon60, gasolioSelf = 1650), // 1 km a nord del km 60
            distributore(3, lat + 0.05, lon60, gasolioSelf = 1500), // 5,5 km: troppo lontano
            distributore(4, lat + 0.004, lon30, gasolioSelf = 1900, autostradale = true), // area sull'altra carreggiata lontana
        )
        val lungo = Tragitto.lungoIlPercorso(lista, Carburante.GASOLIO, true, campioni, Tragitto.distanzaPerMinuti(5), ADESSO_SECONDI)
        assertEquals(listOf(1L, 2L), lungo.map { it.distributore.id })
        assertEquals(30.0, lungo[0].km, 0.5)
        assertEquals(0, lungo[0].deviazioneMin)
        assertTrue(lungo[1].deviazioneMin in 3..5)
    }

    @Test
    fun sostaPrimaDellaRiserva() {
        val campioni = Tragitto.campiona(rettilineo(400.0))
        val (lat, _) = Esempi.estDi(0.0)
        fun a(km: Double, id: Long, prezzo: Int, autostrada: Boolean = false) =
            distributore(id, lat, Esempi.estDi(km).second, gasolioSelf = prezzo, autostradale = autostrada)
        val lista = listOf(
            a(50.0, 1, 1750),
            a(120.0, 2, 1690),
            a(200.0, 3, 1680), // oltre il limite con un quarto di serbatoio
            a(150.0, 4, 1890, autostrada = true),
            a(300.0, 5, 1700),
        )
        val lungo = Tragitto.lungoIlPercorso(lista, Carburante.GASOLIO, true, campioni, 1.0, ADESSO_SECONDI)
        // 50 l, 5 l/100 km: autonomia piena 1000 km, riserva 100 km; con 1/4 arriviamo a 250 - 100 = 150 km
        val piano = Tragitto.pianifica(lungo, 400.0, 50.0, 5.0, 0.25, pienoCompleto = true)
        assertFalse(piano.senzaSoste)
        assertEquals(150.0, piano.kmLimite, 1e-9)
        assertEquals(1, piano.soste.size)
        assertEquals(2L, piano.soste[0].punto.distributore.id)
        // arriva al km 120 con 130 km di autonomia = 6,5 l, quindi mette 43,5 l
        assertEquals(43.5, piano.soste[0].quantita, 0.2)
        assertEquals(1890, piano.mediaAutostrada ?: 1890)

        val pieno = Tragitto.pianifica(lungo, 400.0, 50.0, 5.0, 1.0, pienoCompleto = true)
        assertTrue(pieno.senzaSoste)
        assertEquals(600, pieno.margineArrivoKm)
    }
}
