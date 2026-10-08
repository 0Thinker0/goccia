package it.goccia.app.logica

import it.goccia.app.dati.Distributore

/**
 * Nomi brevi delle bandiere, per riconoscere un distributore sulla mappa a colpo d'occhio,
 * e i gruppi tra cui scegliere le soste dei viaggi.
 */
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

    /** Le bandiere tra cui scegliere le soste dei viaggi: le piu diffuse in Italia. */
    val PER_VIAGGI = listOf("Eni", "IP", "Q8", "Esso", "Tamoil", "Shell")
    const val POMPE_BIANCHE = "Pompe bianche"
    const val ALTRE = "Altre bandiere"

    /** Le voci del filtro dei viaggi, nell'ordine in cui si mostrano. */
    val SCELTE_VIAGGI = PER_VIAGGI + POMPE_BIANCHE + ALTRE

    /** Il gruppo di un distributore per il filtro dei viaggi: una delle [SCELTE_VIAGGI]. */
    fun gruppo(bandiera: String): String {
        val minuscolo = bandiera.trim().lowercase()
        if (minuscolo.isBlank() || minuscolo.startsWith("pomp")) return POMPE_BIANCHE
        val nota = note.firstOrNull { (chiave, _) -> parola(minuscolo, chiave) }?.second ?: "IP".takeIf { minuscolo == "ip" }
        return nota?.takeIf { it in PER_VIAGGI } ?: ALTRE
    }

    /** Vero se il distributore e di una delle bandiere [scelte] (gruppi di [gruppo]); nessuna scelta = tutte. */
    fun ammesso(d: Distributore, scelte: Set<String>): Boolean = scelte.isEmpty() || gruppo(d.bandiera) in scelte

    /** La chiave compare come parola intera (o come "api-ip"), non dentro un'altra parola. */
    private fun parola(testo: String, chiave: String): Boolean =
        Regex("(^|[^a-z0-9])" + Regex.escape(chiave) + "($|[^a-z0-9])").containsMatchIn(testo)
}
