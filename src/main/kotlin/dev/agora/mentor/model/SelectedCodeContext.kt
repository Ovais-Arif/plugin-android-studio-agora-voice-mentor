package dev.agora.mentor.model

data class SelectedCodeContext(
    val projectName: String,
    val fileName: String,
    val filePath: String,
    val language: String,
    val selectedCode: String,
    val surroundingContext: String?,
    val timestamp: Long
)
