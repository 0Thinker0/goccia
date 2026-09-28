package it.goccia.app.logica

import it.goccia.app.dati.Comune
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.Provincia
import org.junit.Assert.assertEquals
import org.junit.Test

class TerritorioTest {
    private val comuni = listOf(
        Comune("Bologna", "BO", 44.5007, 11.3577, 101),
        Comune("Bollate", "MI", 45.5460, 9.1180, 9),
        Comune("Bolzano", "BZ", 46.4900, 11.3400, 30),
        Comune("Casalecchio di Reno", "BO", 44.4760, 11.2800, 12),
        Comune("Reggio nell'Emilia", "RE", 44.7034, 10.6365, 59),
        Comune("Reggio di Calabria", "RC", 38.1046, 15.6527, 53),
        Comune("Castelfranco Emilia", "MO", 44.5960, 11.0520, 14),
        Comune("Forlì", "FC", 44.2227, 12.0407, 40),
    )

    @Test
    fun normalizzazione() {
        assertEquals("reggio nell emilia", Territorio.normalizza("Reggio nell'Emilia"))
        assertEquals("forli", Territorio.normalizza("Forlì"))
    }

    @Test
    fun ricercaDeiComuni() {
        assertEquals(listOf("Bologna", "Bolzano", "Bollate"), Territorio.cerca(comuni, "bol").map { it.nome })
        assertEquals("Reggio nell'Emilia", Territorio.cerca(comuni, "reggio emilia").single().nome)
        assertEquals("Forlì", Territorio.cerca(comuni, "forli").single().nome)
        assertEquals("Casalecchio di Reno", Territorio.cerca(comuni, "casalecchio reno").single().nome)
        assertEquals(emptyList<Comune>(), Territorio.cerca(comuni, "  "))
    }

    @Test
    fun provinceDaCaricare() {
        val piazzaMaggiore = Coordinate(44.4938, 11.3426)
        assertEquals(listOf("BO"), Territorio.provinceAttorno(emptyList(), comuni, piazzaMaggiore, 20.0))
        assertEquals(listOf("BO", "MO"), Territorio.provinceAttorno(emptyList(), comuni, piazzaMaggiore, 30.0))
        val bo = Provincia("BO", "Bologna", 44.1, 10.8, 44.8, 11.8, 44.5, 11.3, emptyMap())
        val rc = Provincia("RC", "Reggio Calabria", 37.9, 15.6, 38.6, 16.4, 38.2, 15.9, emptyMap())
        assertEquals(listOf("BO"), Territorio.provinceAttorno(listOf(rc, bo), emptyList(), piazzaMaggiore, 10.0))
    }
}
