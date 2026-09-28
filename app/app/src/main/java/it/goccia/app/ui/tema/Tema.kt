package it.goccia.app.ui.tema

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.goccia.app.R

/** Colori del sistema visivo di Goccia (vedi il canvas di design, tavola "Stile e componenti"). */
object Colori {
    val Petrolio = Color(0xFF0B7A75)
    val PetrolioScuro = Color(0xFF075E5A)
    val PetrolioChiaro = Color(0xFFDDF1EF)
    val PetrolioTenue = Color(0xFFEAF6F5)
    val PetrolioBarre = Color(0xFFB9E0DC)
    val Inchiostro = Color(0xFF0F1B2A)

    val Verde = Color(0xFF16A34A)
    val VerdeTesto = Color(0xFF15803D)
    val VerdeScuro = Color(0xFF14532D)
    val VerdeChiaro = Color(0xFFE7F6EC)
    val Ambra = Color(0xFFF59E0B)
    val AmbraTesto = Color(0xFF92400E)
    val AmbraScuro = Color(0xFFB45309)
    val AmbraChiaro = Color(0xFFFEF3C7)
    val AmbraBordo = Color(0xFFF6D98A)
    val Rosso = Color(0xFFB91C1C)
    val RossoChiaro = Color(0xFFFDECEC)

    val Testo2 = Color(0xFF4A5A6C)
    val Testo3 = Color(0xFF5F6E80)
    val TestoChip = Color(0xFF33475B)
    val Segnaposto = Color(0xFF6B7A8C)
    val Linea = Color(0xFF9AA6B4)

    val Sfondo = Color(0xFFF6F8FB)
    val Superficie = Color.White
    val Bordo = Color(0xFFE3E8EF)
    val BordoControllo = Color(0xFFD5DDE6)
    val Divisore = Color(0xFFEEF2F6)
    val Segmentato = Color(0xFFE9EEF3)
    val Grigio = Color(0xFFF1F4F8)
    val GrigioBadge = Color(0xFFEEF2F6)
    val InterruttoreSpento = Color(0xFFC9D2DC)
    val Tratteggio = Color(0xFFB7C2CE)
    val Notifica = Color(0xFFC2410C)
    val Velo = Color(0x800F1B2A)

    val Crema = Color(0xFFFFF8EB)
    val CremaBordo = Color(0xFFF3DFB5)
    val CremaTesto = Color(0xFF4A3B1F)
    val CremaIcona = Color(0xFF6B5A38)

    val Lavoro = Color(0xFF1D4ED8)
    val LavoroChiaro = Color(0xFFE6ECFA)
}

private fun font(id: Int, peso: FontWeight) = Font(id, peso)

val PlusJakarta = FontFamily(
    font(R.font.plus_jakarta_sans_300light, FontWeight.Light),
    font(R.font.plus_jakarta_sans_400regular, FontWeight.Normal),
    font(R.font.plus_jakarta_sans_500medium, FontWeight.Medium),
    font(R.font.plus_jakarta_sans_600semibold, FontWeight.SemiBold),
    font(R.font.plus_jakarta_sans_700bold, FontWeight.Bold),
    font(R.font.plus_jakarta_sans_800extrabold, FontWeight.ExtraBold),
)

/** Stili di testo: dimensione/peso come nel design. Le cifre sono tabulari per allineare i prezzi. */
object Testi {
    private fun stile(dimensione: Int, peso: FontWeight, spaziatura: Double = 0.0, altezza: Double? = null, cifre: Boolean = false) =
        TextStyle(
            fontFamily = PlusJakarta,
            fontSize = dimensione.sp,
            fontWeight = peso,
            letterSpacing = spaziatura.sp,
            lineHeight = altezza?.let { (dimensione * it).sp } ?: TextStyle.Default.lineHeight,
            fontFeatureSettings = if (cifre) "tnum" else null,
            color = Colori.Inchiostro,
        )

