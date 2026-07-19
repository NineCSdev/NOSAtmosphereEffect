package com.app.nosatmosphereeffect.ui.model

data class PaletteDiagnostics(
    val extractedColors: List<Int> = emptyList(),
    val wallpaperApiColors: List<Int> = emptyList(),
    val systemColors: List<Int> = emptyList(),
    val systemColorSource: String? = null,
    val systemSeedColor: Int? = null,
    val messages: List<PaletteDiagnosticMessage> = emptyList()
)

data class PaletteDiagnosticMessage(
    val level: PaletteDiagnosticLevel,
    val title: String,
    val detail: String? = null
)

enum class PaletteDiagnosticLevel {
    SUCCESS,
    INFO,
    WARNING,
    ERROR
}
