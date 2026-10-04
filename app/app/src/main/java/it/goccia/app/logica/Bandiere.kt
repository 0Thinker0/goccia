package it.goccia.app.logica

import it.goccia.app.dati.Distributore

/** Nomi brevi delle bandiere, per riconoscere un distributore sulla mappa a colpo d'occhio. */
object Bandiere {
    private const val MASSIMO = 9

    private val note = listOf(
        "agip" to "Eni",
        "eni" to "Eni",
        "api-ip" to "IP",
        "q8" to "Q8",
        "esso" to "Esso",
        "tamoil" to "Tamoil",
        "shell" to "Shell",
        "total" to "Total",
        "repsol" to "Repsol",
        "enercoop" to "Coop",
    )

    /**
     * "Agip Eni" diventa "Eni", "Api-Ip" diventa "IP". Per le pompe bianche si usa il nome
     * dell'impianto ("Sassomet Srl" diventa "Sassomet"); i nomi lunghi si accorciano.
     */
    fun breve(bandiera: String, nome: String): String? {
        val b = bandiera.trim()
        val minuscolo = b.lowercase()
        note.firstOrNull { (chiave, _) -> parola(minuscolo, chiave) }?.let { return it.second }
        if (minuscolo == "ip") return "IP"
        val base = if (b.isBlank() || minuscolo.startsWith("pomp")) nome.trim() else b
        if (base.isBlank()) return null
        if (base.length <= MASSIMO) return base
        val prima = base.split(' ', '-', '.', ',').firstOrNull { it.length >= 2 } ?: base
        return if (prima.length <= MASSIMO) prima else prima.take(MASSIMO - 1) + "."
    }

    fun breve(d: Distributore): String? = breve(d.bandiera, d.nome)

    /** La chiave compare come parola intera (o come "api-ip"), non dentro un'altra parola. */
    private fun parola(testo: String, chiave: String): Boolean =
        Regex("(^|[^a-z0-9])" + Regex.escape(chiave) + "($|[^a-z0-9])").containsMatchIn(testo)
}
