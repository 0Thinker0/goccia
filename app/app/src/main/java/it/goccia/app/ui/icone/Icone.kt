package it.goccia.app.ui.icone

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icone disegnate a tratto (24x24, spessore 2) come nel design.
 * Si colorano con il parametro tint di Icon.
 */
object Icone {

    private fun cerchio(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    private fun rettangolo(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,${r}v${h - 2 * r}a$r,$r 0 0 1 ${-r},${r}h${-(w - 2 * r)}a$r,$r 0 0 1 ${-r},${-r}v${-(h - 2 * r)}a$r,$r 0 0 1 $r,${-r}z"

    private fun tratto(nome: String, vararg percorsi: String, spessore: Float = 2f): ImageVector {
        val b = ImageVector.Builder(
            name = nome,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (p in percorsi) {
            b.addPath(
                pathData = addPathNodes(p),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = spessore,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private fun pieno(nome: String, vararg percorsi: String): ImageVector {
        val b = ImageVector.Builder(
            name = nome,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (p in percorsi) b.addPath(pathData = addPathNodes(p), fill = SolidColor(Color.Black))
        return b.build()
    }

    val Casa by lazy { tratto("casa", "M3 10.5 12 3l9 7.5", "M5 9.5V20a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9.5") }
    val Mappa by lazy { tratto("mappa", "M9 4 3 6.5V20l6-2.5 6 2.5 6-2.5V4l-6 2.5L9 4z", "M9 4v13.5M15 6.5V20") }
    val Percorso by lazy {
        tratto("percorso", cerchio(6f, 19f, 3f), "M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15", cerchio(18f, 5f, 3f))
    }
    val Stella by lazy { tratto("stella", "M12 2.5l2.9 5.9 6.6 1-4.8 4.6 1.1 6.5L12 17.4l-5.8 3.1 1.1-6.5-4.8-4.6 6.6-1z") }
    val StellaPiena by lazy { pieno("stella piena", "M12 2.5l2.9 5.9 6.6 1-4.8 4.6 1.1 6.5L12 17.4l-5.8 3.1 1.1-6.5-4.8-4.6 6.6-1z") }
    val Persona by lazy { tratto("persona", cerchio(12f, 8f, 4f), "M4 21a8 8 0 0 1 16 0") }
    val Campanella by lazy { tratto("campanella", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0") }
    val Auto by lazy {
        tratto(
            "auto",
            "M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2",
            cerchio(7f, 17f, 2f),
            "M9 17h6",
            cerchio(17f, 17f, 2f),
        )
    }
    val Pompa by lazy {
        tratto(
            "pompa",
            "M3 22h12",
            "M4 9h10",
            "M14 22V4a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v18",
            "M14 13h2a2 2 0 0 1 2 2v2a2 2 0 0 0 4 0V9.8a2 2 0 0 0-.6-1.4L18 5",
        )
    }
    val Naviga by lazy { tratto("naviga", "M12 2 19 21 12 17 5 21 12 2z") }
    val Piu by lazy { tratto("più", "M12 5v14M5 12h14", spessore = 2.4f) }
    val Meno by lazy { tratto("meno", "M5 12h14", spessore = 2.6f) }
    val Chiudi by lazy { tratto("chiudi", "M18 6 6 18M6 6l12 12", spessore = 2.4f) }
    val Indietro by lazy { tratto("indietro", "M19 12H5M12 19l-7-7 7-7", spessore = 2.2f) }
    val Avanti by lazy { tratto("avanti", "M5 12h14M12 5l7 7-7 7", spessore = 2.4f) }
    val ChevronDestra by lazy { tratto("apri", "m9 18 6-6-6-6", spessore = 2.2f) }
    val ChevronGiu by lazy { tratto("scegli", "m6 9 6 6 6-6", spessore = 2.2f) }
    val Cerca by lazy { tratto("cerca", cerchio(11f, 11f, 7f), "m20 20-3.5-3.5", spessore = 2.2f) }
    val Lista by lazy { tratto("lista", "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01", spessore = 2.2f) }
    val Mirino by lazy { tratto("mirino", cerchio(12f, 12f, 4f), "M12 2v3M12 19v3M2 12h3M19 12h3", spessore = 2.2f) }
    val Orologio by lazy { tratto("orologio", cerchio(12f, 12f, 9f), "M12 7v5l3 2", spessore = 2.2f) }
    val Attenzione by lazy {
        tratto("attenzione", "M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z", "M12 9v4M12 17h.01", spessore = 2.2f)
    }
    val Info by lazy { tratto("info", cerchio(12f, 12f, 10f), "M12 16v-4M12 8h.01") }
    val Spunta by lazy { tratto("spunta", "M20 6 9 17l-5-5", spessore = 2.6f) }
    val Scudo by lazy { tratto("scudo", "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z") }
    val Condividi by lazy {
        tratto("condividi", cerchio(18f, 5f, 3f), cerchio(6f, 12f, 3f), cerchio(18f, 19f, 3f), "m8.6 13.5 6.8 4M15.4 6.5l-6.8 4")
    }
    val Ingranaggio by lazy {
        tratto(
            "impostazioni",
            cerchio(12f, 12f, 3f),
            "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
        )
    }
    val Lavoro by lazy { tratto("lavoro", rettangolo(2f, 7f, 20f, 14f, 2f), "M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16") }
    val Fulmine by lazy { pieno("fulmine", "M13 2 3 14h9l-1 8 10-12h-9l1-8z") }
    val Cuore by lazy {
        pieno(
            "cuore",
            "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z",
        )
    }
    val Caffe by lazy { tratto("caffè", "M17 8h1a4 4 0 1 1 0 8h-1", "M3 8h14v9a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4Z", "M6 2v2M10 2v2M14 2v2") }
    val InCalo by lazy { tratto("in calo", "m22 17-8.5-8.5-5 5L2 7", "M16 17h6v-6", spessore = 2.4f) }
    val InSalita by lazy { tratto("in salita", "m22 7-8.5 8.5-5-5L2 17", "M16 7h6v6", spessore = 2.4f) }
    val Registro by lazy {
        tratto("registro", "M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z", "M8 7h8M8 11h8M8 15h5")
    }
    val Grafico by lazy { tratto("grafico", "M3 3v18h18", "M8 17v-6M13 17V7M18 17v-4") }
    val Scarica by lazy { tratto("scarica", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3") }
    val Carica by lazy { tratto("carica", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M17 8l-5-5-5 5", "M12 3v12") }
    val Nuvola by lazy { tratto("nuvola", "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z") }
    val Database by lazy {
        tratto("dati", "M3 5a9 3 0 1 0 18 0a9 3 0 1 0 -18 0", "M21 12c0 1.66-4 3-9 3s-9-1.34-9-3", "M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5")
    }
    val Luna by lazy { tratto("luna", "M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z", spessore = 2.2f) }
    val Segnaposto by lazy { tratto("posizione", "M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z", cerchio(12f, 10f, 3f)) }
    val PosizioneSpenta by lazy {
        tratto(
            "posizione spenta",
            "M9.3 4.3A8 8 0 0 1 20 10c0 1.9-.7 3.8-1.7 5.5",
            "M16 18.3C14 20.4 12 22 12 22s-8-6-8-12c0-1.4.4-2.8 1-4",
            "M12 7a3 3 0 0 1 3 3",
            "M2 2l20 20",
            spessore = 1.7f,
        )
    }
    val Offline by lazy {
        tratto(
            "offline",
            "M2 2l20 20",
            "M16.7 11.1A11 11 0 0 1 19 12.6",
            "M5 12.6a11 11 0 0 1 5.2-2.4",
            "M10.7 5A16 16 0 0 1 22.6 9",
            "M1.4 9a16 16 0 0 1 4.7-2.9",
            "M8.5 16.1a6 6 0 0 1 7 0",
            "M12 20h.01",
            spessore = 2.2f,
        )
    }
    val Ordina by lazy { tratto("ordina", "M7 16V4M3 8l4-4 4 4", "M17 8v12M21 16l-4 4-4-4", spessore = 2.2f) }
    val Autostrada by lazy { tratto("autostrada", "M4 19 8 5M16 5l4 14M12 6v2M12 11v2M12 16v2", spessore = 2.2f) }
    val Matita by lazy { tratto("modifica", "M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z") }
    val Cestino by lazy {
        tratto("elimina", "M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", "M10 11v6M14 11v6")
    }
    val Bandiera by lazy { tratto("bandiera", "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z", "M4 22v-7") }
    val Aggiorna by lazy { tratto("aggiorna", "M21 12a9 9 0 1 1-2.64-6.36", "M21 3v6h-6", spessore = 2.2f) }
    val Codice by lazy { tratto("codice", "m16 18 6-6-6-6", "m8 6-6 6 6 6") }
    val Filtro by lazy { tratto("filtro", "M3 5h18l-7 8.5V20l-4-2v-4.5z") }

    /** La goccia del logo: bianca con il riflesso, da usare con Image (non va colorata). */
    val Goccia by lazy {
        ImageVector.Builder("goccia", 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = addPathNodes("M12 2.5c3.6 4.4 6.6 8.2 6.6 11.6a6.6 6.6 0 0 1-13.2 0c0-3.4 3-7.2 6.6-11.6z"),
                fill = SolidColor(Color.White),
            )
            .addPath(
                pathData = addPathNodes("M9.2 14.6a2.9 2.9 0 0 0 2.8 2.6"),
                stroke = SolidColor(Color(0xFF0B7A75)),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
            )
            .build()
    }

    /** Solo la sagoma della goccia, colorabile. */
    val GocciaPiena by lazy { pieno("goccia piena", "M12 2.5c3.6 4.4 6.6 8.2 6.6 11.6a6.6 6.6 0 0 1-13.2 0c0-3.4 3-7.2 6.6-11.6z") }
}
