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

// Pagina per le donazioni (PayPal.me, Ko-fi...): vuota = nessun pulsante per donare.
val donazioni: String = providers.gradleProperty("goccia.donazioni").orNull.orEmpty()

android {
    namespace = "it.goccia.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.goccia.app"
        minSdk = 26
        targetSdk = 36
        versionCode = numeroBuild
        versionName = "0.1.$numeroBuild"
        buildConfigField("String", "DATI_URL", "\"$datiUrl\"")
        buildConfigField("String", "REPO", "\"$repository\"")
        buildConfigField("String", "DONAZIONI", "\"$donazioni\"")
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
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        // La versione che si installa sul telefono: non "debuggable", quindi molto piu fluida,
        // firmata con la stessa chiave fissa cosi ogni nuova build si installa sopra la precedente.
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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

    testImplementation(libs.junit)
}
