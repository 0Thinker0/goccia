package it.goccia.app.dati

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** I dati dell'utente vivono solo sul telefono, in un piccolo file JSON. */
class ArchivioUtente(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    private val mutex = Mutex()
    private val _dati = MutableStateFlow(carica())
    val dati: StateFlow<DatiUtente> = _dati.asStateFlow()

    private fun carica(): DatiUtente = try {
        if (file.exists()) json.decodeFromString(DatiUtente.serializer(), file.readText()) else DatiUtente()
    } catch (e: Exception) {
        DatiUtente()
    }

    suspend fun aggiorna(trasforma: (DatiUtente) -> DatiUtente) {
        mutex.withLock {
            val nuovo = trasforma(_dati.value)
            if (nuovo == _dati.value) return@withLock
            _dati.value = nuovo
            withContext(Dispatchers.IO) { scrivi(nuovo) }
        }
    }

    /** Copia completa dei dati, per il backup su file scelto dall'utente. */
    fun esporta(): String = json.encodeToString(DatiUtente.serializer(), _dati.value)

    /** Ripristino da un backup: sostituisce tutto. Lancia un'eccezione se il file non e valido. */
    suspend fun importa(testo: String) {
        val letti = json.decodeFromString(DatiUtente.serializer(), testo)
        aggiorna { letti.copy(introduzioneVista = true) }
    }

    private fun scrivi(dati: DatiUtente) {
        file.parentFile?.mkdirs()
        val temporaneo = File(file.parentFile, file.name + ".tmp")
        temporaneo.writeText(json.encodeToString(DatiUtente.serializer(), dati))
        if (!temporaneo.renameTo(file)) {
            file.delete()
            temporaneo.renameTo(file)
        }
    }
}
