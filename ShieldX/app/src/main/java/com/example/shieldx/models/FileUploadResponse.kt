package com.example.shieldx.models

data class FileUploadResponse(
    val success: Boolean,
    val message: String,
    val fileId: String? = null,
    val fileName: String? = null,
    val url: String? = null,
    val error: String? = null
)