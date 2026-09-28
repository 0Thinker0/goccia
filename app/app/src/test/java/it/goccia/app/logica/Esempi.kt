package it.goccia.app.logica

import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Distributore
import it.goccia.app.dati.PrezziCarburante
import it.goccia.app.dati.Prezzo
import java.time.LocalDateTime
import java.time.ZoneId

/** Dati di prova condivisi dai test. */
object Esempi {
    val ADESSO_SECONDI: Long = LocalDateTime.of(2026, 9, 27, 9, 0).atZone(ZoneId.of("Europe/Rome")).toEpochSecond()
    val ADESSO_MILLIS: Long = ADESSO_SECONDI * 1000

    const val GIORNO = 86_400L

    /** Piazza Maggiore, Bologna */
    const val BO_LAT = 44.4938
    const val BO_LON = 11.3426

    fun distributore(
        id: Long,
        lat: Double = BO_LAT,
        lon: Double = BO_LON,
        gasolioSelf: Int? = null,
        gasolioServito: Int? = null,
        giorniFa: Long = 0,
        autostradale: Boolean = false,
        bandiera: String = "Bandiera $id",
        gpl: Int? = null,
    ): Distributore {
        val quando = ADESSO_SECONDI - giorniFa * GIORNO - 3_600
        val prezzi = buildMap {
            if (gasolioSelf != null || gasolioServito != null) {
                put(
                    Carburante.GASOLIO,
                    PrezziCarburante(gasolioSelf?.let { Prezzo(it, quando) }, gasolioServito?.let { Prezzo(it, quando) }),
                )
            }
            if (gpl != null) put(Carburante.GPL, PrezziCarburante(null, Prezzo(gpl, quando)))
        }
        return Distributore(
            id = id,
            bandiera = bandiera,
            nome = "Impianto $id",
            indirizzo = "Via Prova $id",
            comune = "Bologna",
            provincia = "BO",
            autostradale = autostradale,
            lat = lat,
            lon = lon,
            prezzi = prezzi,
            speciali = emptyList(),
        )
    }

    /** Sposta un punto di circa [km] verso est. */
    fun estDi(km: Double, lat: Double = BO_LAT, lon: Double = BO_LON): Pair<Double, Double> =
        lat to lon + km / (111.32 * Math.cos(Math.toRadians(lat)))
}
