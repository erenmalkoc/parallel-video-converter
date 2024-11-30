package dev.erenmalkoc.asyncvideoconverter.ui


data class ConverterUiState(
    val filePaths: List<String> = listOf(),
    val conversionInProgress: Boolean = false,
    val conversionSuccess: Boolean? = null
)