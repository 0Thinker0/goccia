package it.goccia.app.logica

import it.goccia.app.dati.Rifornimento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatisticheTest {
    private val giorno = 86_400_000L

    private fun rifornimento(n: Int, giorniFa: Int, litri: Double, km: Int?, pieno: Boolean = true, prezzo: Int = 1700, media: Int? = 1740) =
        Rifornimento(
            id = "r$n",
            quando = Esempi.ADESSO_MILLIS - giorniFa * giorno,
            distributore = "Prova",
            carburante = "G",
            prezzo = prezzo,
            litri = litri,
            importo = litri * prezzo / 1000,
            km = km,
            pieno = pieno,
            mediaZona = media,
        )

    @Test
    fun consumoDaiPieni() {
        val lista = listOf(
            rifornimento(1, 30, 40.0, 10_000),
            rifornimento(2, 20, 10.0, 10_300, pieno = false),
            rifornimento(3, 10, 30.0, 10_800),
        )
        // 10 + 30 litri per 800 km
        assertEquals(5.0, Statistiche.consumo(lista)!!, 1e-9)
        assertNull(Statistiche.consumo(lista.take(2)))
    }

    @Test
    fun risparmioRispettoAllaMedia() {
        val lista = listOf(
            rifornimento(1, 0, 40.0, null, prezzo = 1700, media = 1740),
            rifornimento(2, 1, 20.0, null, prezzo = 1760, media = 1740),
            rifornimento(3, 2, 20.0, null, media = null),
        )
        val r = Statistiche.risparmio(lista, Esempi.ADESSO_MILLIS)
        // 40 l * 0,04 - 20 l * 0,02
        assertEquals(1.2, r.totale, 1e-9)
        assertEquals(6, r.perMese.size)
    }
}
