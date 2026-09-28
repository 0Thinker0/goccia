package it.goccia.app.dati

import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Scarica i file pubblicati ogni mattina su GitHub Pages e li tiene in una cache su disco,
 * cosi l'app funziona anche offline con gli ultimi prezzi scaricati.
 */
class DatiRepository(
    private val cartella: File,
    private val http: OkHttpClient,
    private val base: String,
) {
    data class Esito<T>(val dati: T, val offline: Boolean, val scaricatoIl: Long)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Volatile
    private var comuniInMemoria: List<Comune>? = null
    private val prezziInMemoria = ConcurrentHashMap<String, Pair<String, List<Distributore>>>()
    private val storicoInMemoria = ConcurrentHashMap<String, Pair<String, StoricoZona>>()
    private val cronologiaInMemoria = ConcurrentHashMap<String, Pair<String, CronologiaDto>>()

    init {
        cartella.mkdirs()
    }

    private fun scarica(percorso: String): String {
        val richiesta = Request.Builder().url(base + percorso).header("Accept", "application/json").build()
        http.newCall(richiesta).execute().use { risposta ->
            if (!risposta.isSuccessful) throw IOException("HTTP ${risposta.code} per $percorso")
            return risposta.body?.string() ?: throw IOException("Risposta vuota per $percorso")
        }
    }

    private fun salva(file: File, testo: String) {
        val temporaneo = File(file.parentFile, file.name + ".tmp")
        temporaneo.writeText(testo)
        if (!temporaneo.renameTo(file)) {
            file.delete()
            temporaneo.renameTo(file)
        }
    }

    private inline fun <reified T> leggi(file: File): T? = try {
        if (file.exists()) json.decodeFromString<T>(file.readText()) else null
    } catch (e: Exception) {
        null
    }

    /** L'indice cambia una volta al giorno: lo riscarichiamo al massimo ogni due ore. */
    suspend fun indice(forza: Boolean = false): Esito<Indice> = withContext(Dispatchers.IO) {
        val file = File(cartella, "indice.json")
        val eta = System.currentTimeMillis() - file.lastModified()
        if (!forza && file.exists() && eta in 0 until DUE_ORE) {
            leggi<IndiceDto>(file)?.let { return@withContext Esito(it.inDominio(), false, file.lastModified()) }
        }
        try {
            val testo = scarica("dati/v1/indice.json")
            val dto = json.decodeFromString<IndiceDto>(testo)
            salva(file, testo)
            Esito(dto.inDominio(), false, System.currentTimeMillis())
        } catch (e: Exception) {
            val dto = leggi<IndiceDto>(file) ?: throw e
            Esito(dto.inDominio(), true, file.lastModified())
        }
    }

    /** Distributori di una provincia per l'estrazione indicata (dall'indice). */
    suspend fun distributori(sigla: String, estrazione: String): Esito<List<Distributore>> = withContext(Dispatchers.IO) {
        prezziInMemoria[sigla]?.let { (data, lista) ->
            if (data == estrazione) return@withContext Esito(lista, false, System.currentTimeMillis())
        }
        val file = File(cartella, "p_$sigla.json")
        val inCache = leggi<PrezziProvinciaDto>(file)
        if (inCache != null && inCache.estrazione == estrazione) {
            val lista = inCache.impianti.map { it.inDominio(sigla) }
            prezziInMemoria[sigla] = estrazione to lista
            return@withContext Esito(lista, false, file.lastModified())
        }
        try {
            val testo = scarica("dati/v1/p/$sigla.json")
            val dto = json.decodeFromString<PrezziProvinciaDto>(testo)
            salva(file, testo)
            val lista = dto.impianti.map { it.inDominio(sigla) }
            prezziInMemoria[sigla] = dto.estrazione to lista
            Esito(lista, false, System.currentTimeMillis())
        } catch (e: Exception) {
            inCache ?: throw e
            val lista = inCache.impianti.map { it.inDominio(sigla) }
            Esito(lista, true, file.lastModified())
        }
    }

    /** Medie giornaliere della provincia (grafico "prezzo medio in zona"). */
    suspend fun storico(sigla: String, estrazione: String): StoricoZona? = withContext(Dispatchers.IO) {
        storicoInMemoria[sigla]?.let { (data, s) -> if (data == estrazione) return@withContext s }
        val file = File(cartella, "s_$sigla.json")
        val inCache = leggi<StoricoDto>(file)
        val scelto: StoricoDto? = if (inCache != null && inCache.giorni.lastOrNull() == estrazione) {
            inCache
        } else {
            try {
                val testo = scarica("dati/v1/s/$sigla.json")
                salva(file, testo)
                json.decodeFromString<StoricoDto>(testo)
            } catch (e: Exception) {
                inCache
            }
        }
        val dto = scelto ?: return@withContext null
        val zona = dto.inDominio()
        storicoInMemoria[sigla] = estrazione to zona
        zona
    }

    /** Elenco dei comuni con distributori: cambia poco, lo riscarichiamo al massimo una volta a settimana. */
    suspend fun comuni(): List<Comune> = withContext(Dispatchers.IO) {
        comuniInMemoria?.let { return@withContext it }
        val file = File(cartella, "comuni.json")
        val eta = System.currentTimeMillis() - file.lastModified()
        val inCache = leggi<ComuniDto>(file)
        val dto = if (inCache != null && eta in 0 until SETTE_GIORNI) {
            inCache
        } else {
            try {
                val testo = scarica("dati/v1/comuni.json")
                val nuovo = json.decodeFromString<ComuniDto>(testo)
                salva(file, testo)
                nuovo
            } catch (e: Exception) {
                inCache
            }
        }
        val lista = dto?.let { converti(it) } ?: emptyList()
        if (lista.isNotEmpty()) comuniInMemoria = lista
        lista
    }

    private fun converti(dto: ComuniDto): List<Comune> = dto.comuni.mapNotNull { riga ->
        try {
            Comune(
                nome = riga[0].jsonPrimitive.content,
                provincia = riga[1].jsonPrimitive.content,
                lat = riga[2].jsonPrimitive.double,
                lon = riga[3].jsonPrimitive.double,
                distributori = riga.getOrNull(4)?.jsonPrimitive?.intOrNull ?: 0,
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Storico dei singoli distributori (30 giorni e 12 mesi), scaricato solo quando serve. */
    suspend fun storicoDistributore(sigla: String, estrazione: String, id: Long): StoricoDistributore? =
        withContext(Dispatchers.IO) {
            val cronologia = cronologiaInMemoria[sigla]?.takeIf { it.first == estrazione }?.second
                ?: run {
                    val file = File(cartella, "h_$sigla.json")
                    val inCache = leggi<CronologiaDto>(file)
                    val dto = if (inCache != null && inCache.giorni.lastOrNull() == estrazione) {
                        inCache
                    } else {
                        try {
                            val testo = scarica("dati/v1/h/$sigla.json")
                            salva(file, testo)
                            json.decodeFromString<CronologiaDto>(testo)
                        } catch (e: Exception) {
                            inCache
                        }
                    }
                    dto?.also { cronologiaInMemoria[sigla] = estrazione to it }
                }
            cronologia?.perDistributore(id)
        }

    private companion object {
        const val DUE_ORE = 2 * 60 * 60 * 1000L
        const val SETTE_GIORNI = 7 * 24 * 60 * 60 * 1000L
    }
}
