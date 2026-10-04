package it.goccia.app.dati

import it.goccia.app.logica.CredenzialiAws
import it.goccia.app.logica.FirmaAws
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Lo stato di un punto di ricarica come lo trasmette il gestore. */
enum class StatoPunto { LIBERO, OCCUPATO, FUORI_SERVIZIO, SCONOSCIUTO }

data class PuntoRicarica(val id: String, val stato: StatoPunto, val tempoReale: Boolean)

/** Lo stato dei punti di una colonnina, letto dalla PUN alle [quando] (millisecondi). */
data class StatoColonnina(val punti: List<PuntoRicarica>, val quando: Long) {
    private val vivi: List<PuntoRicarica> get() = punti.filter { it.tempoReale }
    val tempoReale: Boolean get() = vivi.isNotEmpty()
    val totale: Int get() = vivi.size
    val liberi: Int get() = vivi.count { it.stato == StatoPunto.LIBERO }
    val occupati: Int get() = vivi.count { it.stato == StatoPunto.OCCUPATO }
    val guasti: Int get() = vivi.count { it.stato == StatoPunto.FUORI_SERVIZIO }
}

/**
 * Stato in tempo reale dei punti di ricarica dalla Piattaforma Unica Nazionale (GSE). Come il
 * portale, entra da ospite: Amazon Cognito da credenziali temporanee anonime (nessun account),
 * con cui firmiamo la richiesta. Manda solo gli identificativi dei punti della colonnina aperta.
 */
class Pun(private val http: OkHttpClient, private val cartella: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }
    private val accesso = Mutex()

    @Volatile
    private var credenziali: CredenzialiAws? = null
    private val recenti = ConcurrentHashMap<String, StatoColonnina>()

    /** Lo stato dei punti della colonnina; null se non e della PUN. Un minuto di cache. */
    suspend fun stato(c: Colonnina): StatoColonnina? {
        if (!c.daPun || c.punti.isEmpty()) return null
        val adesso = System.currentTimeMillis()
        recenti[c.id]?.let { if (adesso - it.quando in 0 until UN_MINUTO) return it }
        return withContext(Dispatchers.IO) {
            val corpo = json.encodeToString(c.punti.take(100))
            val testo = try {
                firmata("/v1/chargepoints/group", corpo)
            } catch (e: AccessoNegato) {
                // credenziali scadute o identita non piu valida: si riprova una volta da capo
                credenziali = null
                firmata("/v1/chargepoints/group", corpo)
            }
            val punti = json.decodeFromString<List<PunPuntoDto>>(testo)
                .filter { it.evse_id in c.punti && it.status !in ESCLUSI }
                .map { PuntoRicarica(it.evse_id, statoDi(it.status), it.realTime) }
            StatoColonnina(punti, System.currentTimeMillis()).also { recenti[c.id] = it }
        }
    }

    private class AccessoNegato(messaggio: String) : IOException(messaggio)

    private suspend fun firmata(percorso: String, corpo: String): String {
        val cred = valide()
        val url = API + percorso
        val intestazioni = FirmaAws.intestazioni(url, corpo, cred, REGIONE, System.currentTimeMillis())
        val richiesta = Request.Builder().url(url).apply {
            intestazioni.forEach { (k, v) -> header(k, v) }
            // proprio "application/json", senza charset: e firmato cosi
            post(corpo.toByteArray().toRequestBody("application/json".toMediaType()))
        }.build()
        http.newCall(richiesta).execute().use { r ->
            val testo = r.body?.string().orEmpty()
            if (r.code == 401 || r.code == 403) throw AccessoNegato("PUN: HTTP ${r.code}")
            if (!r.isSuccessful) throw IOException("PUN: HTTP ${r.code}")
            return testo
        }
    }

    /** Credenziali di ospite ancora buone per qualche minuto, rinnovate se serve. */
    private suspend fun valide(): CredenzialiAws = accesso.withLock {
        credenziali?.takeIf { it.scadenzaMillis - System.currentTimeMillis() > 3 * UN_MINUTO } ?: rinnova()
    }

    private fun rinnova(): CredenzialiAws {
        val fileIdentita = File(cartella, "pun_identita.txt")
        val salvata = fileIdentita.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        val nuove = salvata?.let { identita ->
            try {
                credenzialiPer(identita)
            } catch (e: IOException) {
                null
            }
        } ?: run {
            // prima volta (o identita scaduta): Cognito ne crea una nuova, anonima
            val identita = json.decodeFromString<PunIdentitaDto>(cognito("GetId", json.encodeToString(PunGetIdDto(POOL)))).IdentityId
            fileIdentita.parentFile?.mkdirs()
            fileIdentita.writeText(identita)
            credenzialiPer(identita)
        }
        credenziali = nuove
        return nuove
    }

    private fun credenzialiPer(identita: String): CredenzialiAws {
        val r = json.decodeFromString<PunCredenzialiDto>(cognito("GetCredentialsForIdentity", json.encodeToString(PunPerIdentitaDto(identita))))
        val c = r.Credentials
        return CredenzialiAws(c.AccessKeyId, c.SecretKey, c.SessionToken, (c.Expiration * 1000).toLong())
    }

    private fun cognito(operazione: String, corpo: String): String {
        val richiesta = Request.Builder()
            .url("https://cognito-identity.$REGIONE.amazonaws.com/")
            .header("X-Amz-Target", "AWSCognitoIdentityService.$operazione")
            .post(corpo.toByteArray().toRequestBody("application/x-amz-json-1.1".toMediaType()))
            .build()
        http.newCall(richiesta).execute().use { r ->
            val testo = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("Cognito $operazione: HTTP ${r.code}")
            return testo
        }
    }

    private companion object {
        const val API = "https://api.pun.piattaformaunicanazionale.it"
        const val REGIONE = "eu-south-1"
        const val POOL = "eu-south-1:e3b2ab05-2046-43dd-8ed0-c0f14c69d507"
        const val UN_MINUTO = 60_000L
        val ESCLUSI = setOf("REMOVED", "PLANNED")

        fun statoDi(s: String?): StatoPunto = when (s) {
            "AVAILABLE" -> StatoPunto.LIBERO
            "CHARGING", "RESERVED", "BLOCKED" -> StatoPunto.OCCUPATO
            "OUTOFORDER", "INOPERATIVE" -> StatoPunto.FUORI_SERVIZIO
            else -> StatoPunto.SCONOSCIUTO
        }
    }
}

@Suppress("PropertyName")
@Serializable
private data class PunPuntoDto(val evse_id: String = "", val status: String? = null, val realTime: Boolean = false)

@Suppress("PropertyName")
@Serializable
private data class PunGetIdDto(val IdentityPoolId: String)

@Suppress("PropertyName")
@Serializable
private data class PunIdentitaDto(val IdentityId: String)

@Suppress("PropertyName")
@Serializable
private data class PunPerIdentitaDto(val IdentityId: String)

@Suppress("PropertyName")
@Serializable
private data class PunCredenzialiDto(val Credentials: PunValoriDto)

@Suppress("PropertyName")
@Serializable
private data class PunValoriDto(val AccessKeyId: String, val SecretKey: String, val SessionToken: String, val Expiration: Double)
