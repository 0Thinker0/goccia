package it.goccia.app.ui.componenti

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import it.goccia.app.ui.tema.Colori

/**
 * Le fonti pubbliche dei dati, con i link ai siti ufficiali: Google Play chiede che chi mostra
 * informazioni di enti pubblici porti alla fonte originale e dica di non esserne affiliato.
 */
object Fonti {
    /** Osservaprezzi carburanti: la ricerca ufficiale dei prezzi in vigore. */
    const val OSSERVAPREZZI = "https://carburanti.mise.gov.it/ospzSearch/home"

    /** I file aperti del Ministero con prezzi e anagrafica degli impianti. */
    const val OPEN_DATA_MIMIT = "https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti"

    /** Piattaforma Unica Nazionale dei punti di ricarica (GSE). */
    const val PUN = "https://www.piattaformaunicanazionale.it/"

    const val NON_AFFILIATA =
        "Goccia è un'app indipendente: non è affiliata, approvata o gestita dal Ministero delle Imprese e del Made in Italy, " +
            "dal GSE o da altri enti pubblici. Mostra i loro dati aperti, che puoi verificare sui siti ufficiali qui sotto."
}

/** Una nota piccola che finisce con il link alla fonte ufficiale: "[testo] Fonte: [etichetta]". */
@Composable
fun NotaFonte(testo: String, etichetta: String, url: String, stile: TextStyle, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(testo)
            append(" Fonte ufficiale: ")
            withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(SpanStyle(color = Colori.Petrolio, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)),
                ),
            ) { append(etichetta) }
            append(".")
        },
        style = stile,
        modifier = modifier,
    )
}
