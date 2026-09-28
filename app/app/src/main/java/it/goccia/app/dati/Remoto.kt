package it.goccia.app.dati

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

// Formato dei file JSON pubblicati dalla pipeline (cartella pipeline/ del repository).
// Tutti i prezzi sono in millesimi di euro: 1689 = 1,689 €.

@Serializable
data class IndiceDto(
    val v: Int = 1,
    val estrazione: String,
    val generato: String = "",
    val fonte: String = "",
    val nazionale: NazionaleDto = NazionaleDto(),
    val province: Map<String, ProvinciaDto> = emptyMap(),
)

@Serializable
data class NazionaleDto(
    val medie: Map<String, MediaDto> = emptyMap(),
    val giorni: List<String> = emptyList(),
    val storico: Map<String, SerieDto> = emptyMap(),
)

@Serializable
data class MediaDto(val s: ValoreMediaDto? = null, val v: ValoreMediaDto? = null)

@Serializable
data class ValoreMediaDto(val p: Int, val n: Int)

@Serializable
data class SerieDto(val s: List<Int?>? = null, val v: List<Int?>? = null)

@Serializable
data class ProvinciaDto(
    val nome: String,
    val n: Int = 0,
    val bbox: List<Double> = emptyList(),
    val centro: List<Double> = emptyList(),
    val medie: Map<String, MediaDto> = emptyMap(),
)

@Serializable
data class PrezziProvinciaDto(
    val v: Int = 1,
    val prov: String,
    val estrazione: String,
    val medie: Map<String, MediaDto> = emptyMap(),
    val impianti: List<ImpiantoDto> = emptyList(),
)

@Serializable
data class ImpiantoDto(
    val id: Long,
    val b: String = "",
    val n: String = "",
    val i: String = "",
    val c: String = "",
    val t: String = "S",
    val la: Double,
    val lo: Double,
    val p: Map<String, PrezzoDto> = emptyMap(),
    val x: List<SpecialeDto> = emptyList(),
)

@Serializable
data class PrezzoDto(val s: Int? = null, val st: Long? = null, val v: Int? = null, val vt: Long? = null)

@Serializable
data class SpecialeDto(val d: String, val f: String? = null, val p: Int, val s: Int = 0, val t: Long = 0)

@Serializable
data class StoricoDto(
    val v: Int = 1,
    val prov: String,
    val giorni: List<String> = emptyList(),
    val medie: Map<String, SerieDto> = emptyMap(),
)

@Serializable
data class CronologiaDto(
    val v: Int = 1,
    val prov: String,
    val giorni: List<String> = emptyList(),
    val mesi: List<String> = emptyList(),
    val impianti: Map<String, CronoImpiantoDto> = emptyMap(),
)

/** Righe [nome, provincia, lat, lon, numero distributori]. */
@Serializable
data class ComuniDto(
    val v: Int = 1,
    val estrazione: String = "",
    val comuni: List<JsonArray> = emptyList(),
)

@Serializable
data class CronoImpiantoDto(
    val g: Map<String, List<Int?>> = emptyMap(),
    val m: Map<String, List<Int?>> = emptyMap(),
)
