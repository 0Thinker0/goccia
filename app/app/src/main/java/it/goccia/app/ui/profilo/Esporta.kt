package it.goccia.app.ui.profilo

import android.content.Context
import android.net.Uri
import android.widget.Toast
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.DatiUtente
import it.goccia.app.logica.FUSO_ITALIA
import it.goccia.app.logica.Formati
import java.time.Instant
import java.time.format.DateTimeFormatter

private val formatoData = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun cella(testo: String): String =
    if (testo.contains(';') || testo.contains('"') || testo.contains('\n')) "\"" + testo.replace("\"", "\"\"") + "\"" else testo

/** Rifornimenti in CSV all'italiana (punto e virgola, virgola decimale): si apre bene con Excel. */
fun csvRifornimenti(u: DatiUtente): String {
    val righe = mutableListOf(
        "data;auto;distributore;carburante;modalità;prezzo €/l;litri;importo €;km;pieno;media zona €/l;risparmio €",
    )
    for (r in u.rifornimenti.sortedBy { it.quando }) {
        val c = Carburante.daCodice(r.carburante)
        righe += listOf(
            formatoData.format(Instant.ofEpochMilli(r.quando).atZone(FUSO_ITALIA)),
            u.auto.firstOrNull { it.id == r.autoId }?.nome.orEmpty(),
            r.distributore,
            c?.etichetta ?: r.carburante,
            if (c?.haSelf == false) "" else if (r.self) "self" else "servito",
            Formati.prezzo(r.prezzo),
            Formati.numero(r.litri, 2).replace(".", ""),
            Formati.numero(r.importo, 2).replace(".", ""),
            r.km?.toString().orEmpty(),
            if (r.pieno) "sì" else "no",
            r.mediaZona?.let { Formati.prezzo(it) }.orEmpty(),
            r.risparmio?.let { Formati.numero(it, 2).replace(".", "").replace("−", "-") }.orEmpty(),
        ).joinToString(";") { cella(it) }
    }
    return "﻿" + righe.joinToString("\r\n") + "\r\n"
}

fun scriviFile(context: Context, uri: Uri, testo: String) {
    val riuscito = try {
        context.contentResolver.openOutputStream(uri)?.use { it.write(testo.toByteArray(Charsets.UTF_8)) } != null
    } catch (e: Exception) {
        false
    }
    Toast.makeText(context, if (riuscito) "File salvato" else "Non sono riuscito a salvare il file", Toast.LENGTH_SHORT).show()
}

fun leggiFile(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
} catch (e: Exception) {
    null
}
