package it.goccia.app.logica

import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Coordinate
import it.goccia.app.logica.Esempi.ADESSO_SECONDI
import it.goccia.app.logica.Esempi.distributore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidaTest {

    /** Un percorso dritto verso est lungo [km] chilometri, partendo da Bologna. */
    private fun campioni(km: Double): List<PuntoPercorso> {
        val (lat, lonFine) = Esempi.estDi(km)
        return Tragitto.campiona(listOf(Coordinate(lat, Esempi.BO_LON), Coordinate(lat, lonFine)))
    }

    private fun percorso(km: Double = 400.0, livelloAutostrada: Boolean = true): PercorsoGuida.AlCarburante {
        val c = campioni(km)
        val lat = Esempi.BO_LAT
        fun a(k: Double, id: Long, prezzo: Int, autostrada: Boolean = false) =
            distributore(id, lat, Esempi.estDi(k).second, gasolioSelf = prezzo, autostradale = autostrada)
        val lista = listOf(
            a(30.0, 1, 1890, autostrada = livelloAutostrada),
            a(120.0, 2, 1690),
            a(150.0, 3, 1880, autostrada = true),
            a(200.0, 4, 1700),
            a(300.0, 5, 1720),
        )
        val lungo = Tragitto.lungoIlPercorso(lista, Carburante.GASOLIO, true, c, 1.0, ADESSO_SECONDI)
        return PercorsoGuida.AlCarburante(
            destinazione = "Roma",
            lunghezzaKm = km,
            campioni = c,
            strade = listOf(TrattoStrada(0.0, 5.0, "Via Emilia", null), TrattoStrada(5.0, km, "Autostrada del Sole", "A1")),
            lungo = lungo,
            carburante = Carburante.GASOLIO,
            self = true,
            capienza = 50.0,
            consumo = 5.0,
            pienoCompleto = true,
        )
    }

    @Test
    fun posizioneLungoIlPercorso() {
        val c = campioni(100.0)
        val (lat, lon) = Esempi.estDi(42.3)
        // 300 metri a nord della strada, al km 42,3
        val dove = Guida.sulPercorso(c, lat + 0.0027, lon)
        assertNotNull(dove)
        assertEquals(42.3, dove!!.km, 0.1)
        assertEquals(0.3, dove.distanzaKm, 0.05)
        assertFalse(dove.fuori)
        // 5 km lontano: fuori dal percorso
        assertTrue(Guida.sulPercorso(c, lat + 0.045, lon)!!.fuori)
    }

    @Test
    fun percorsoCheRipassaVicino() {
        // andata verso est per 10 km e ritorno su una strada parallela 200 m piu a nord
        val (lat, lon10) = Esempi.estDi(10.0)
        val punti = listOf(
            Coordinate(lat, Esempi.BO_LON),
            Coordinate(lat, lon10),
            Coordinate(lat + 0.0018, lon10),
            Coordinate(lat + 0.0018, Esempi.BO_LON),
        )
        val c = Tragitto.campiona(punti)
        val (_, lon3) = Esempi.estDi(3.0)
        // a meta fra le due strade: con l'ultima posizione al km 16 resta sul ritorno
        val dove = Guida.sulPercorso(c, lat + 0.0009, lon3, kmPrecedente = 16.0)!!
        assertTrue("km ${dove.km}", dove.km > 15)
        // senza storia vince il primo passaggio (piu vicino o uguale)
        val primo = Guida.sulPercorso(c, lat + 0.0008, lon3)!!
        assertEquals(3.0, primo.km, 0.2)
    }

    @Test
    fun siglaDellaStrada() {
        val p = percorso()
        assertNull(Guida.sigla(p.strade, 2.0))
        assertEquals("A1", Guida.sigla(p.strade, 120.0))
        assertEquals("A1", Guida.siglaBreve("A1;E35"))
        assertEquals("A14", Guida.siglaBreve(" A14 ; E45"))
        assertNull(Guida.siglaBreve(null))
    }

    @Test
    fun sostaConsigliataDaQui() {
        val p = percorso()
        // 1/4 di serbatoio: 250 km di autonomia con 100 km di riserva, come nel viaggio pianificato
        val energia = Guida.energiaIniziale(p, 0.25)
        val (lat, lon) = Esempi.estDi(20.0)
        val s = Guida.situazione(p, Guida.sulPercorso(p.campioni, lat, lon)!!, energia)
        assertEquals(20.0, s.km, 0.3)
        assertEquals(230.0, s.autonomiaKm, 0.5)
        assertEquals("A1", s.sigla)
        val consigliata = s.consigliata
        assertNotNull(consigliata)
        // il distributore 2 al km 120, cioe tra 100 km
        assertEquals("d2", consigliata!!.id)
        assertEquals(100.0, consigliata.traKm, 0.6)
        assertTrue(s.messaggio, s.messaggio.contains("con margine"))
        // nella lista c'e anche l'area di servizio al km 30, piu cara della consigliata
        val area = s.prossime.first { it.id == "d1" }
        assertEquals("+20 cent vs consigliato", area.confronto)
        assertTrue(area.piuCara)
        assertEquals(s.prossime.sortedBy { it.traKm }, s.prossime)
    }

    @Test
    fun dopoIlPienoNonServePiuFermarsi() {
        val p = percorso()
        val (lat, lon) = Esempi.estDi(120.0)
        val dove = Guida.sulPercorso(p.campioni, lat, lon)!!
        val energia = Guida.dopoRifornimento(p, dove.km)
        assertEquals(1000.0, energia.autonomiaKm, 1e-9)
        val s = Guida.situazione(p, dove, energia)
        assertNull(s.consigliata)
        assertTrue(s.messaggio, s.messaggio.startsWith("Arrivi senza fermarti"))
        assertEquals(1.0, s.livello, 1e-3)
    }

    @Test
    fun soloLeBandiereScelte() {
        // il distributore 4 (km 200) e IP, gli altri no: si consiglia lui e la lista mostra solo lui
        val base = percorso()
        val p = base.copy(
            lungo = base.lungo.map { if (it.distributore.id == 4L) it.copy(distributore = it.distributore.copy(bandiera = "IP")) else it },
            bandiere = setOf("IP"),
        )
        val (lat, lon) = Esempi.estDi(20.0)
        val s = Guida.situazione(p, Guida.sulPercorso(p.campioni, lat, lon)!!, Guida.energiaIniziale(p, 0.25))
        assertEquals("d4", s.consigliata?.id)
        assertEquals(listOf("d4"), s.prossime.map { it.id })
    }

    @Test
    fun autonomiaScarsa() {
        val p = percorso()
        // parte con pochissimo: la sosta consigliata e oltre l'autonomia
        val energia = Energia(90.0)
        val (lat, lon) = Esempi.estDi(10.0)
        val s = Guida.situazione(p, Guida.sulPercorso(p.campioni, lat, lon)!!, energia)
        assertTrue(s.critica)
        assertNotNull(s.consigliata)
    }

    @Test
    fun arrivo() {
        val p = percorso(km = 50.0)
        val (lat, lon) = Esempi.estDi(49.8)
        val s = Guida.situazione(p, Guida.sulPercorso(p.campioni, lat, lon)!!, Energia(500.0))
        assertTrue(s.arrivato)
        assertTrue(s.prossime.isEmpty())
    }

    @Test
    fun stazioneVicinaPerLaDomandaSulRifornimento() {
        val p = percorso()
        val (lat, lon) = Esempi.estDi(120.0)
        val vicina = Guida.stazioneVicina(p, lat + 0.001, lon)
        assertEquals("d2", vicina?.first)
        assertNull(Guida.stazioneVicina(p, lat + 0.01, lon))
    }

    @Test
    fun autonomiaArrotondata() {
        assertEquals("~170 km", Guida.autonomiaTesto(168.0))
        assertEquals("~0 km", Guida.autonomiaTesto(-3.0))
    }
}
