plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Indirizzo dei dati: variabile d'ambiente (build su GitHub) oppure gradle.properties.
val datiUrl: String = (System.getenv("GOCCIA_DATI_URL")?.takeIf { it.isNotBlank() }
    ?: providers.gradleProperty("goccia.datiUrl").get())
    .let { if (it.endsWith("/")) it else "$it/" }

val numeroBuild: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// Per la prova sull'emulatore (x86_64) servono anche le librerie native x86.
val tutteLeAbi: Boolean = providers.gradleProperty("goccia.tutteLeAbi").isPresent

// Repository GitHub (per i link "segnala un problema" e "codice sorgente").
val repository: String = System.getenv("GITHUB_REPOSITORY")?.takeIf { it.isNotBlank() }
    ?: providers.gradleProperty("goccia.repo").get()

// Informativa sulla privacy (pagina del progetto sul sito dell'autore).
val privacyUrl: String = providers.gradleProperty("goccia.privacy").get()

// Versione per Google Play (-Pgoccia.play): i caffe si offrono con gli acquisti in-app di Google
// Play e non c'e nessun link esterno per donare (le regole di Play non lo permettono).
val perPlay: Boolean = providers.gradleProperty("goccia.play").isPresent

// Pagina per le donazioni (PayPal.me, Ko-fi...) della versione scaricata da GitHub:
// vuota = nessun pulsante per donare. Mai nella versione per Google Play.
val donazioni: String = if (perPlay) "" else providers.gradleProperty("goccia.donazioni").orNull.orEmpty()

// Chiave privata per firmare le versioni pubblicate (sulla build di GitHub arriva dai secret,
// vedi .github/workflows/android.yml). Senza, si usa la chiave di prova fissa dell'anteprima.
val chiaveRilascio: File? = System.getenv("GOCCIA_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "it.goccia.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.goccia.app"
        minSdk = 26
        targetSdk = 36
        versionCode = numeroBuild
        versionName = "1.0.$numeroBuild"
        buildConfigField("String", "DATI_URL", "\"$datiUrl\"")
        buildConfigField("String", "REPO", "\"$repository\"")
        buildConfigField("String", "PRIVACY_URL", "\"$privacyUrl\"")
        buildConfigField("String", "DONAZIONI", "\"$donazioni\"")
        buildConfigField("boolean", "PLAY", "$perPlay")
        // i telefoni Android sono ARM: niente librerie x86 (solo emulatori) e APK piu leggero
        if (!tutteLeAbi) ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        // Chiave di debug fissa: permette di aggiornare l'app sul telefono build dopo build.
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (chiaveRilascio != null) {
            create("pubblica") {
                storeFile = chiaveRilascio
                storePassword = System.getenv("GOCCIA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GOCCIA_KEY_ALIAS")
                keyPassword = System.getenv("GOCCIA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        // La versione che si installa sul telefono: non "debuggable", quindi molto piu fluida.
        // Firmata con la chiave privata se c'e, altrimenti con quella di prova fissa: in entrambi i
        // casi ogni nuova build si installa sopra la precedente firmata con la stessa chiave.
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (chiaveRilascio != null) "pubblica" else "debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // librerie native compresse: download molto piu piccolo per l'installazione manuale
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.maplibre)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.play.billing)

    testImplementation(libs.junit)
}
