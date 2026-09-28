package it.goccia.app.avvisi

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.goccia.app.contenitore
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Distributore
import it.goccia.app.logica.Convenienza
import it.goccia.app.logica.FUSO_ITALIA
import it.goccia.app.logica.Formati
import it.goccia.app.logica.Territorio
import it.goccia.app.logica.ValutaAvvisi
import java.io.IOException
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Pianifica il controllo dei prezzi: ogni 3 ore, solo con la rete. I dati cambiano una volta al giorno. */
object Sorveglianza {
    private const val PERIODICO = "controllo-prezzi"
    private const val SUBITO = "controllo-prezzi-subito"

    private val conRete = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun pianifica(context: Context) {
        val richiesta = PeriodicWorkRequestBuilder<ControlloPrezziWorker>(3, TimeUnit.HOURS)
            .setConstraints(conRete)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODICO, ExistingPeriodicWorkPolicy.KEEP, richiesta)
    }

    /** Dopo aver creato un avviso lo controlliamo subito, cosi l'utente vede lo stato aggiornato. */
    fun controllaOra(context: Context) {
        val richiesta = OneTimeWorkRequestBuilder<ControlloPrezziWorker>().setConstraints(conRete).build()
        WorkManager.getInstance(context).enqueueUniqueWork(SUBITO, ExistingWorkPolicy.REPLACE, richiesta)
    }
}

class ControlloPrezziWorker(context: Context, parametri: WorkerParameters) : CoroutineWorker(context, parametri) {
    override suspend fun doWork(): Result = try {
        ControlloPrezzi(applicationContext).esegui()
        Result.success()
    } catch (e: IOException) {
        Result.retry()
    } catch (e: Exception) {
        Result.success()
    }
}

/** Il controllo vero e proprio: avvisi a soglia attorno ai luoghi e cali dei preferiti. */
class ControlloPrezzi(private val context: Context) {
    private val c = context.contenitore

    suspend fun esegui() {
        val u = c.archivio.dati.value
        val imp = u.impostazioni
        val conSoglia = if (imp.avvisiPrezzo) u.avvisi.filter { it.attivo } else emptyList()
        val conCalo = if (imp.caloPreferiti) u.preferiti.filter { it.avvisaCalo } else emptyList()
        if (conSoglia.isEmpty() && conCalo.isEmpty()) return
        if (imp.orarioSilenzioso && ValutaAvvisi.silenzioso(ZonedDateTime.now(FUSO_ITALIA).hour)) return

        val esitoIndice = c.repository.indice(forza = true)
        if (esitoIndice.offline) throw IOException("dati non raggiungibili")
        val indice = esitoIndice.dati
        val estrazione = indice.estrazione
        val adesso = System.currentTimeMillis()
        val caricate = mutableMapOf<String, List<Distributore>>()
        suspend fun provincia(sigla: String): List<Distributore> = caricate.getOrPut(sigla) {
            try {
                c.repository.distributori(sigla, estrazione).dati
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }

        if (conSoglia.isNotEmpty()) {
            val comuni = try {
                c.repository.comuni()
            } catch (e: Exception) {
                emptyList()
            }
            for (avviso in conSoglia) {
                if (avviso.ultimaEstrazione == estrazione) continue
                val luogo = u.luogo(avviso.luogoId)
                val centro = luogo?.coordinate ?: u.ultimaPosizione?.coordinate ?: continue
                val carburante = Carburante.daCodice(avviso.carburante) ?: continue
                val sigle = Territorio.provinceAttorno(indice.province, comuni, centro, avviso.raggioKm + 5.0)
                val lista = sigle.flatMap { provincia(it) }
                val trovato = ValutaAvvisi.migliore(
                    lista, carburante, imp.preferisciSelf, centro, avviso.raggioKm, imp.escludiAutostrade, adesso / 1000,
                )
                val decisione = ValutaAvvisi.decidiSoglia(avviso, trovato, estrazione, adesso)
                if (decisione.notifica && trovato != null) {
                    val dove = luogo?.let { "vicino a ${it.nome}" } ?: "vicino a te"
                    Notifiche.mostra(
                        context,
                        id = avviso.id.hashCode(),
                        titolo = "${carburante.etichetta} a ${Formati.prezzo(trovato.prezzo.millesimi)} ${carburante.unita} $dove",
                        testo = "${trovato.distributore.titolo} · sotto la tua soglia di ${Formati.prezzo(avviso.soglia)} ${carburante.unita}",
                        distributore = trovato.distributore,
                    )
                }
                val esito = decisione.avviso
                c.archivio.aggiorna { du ->
                    du.copy(
                        avvisi = du.avvisi.map {
                            if (it.id != avviso.id) it
                            else it.copy(
                                ultimoMinimo = esito.ultimoMinimo,
                                ultimaNotificaIl = esito.ultimaNotificaIl,
                                ultimoEsito = esito.ultimoEsito,
                                ultimoControlloIl = esito.ultimoControlloIl,
                                minimoVisto = esito.minimoVisto,
                                ultimaEstrazione = esito.ultimaEstrazione,
                            )
                        },
                    )
                }
            }
        }

        if (conCalo.isNotEmpty()) {
            val carburante = u.carburante
            for (preferito in conCalo) {
                val d = provincia(preferito.provincia).firstOrNull { it.id == preferito.id } ?: continue
                val prezzo = Convenienza.prezzoPer(d, carburante, imp.preferisciSelf)?.first?.millesimi
                val decisione = ValutaAvvisi.decidiCalo(preferito, prezzo, carburante, estrazione)
                if (decisione.notifica && prezzo != null) {
                    Notifiche.mostra(
                        context,
                        id = preferito.id.hashCode(),
                        titolo = "${d.intestazione} ha abbassato il prezzo",
                        testo = "${carburante.etichetta} a ${Formati.prezzo(prezzo)} ${carburante.unita} " +
                            "(${Formati.variazioneCent(-decisione.caloMillesimi)}) · ${d.indirizzo.ifBlank { d.comune }}",
                        distributore = d,
                    )
                }
                val esito = decisione.preferito
                c.archivio.aggiorna { du ->
                    du.copy(
                        preferiti = du.preferiti.map {
                            if (it.id != preferito.id) it
                            else it.copy(
                                ultimoPrezzo = esito.ultimoPrezzo,
                                ultimoCarburante = esito.ultimoCarburante,
                                ultimaEstrazione = esito.ultimaEstrazione,
                            )
                        },
                    )
                }
            }
        }
    }
}
