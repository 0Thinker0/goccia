package it.goccia.app.dati

import it.goccia.app.logica.Tragitto
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Un percorso stradale: i punti, la lunghezza e il tempo di guida stimato. */
data class Percorso(val punti: List<Coordinate>, val distanzaKm: Double, val durataMin: Int)

@Serializable
private data class RispostaOsrm(val code: String = "", val message: String? = null, val routes: List<RottaOsrm> = emptyList())

@Serializable
private data class RottaOsrm(val geometry: String = "", val distance: Double = 0.0, val duration: Double = 0.0)

/**
 * Calcolo del percorso con OSRM sul server pubblico di FOSSGIS (dati OpenStreetMap).
 * Una sola richiesta per viaggio: niente chiavi, niente account.
 */
class Instradamento(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun percorso(da: Coordinate, a: Coordinate): Percorso = withContext(Dispatchers.IO) {
        val url = "https://routing.openstreetmap.de/routed-car/route/v1/driving/" +
            "${da.lon},${da.lat};${a.lon},${a.lat}?overview=full&geometries=polyline6&steps=false&alternatives=false"
        val richiesta = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(richiesta).execute().use { risposta ->
            val testo = risposta.body?.string() ?: throw IOException("Risposta vuota dal servizio dei percorsi")
            val dati = try {
                json.decodeFromString(RispostaOsrm.serializer(), testo)
            } catch (e: Exception) {
                throw IOException("Risposta non valida dal servizio dei percorsi")
            }
            val rotta = dati.routes.firstOrNull()
            if (dati.code != "Ok" || rotta == null) {
                throw PercorsoNonTrovato(dati.message ?: "Nessun percorso stradale tra i due punti")
            }
            Percorso(
                punti = Tragitto.decodifica(rotta.geometry, precisione = 6),
                distanzaKm = rotta.distance / 1000.0,
                durataMin = (rotta.duration / 60.0).roundToInt(),
            )
        }
    }
}

class PercorsoNonTrovato(messaggio: String) : Exception(messaggio)
