package it.goccia.app.logica

import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Credenziali temporanee di AWS (quelle di ospite che Cognito da alla PUN). */
data class CredenzialiAws(val accessKeyId: String, val secretKey: String, val sessionToken: String?, val scadenzaMillis: Long)

/**
 * Firma AWS Signature Version 4 per le richieste POST con corpo JSON, come la firma la pipeline
 * (pun.js): l'API della Piattaforma Unica Nazionale accetta solo richieste firmate.
 */
object FirmaAws {
    private val formatoData = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /**
     * Le intestazioni da aggiungere alla richiesta (la richiesta deve avere proprio
     * "Content-Type: application/json" e l'host dell'URL).
     */
    fun intestazioni(
        url: String,
        corpo: String,
        credenziali: CredenzialiAws,
        regione: String,
        adessoMillis: Long,
        servizio: String = "execute-api",
    ): Map<String, String> {
        val uri = URI(url)
        val amz = formatoData.format(Instant.ofEpochMilli(adessoMillis))
        val giorno = amz.substring(0, 8)
        val firmate = sortedMapOf(
            "content-type" to "application/json",
            "host" to uri.host,
            "x-amz-date" to amz,
        )
        credenziali.sessionToken?.let { firmate["x-amz-security-token"] = it }
        val nomi = firmate.keys.joinToString(";")
        val canonica = listOf(
            "POST",
            uri.rawPath.ifEmpty { "/" },
            uri.rawQuery ?: "",
            firmate.entries.joinToString("") { (k, v) -> "$k:${v.trim()}\n" },
            nomi,
            sha256(corpo),
        ).joinToString("\n")
        val ambito = "$giorno/$regione/$servizio/aws4_request"
        val daFirmare = listOf("AWS4-HMAC-SHA256", amz, ambito, sha256(canonica)).joinToString("\n")
        var chiave = hmac("AWS4${credenziali.secretKey}".toByteArray(), giorno)
        chiave = hmac(chiave, regione)
        chiave = hmac(chiave, servizio)
        chiave = hmac(chiave, "aws4_request")
        val firma = esadecimale(hmac(chiave, daFirmare))
        return buildMap {
            put("X-Amz-Date", amz)
            credenziali.sessionToken?.let { put("X-Amz-Security-Token", it) }
            put("Authorization", "AWS4-HMAC-SHA256 Credential=${credenziali.accessKeyId}/$ambito, SignedHeaders=$nomi, Signature=$firma")
        }
    }

    private fun sha256(testo: String): String = esadecimale(MessageDigest.getInstance("SHA-256").digest(testo.toByteArray()))

    private fun hmac(chiave: ByteArray, testo: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(chiave, "HmacSHA256"))
            doFinal(testo.toByteArray())
        }

    private fun esadecimale(byte: ByteArray): String = byte.joinToString("") { "%02x".format(it) }
}
