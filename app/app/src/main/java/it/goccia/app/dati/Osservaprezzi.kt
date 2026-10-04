package it.goccia.app.dati

import it.goccia.app.logica.ImpiantoLive
import it.goccia.app.logica.PrezzoLive
import java.io.IOException
import java.time.OffsetDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Osservaprezzi carburanti del Ministero delle Imprese e del Made in Italy: l'API pubblica del
 * sito carburanti.mise.gov.it, con i prezzi in vigore adesso (il file del mattino ha quelli
 * delle 8 del giorno prima). Riceve solo il centro della zona e il raggio, nessun dato personale.
 */
class Osservaprezzi(private val http: OkHttpClient) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        // priceOrder e fuelType hanno un valore di serie ma vanno mandati
        encodeDefaults = true
    }

    /** I distributori entro [raggioKm] (al massimo 10) da [centro], con i prezzi in vigore. */
    suspend fun attorno(centro: Coordinate, raggioKm: Int): List<ImpiantoLive> = withContext(Dispatchers.IO) {
        val corpo = json.encodeToString(
            OspzRicercaZonaDto(listOf(OspzPuntoDto(centro.lat, centro.lon)), raggioKm.coerceIn(1, RAGGIO_MASSIMO_KM)),
        )
        val letto = System.currentTimeMillis() / 1000
        val risposta = json.decodeFromString<OspzZonaDto>(posta("search/zone", corpo))
        if (!risposta.success) throw IOException("Osservaprezzi: ricerca non riuscita")
        risposta.results.map { it.inLive(letto) }
    }

    /** La scheda di un distributore: prezzi con la data di ciascuno e servizi dichiarati. */
    suspend fun scheda(id: Long): SchedaImpianto = withContext(Dispatchers.IO) {
        val letto = System.currentTimeMillis() / 1000
        val dto = json.decodeFromString<OspzSchedaDto>(leggi("registry/servicearea/$id"))
        val prezzi = dto.fuels.mapNotNull { f ->
            val quando = secondi(f.insertDate) ?: return@mapNotNull null
            PrezzoLive(f.name, f.isSelf, (f.price * 1000).roundToInt(), quando)
        }
        SchedaImpianto(
            id = dto.id,
            prezzi = ImpiantoLive(dto.id, prezzi, letto, dettagliato = true),
            servizi = dto.services.mapNotNull { s -> s.id.toIntOrNull()?.let { ServizioImpianto.daCodice(it) } }.distinct().sortedBy { it.ordinal },
            telefono = dto.phoneNumber?.trim()?.takeIf { it.length >= 6 },
            sito = dto.website?.trim()?.takeIf { it.contains('.') },
        )
    }

    private fun posta(percorso: String, corpo: String): String {
        val richiesta = Request.Builder()
            .url(BASE + percorso)
            .header("Accept", "application/json")
            .post(corpo.toRequestBody("application/json".toMediaType()))
            .build()
        return esegui(richiesta, percorso)
    }

    private fun leggi(percorso: String): String =
        esegui(Request.Builder().url(BASE + percorso).header("Accept", "application/json").build(), percorso)

    private fun esegui(richiesta: Request, percorso: String): String =
        http.newCall(richiesta).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Osservaprezzi: HTTP ${r.code} per $percorso")
            r.body?.string() ?: throw IOException("Osservaprezzi: risposta vuota per $percorso")
        }

    private fun OspzImpiantoDto.inLive(letto: Long): ImpiantoLive {
        // la ricerca da una sola data per distributore: quella dell'ultima comunicazione
        val quando = secondi(insertDate) ?: 0L
        return ImpiantoLive(id, if (quando <= 0) emptyList() else fuels.map { PrezzoLive(it.name, it.isSelf, (it.price * 1000).roundToInt(), quando) }, letto)
    }

    companion object {
        const val BASE = "https://carburanti.mise.gov.it/ospzApi/"
        const val RAGGIO_MASSIMO_KM = 10

        /** "2026-10-04T09:14:13+02:00" o "2026-10-04T07:14:13Z" -> secondi; null se non si legge. */
        fun secondi(data: String?): Long? = try {
            data?.let { OffsetDateTime.parse(it).toEpochSecond() }
        } catch (e: Exception) {
            null
        }
    }
}

/** I servizi che il gestore dichiara al Ministero (elenco di registry/services). */
enum class ServizioImpianto(val codice: Int, val etichetta: String) {
    RISTORO(1, "Bar e ristoro"),
    OFFICINA(2, "Officina"),
    SOSTA_CAMPER_TIR(3, "Sosta camper e tir"),
    SCARICO_CAMPER(4, "Scarico camper"),
    AREA_BAMBINI(5, "Area bambini"),
    BANCOMAT(6, "Bancomat"),
    DISABILI(7, "Servizi per disabili"),
    WIFI(8, "Wi-Fi"),
    GOMMISTA(9, "Gommista"),
    AUTOLAVAGGIO(10, "Autolavaggio"),
    RICARICA(11, "Ricarica elettrica"),
    ;

    companion object {
        fun daCodice(codice: Int): ServizioImpianto? = entries.firstOrNull { it.codice == codice }
    }
}

data class SchedaImpianto(
    val id: Long,
    val prezzi: ImpiantoLive,
    val servizi: List<ServizioImpianto>,
    val telefono: String?,
    val sito: String?,
)

@Serializable
private data class OspzRicercaZonaDto(
    val points: List<OspzPuntoDto>,
    val radius: Int,
    val priceOrder: String = "asc",
    /** tutti i carburanti (0), self e servito (x) */
    val fuelType: String = "0-x",
)

@Serializable
private data class OspzPuntoDto(val lat: Double, val lng: Double)

@Serializable
private data class OspzZonaDto(val success: Boolean = false, val results: List<OspzImpiantoDto> = emptyList())

@Serializable
private data class OspzImpiantoDto(
    val id: Long,
    val fuels: List<OspzCarburanteDto> = emptyList(),
    val insertDate: String? = null,
)

@Serializable
private data class OspzCarburanteDto(
    val price: Double = 0.0,
    val name: String = "",
    val isSelf: Boolean = false,
    val insertDate: String? = null,
)

@Serializable
private data class OspzSchedaDto(
    val id: Long,
    val fuels: List<OspzCarburanteDto> = emptyList(),
    val services: List<OspzServizioDto> = emptyList(),
    val phoneNumber: String? = null,
    val website: String? = null,
)

@Serializable
private data class OspzServizioDto(val id: String = "", val description: String = "")
