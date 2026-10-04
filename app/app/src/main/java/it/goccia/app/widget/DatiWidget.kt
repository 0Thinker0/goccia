package it.goccia.app.widget

import android.content.Context
import it.goccia.app.contenitore
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.DatiUtente
import it.goccia.app.logica.Consiglio
import it.goccia.app.logica.Convenienza
import it.goccia.app.logica.FUSO_ITALIA
import it.goccia.app.logica.Formati
import it.goccia.app.logica.TempoReale
import it.goccia.app.logica.Territorio
import it.goccia.app.logica.Urgenza
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Quello che mostrano i widget. I prezzi vengono dai file gia scaricati dall'app (o li scarichiamo
 * se mancano); il centro e l'ultima posizione vista con l'app aperta o Casa: niente posizione in
 * background.
 */
object DatiWidget {
    data class Voce(
        val nome: String,
        val titolo: String,
        val distanzaKm: Double,
        val prezzo: Int,
        val cent: Int?,
        val provincia: String,
        val id: Long,
    )

    /** [lettiIl]: millisecondi della lettura in tempo reale, null se i prezzi sono quelli del file. */
    data class Prezzi(val carburante: String, val estrazione: String, val dove: String, val voci: List<Voce>, val lettiIl: Long? = null)

    data class Auto(val nome: String, val valore: String, val livello: Double, val testo: String, val elettrica: Boolean)

    /** Una posizione di meno di 12 ore fa vale ancora come "vicino a te". */
    private const val POSIZIONE_RECENTE_MS = 12 * 3_600_000L

    private fun centro(u: DatiUtente): Pair<Coordinate, String>? {
        val ultima = u.ultimaPosizione
        val casa = u.casa
        return when {
            ultima != null && System.currentTimeMillis() - ultima.quando < POSIZIONE_RECENTE_MS -> ultima.coordinate to "vicino a te"
            casa != null -> casa.coordinate to "vicino a Casa"
            ultima != null -> ultima.coordinate to "vicino all'ultima posizione"
            else -> null
        }
    }

    suspend fun prezzi(context: Context): Prezzi? = withContext(Dispatchers.IO) {
        val c = context.contenitore
        val u = c.archivio.dati.value
        val (centro, dove) = centro(u) ?: return@withContext null
        val indice = try {
            c.repository.indice().dati
        } catch (e: Exception) {
            return@withContext null
        }
        val comuni = try {
            c.repository.comuni()
        } catch (e: Exception) {
            emptyList()
        }
        val sigle = Territorio.provinceAttorno(indice.province, comuni, centro, 15.0)
        val delFile = sigle.flatMap { sigla ->
            try {
                c.repository.distributori(sigla, indice.estrazione).dati
            } catch (e: Exception) {
                emptyList()
            }
        }
        val carburante = u.carburante
        val imp = u.impostazioni
        // i prezzi in vigore adesso, se Osservaprezzi risponde in fretta
        val live = try {
            withTimeoutOrNull(10_000) { c.osservaprezzi.attorno(centro, imp.raggioKm) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val distributori = live?.let { lista -> TempoReale.applica(delFile, lista.associateBy { it.id }) } ?: delFile
        val lettiIl = live?.let { System.currentTimeMillis() }
        val self = imp.preferisciSelf || !carburante.haSelf
        val provincia = Territorio.provinciaDi(indice.province, comuni, centro)
        val media = provincia?.medie?.get(carburante)?.let { if (self) it.self ?: it.servito else it.servito ?: it.self }
        val auto = u.autoCorrente?.takeIf { !it.alimentazione.elettrica }
        val zona = Convenienza.zona(
            distributori = distributori,
            carburante = carburante,
            preferisciSelf = imp.preferisciSelf,
            centro = centro,
            raggioKm = imp.raggioKm,
            escludiAutostrade = imp.escludiAutostrade,
            litri = 40.0,
            consumoPer100 = auto?.consumo ?: 6.0,
            adessoSecondi = System.currentTimeMillis() / 1000,
            mediaDiRiserva = media,
        )
        val voci = Convenienza.consigliati(zona, 3).map { o ->
            Voce(o.distributore.intestazione, o.distributore.titolo, o.distanzaKm, o.prezzo.millesimi, o.differenzaCent, o.distributore.provincia, o.distributore.id)
        }
        val modo = if (carburante.haSelf) (if (self) " self" else " servito") else ""
        Prezzi(carburante.etichetta + modo, indice.estrazione, dove, voci, lettiIl)
    }

    fun auto(context: Context): Auto? {
        val u = context.contenitore.archivio.dati.value
        val auto = u.autoCorrente ?: return null
        val s = Consiglio.serbatoio(auto, u.rifornimenti.filter { it.autoId == null || it.autoId == auto.id }, System.currentTimeMillis())
        if (auto.alimentazione.elettrica) {
            return Auto(auto.nome, "~${s.autonomiaKm} km", s.livello, "Batteria al ${Formati.percento(s.livello)}", true)
        }
        val consiglio = Consiglio.consiglio(s, null, null, LocalDate.now(FUSO_ITALIA))
        val quando = if (consiglio.urgenza == Urgenza.TRANQUILLO) "${s.giorniRimasti} giorni di tragitti" else consiglio.titolo.substringAfter(": ").replaceFirstChar { it.lowercase() }
        return Auto(auto.nome, "~${s.autonomiaKm} km", s.livello, "Serbatoio ${Consiglio.quarti(s.livello)} · $quando", false)
    }
}