    val Display = stile(32, FontWeight.ExtraBold, -0.8, cifre = true)
    val Onboarding = stile(29, FontWeight.ExtraBold, -0.7, 1.2)
    val TitoloSchermata = stile(26, FontWeight.ExtraBold, -0.6, 1.2)
    val Titolo = stile(22, FontWeight.ExtraBold, -0.4)
    val Foglio = stile(20, FontWeight.ExtraBold, -0.3)
    val Sezione = stile(19, FontWeight.ExtraBold, -0.3)
    val Sottosezione = stile(17, FontWeight.ExtraBold)
    val Voce = stile(16, FontWeight.ExtraBold)
    val CorpoGrande = stile(16, FontWeight.Medium, altezza = 1.5)
    val Corpo = stile(15, FontWeight.Medium, altezza = 1.5)
    val CorpoForte = stile(15, FontWeight.Bold)
    val Pulsante = stile(15, FontWeight.Bold)
    val Chip = stile(14, FontWeight.SemiBold)
    val ChipAttivo = stile(14, FontWeight.Bold)
    val Testo14 = stile(14, FontWeight.Medium, altezza = 1.5)
    val Didascalia = stile(13, FontWeight.Medium, altezza = 1.45)
    val DidascaliaForte = stile(13, FontWeight.Bold)
    val Etichetta = stile(12, FontWeight.Bold)
    val Piccolo = stile(12, FontWeight.SemiBold)
    val Minimo = stile(11, FontWeight.Bold)
    val TitoloGruppo = stile(12, FontWeight.ExtraBold, 0.8)

    val PrezzoGrande = stile(28, FontWeight.ExtraBold, -0.6, cifre = true)
    val PrezzoMappa = stile(26, FontWeight.ExtraBold, -0.6, cifre = true)
    val Prezzo = stile(23, FontWeight.ExtraBold, -0.5, cifre = true)
    val PrezzoMedio = stile(20, FontWeight.ExtraBold, cifre = true)
    val Numero = stile(21, FontWeight.ExtraBold, cifre = true)
    val Importo = stile(44, FontWeight.ExtraBold, -1.2, cifre = true)
}

object Forme {
    val Card: Shape = RoundedCornerShape(18.dp)
    val CardGrande: Shape = RoundedCornerShape(20.dp)
    val Pulsante: Shape = RoundedCornerShape(14.dp)
    val Campo: Shape = RoundedCornerShape(16.dp)
    val Pillola: Shape = RoundedCornerShape(999.dp)
    val Logo: Shape = RoundedCornerShape(12.dp)
    val Foglio: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

/** Ombra morbida delle card, come nel design (due ombre leggerissime). */
fun Modifier.ombra(forma: Shape = Forme.Card, elevazione: Dp = 3.dp): Modifier =
    shadow(elevazione, forma, clip = false, ambientColor = Color(0x1A0F1B2A), spotColor = Color(0x260F1B2A))

private val schema = lightColorScheme(
    primary = Colori.Petrolio,
    onPrimary = Color.White,
    primaryContainer = Colori.PetrolioChiaro,
    onPrimaryContainer = Colori.PetrolioScuro,
    secondary = Colori.Inchiostro,
    onSecondary = Color.White,
    background = Colori.Sfondo,
    onBackground = Colori.Inchiostro,
    surface = Colori.Superficie,
    onSurface = Colori.Inchiostro,
    surfaceVariant = Colori.Grigio,
    onSurfaceVariant = Colori.Testo2,
    surfaceContainer = Colori.Superficie,
    surfaceContainerHigh = Colori.Superficie,
    surfaceContainerHighest = Colori.Grigio,
    surfaceContainerLow = Colori.Superficie,
    outline = Colori.BordoControllo,
    outlineVariant = Colori.Bordo,
    error = Colori.Rosso,
)

private val tipografia = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = PlusJakarta),
        displayMedium = base.displayMedium.copy(fontFamily = PlusJakarta),
        displaySmall = base.displaySmall.copy(fontFamily = PlusJakarta),
        headlineLarge = base.headlineLarge.copy(fontFamily = PlusJakarta),
        headlineMedium = base.headlineMedium.copy(fontFamily = PlusJakarta),
        headlineSmall = base.headlineSmall.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.ExtraBold),
        titleLarge = base.titleLarge.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.ExtraBold),
        titleMedium = base.titleMedium.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontFamily = PlusJakarta),
        bodyMedium = base.bodyMedium.copy(fontFamily = PlusJakarta),
        bodySmall = base.bodySmall.copy(fontFamily = PlusJakarta),
        labelLarge = base.labelLarge.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.Bold),
        labelSmall = base.labelSmall.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun GocciaTema(contenuto: @Composable () -> Unit) {
    MaterialTheme(colorScheme = schema, typography = tipografia, content = contenuto)
}
