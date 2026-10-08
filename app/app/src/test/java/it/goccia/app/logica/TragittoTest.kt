package it.goccia.app.logica

import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Coordinate
import it.goccia.app.logica.Esempi.ADESSO_SECONDI
import it.goccia.app.logica.Esempi.distributore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun areeDiServizioSoloDalNostroLato() {
        // verso est: si viaggia sulla carreggiata sud, le aree "Sud" sono raggiungibili, le "Nord" no
        val campioni = Tragitto.campiona(rettilineo(100.0))
        val (lat, lon40) = Esempi.estDi(40.0)
        val (_, lon70) = Esempi.estDi(70.0)
        fun area(id: Long, nome: String, dLat: Double, lon: Double) =
            distributore(id, lat + dLat, lon, gasolioSelf = 1800, autostradale = true).copy(nome = nome)
        val lista = listOf(
            area(1, "Area Pioppa Sud", 0.0006, lon40), // il nome vince anche se le coordinate sono un po' a nord
            area(2, "Area Pioppa Nord", 0.0007, lon40),
            area(3, "Servizi 70", -0.0008, lon70), // senza lato nel nome: a destra, cioe a sud
            area(4, "Servizi 71", 0.0008, lon70), // a sinistra
        )
        val lungo = Tragitto.lungoIlPercorso(lista, Carburante.GASOLIO, true, campioni, 1.0, ADESSO_SECONDI)
        assertEquals(listOf(1L, 3L), lungo.map { it.distributore.id }.sorted())
        // e al contrario, verso ovest
        val ritorno = Tragitto.campiona(rettilineo(100.0).reversed())
        val lungoRitorno = Tragitto.lungoIlPercorso(lista, Carburante.GASOLIO, true, ritorno, 1.0, ADESSO_SECONDI)
        assertEquals(listOf(2L, 4L), lungoRitorno.map { it.distributore.id }.sorted())
        assertEquals(270.0, Tragitto.latoDalNome("Cantagallo Ovest") ?: 0.0, 1e-9)
        assertEquals(null, Tragitto.latoDalNome("Area Secchia"))
    }

    /** 400 km verso est con un quarto di serbatoio: 50 l, 5 l/100 km, si arriva al km 150 tenendo la riserva. */
    private fun lungoConBandiere(): List<LungoIlPercorso> {
        val campioni = Tragitto.campiona(rettilineo(400.0))
        val (lat, _) = Esempi.estDi(0.0)
        fun a(km: Double, id: Long, prezzo: Int, bandiera: String) =
            distributore(id, lat, Esempi.estDi(km).second, gasolioSelf = prezzo, bandiera = bandiera)
        val lista = listOf(
            a(50.0, 1, 1750, "Agip Eni"),
            a(120.0, 2, 1650, "Q8"), // il piu conveniente prima del limite
            a(140.0, 3, 1720, "IP"),
            a(300.0, 4, 1700, "IP"),
            a(320.0, 5, 1600, "Pompa bianca"),
        )
        return Tragitto.lungoIlPercorso(lista, Carburante.GASOLIO, true, campioni, 1.0, ADESSO_SECONDI)
    }

    @Test
    fun sosteSoloDalleBandiereScelte() {
        val lungo = lungoConBandiere()
        val tutte = Tragitto.pianifica(lungo, 400.0, 50.0, 5.0, 0.25, pienoCompleto = true)
        assertEquals(2L, tutte.soste.single().punto.distributore.id)

        val soloIp = Tragitto.pianifica(lungo, 400.0, 50.0, 5.0, 0.25, pienoCompleto = true, bandiere = setOf("IP"))
        assertTrue(soloIp.raggiungibile)
        assertFalse(soloIp.bandiereIgnorate)
        assertEquals(3L, soloIp.soste.single().punto.distributore.id)
        // anche le alternative e il piu conveniente sono delle bandiere scelte
        assertTrue(soloIp.alternative.all { it.distributore.bandiera == "IP" })
        assertEquals("IP", soloIp.migliore?.distributore?.bandiera)
    }

    @Test
    fun conBandiereCheNonBastanoSiFermaDaUnAltra() {
        val lungo = lungoConBandiere()
        // nessun Tamoil lungo la strada: con un quarto di serbatoio si resterebbe a secco
        val piano = Tragitto.pianifica(lungo, 400.0, 50.0, 5.0, 0.25, pienoCompleto = true, bandiere = setOf("Tamoil"))
        assertTrue(piano.bandiereIgnorate)
        assertTrue(piano.raggiungibile)
        assertEquals(2L, piano.soste.single().punto.distributore.id)
        // con il pieno non serve fermarsi: le bandiere scelte valgono anche se non ce ne sono
        val pieno = Tragitto.pianifica(lungo, 400.0, 50.0, 5.0, 1.0, pienoCompleto = true, bandiere = setOf("Tamoil"))
        assertTrue(pieno.senzaSoste)
        assertFalse(pieno.bandiereIgnorate)
        assertNull(pieno.migliore)
    }

    @Test
    fun senzaDistributoriNonSiArriva() {
        val piano = Tragitto.pianifica(emptyList(), 400.0, 50.0, 5.0, 0.25, pienoCompleto = true)
        assertTrue(piano.senzaSoste)
        assertFalse(piano.raggiungibile)
        assertTrue(piano.margineArrivoKm < 0)
        // l'unico distributore e oltre l'autonomia (250 km)
        val lontano = lungoConBandiere().filter { it.distributore.id == 4L }
        val oltre = Tragitto.pianifica(lontano, 400.0, 50.0, 5.0, 0.25, pienoCompleto = true)
        assertEquals(4L, oltre.soste.single().punto.distributore.id)
        assertFalse(oltre.raggiungibile)
    }

    @Test
    fun gruppiDelleBandiere() {
        assertEquals("Eni", Bandiere.gruppo("Agip Eni"))
        assertEquals("IP", Bandiere.gruppo("IP"))
        assertEquals("IP", Bandiere.gruppo("Api-Ip"))
        assertEquals("Q8", Bandiere.gruppo("Q8"))
        assertEquals("Esso", Bandiere.gruppo("Esso"))
        assertEquals(Bandiere.POMPE_BIANCHE, Bandiere.gruppo("Pompa bianca"))
        assertEquals(Bandiere.POMPE_BIANCHE, Bandiere.gruppo(""))
        assertEquals(Bandiere.ALTRE, Bandiere.gruppo("Europam"))
        // nota ma poco diffusa: tra le altre
        assertEquals(Bandiere.ALTRE, Bandiere.gruppo("Total"))
        assertTrue(Bandiere.SCELTE_VIAGGI.containsAll(listOf("Eni", "IP", Bandiere.POMPE_BIANCHE, Bandiere.ALTRE)))
        val ip = distributore(1, bandiera = "IP")
        assertTrue(Bandiere.ammesso(ip, emptySet()))
        assertTrue(Bandiere.ammesso(ip, setOf("Q8", "IP")))
        assertFalse(Bandiere.ammesso(ip, setOf("Q8")))
    }
}
