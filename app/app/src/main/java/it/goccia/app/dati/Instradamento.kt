package it.goccia.app.dati

import it.goccia.app.logica.Guida
import it.goccia.app.logica.Tragitto
import it.goccia.app.logica.TrattoStrada
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Un percorso stradale: i punti, la lunghezza, il tempo di guida stimato e le strade
 * che percorre (per la sigla dell'autostrada nella modalita autostrada).
 */
data class Percorso(
    val punti: List<Coordinate>,
    val distanzaKm: Double,
    val durataMin: Int,
    val strade: List<TrattoStrada> = emptyList(),
)

@Serializable
private data class RispostaOsrm(val code: String = "", val message: String? = null, val routes: List<RottaOsrm> = emptyList())

@Serializable
private data class RottaOsrm(
    val geometry: String = "",
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val legs: List<TrattaOsrm> = emptyList(),
)

@Serializable
private data class TrattaOsrm(val steps: List<PassoOsrm> = emptyList())

@Serializable
private data class PassoOsrm(val distance: Double = 0.0, val name: String = "", val ref: String? = null)

/**
 * Calcolo del percorso con OSRM sul server pubblico di FOSSGIS (dati OpenStreetMap).
 * Una sola richiesta per viaggio: niente chiavi, niente account.
 */
class Instradamento(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun percorso(da: Coordinate, a: Coordinate): Percorso = withContext(Dispatchers.IO) {
        val url = "https://routing.openstreetmap.de/routed-car/route/v1/driving/" +
            "${da.lon},${da.lat};${a.lon},${a.lat}?overview=full&geometries=polyline6&steps=true&alternatives=false"
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
                strade = strade(rotta),
            )
        }
    }

    /** I passi di OSRM diventano tratti con i km progressivi; quelli consecutivi sulla stessa strada si uniscono. */
    private fun strade(rotta: RottaOsrm): List<TrattoStrada> {
        val tratti = ArrayList<TrattoStrada>()
        var km = 0.0
        for (passo in rotta.legs.flatMap { it.steps }) {
            val lunghezza = passo.distance / 1000.0
            if (lunghezza <= 0.0) continue
            val sigla = Guida.siglaBreve(passo.ref)
            val ultimo = tratti.lastOrNull()
            if (ultimo != null && ultimo.sigla == sigla && ultimo.nome == passo.name) {
                tratti[tratti.lastIndex] = ultimo.copy(a = km + lunghezza)
            } else {
                tratti += TrattoStrada(km, km + lunghezza, passo.name, sigla)
            }
            km += lunghezza
        }
        return tratti
    }
}

class PercorsoNonTrovato(messaggio: String) : Exception(messaggio)
