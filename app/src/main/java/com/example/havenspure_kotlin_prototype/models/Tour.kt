package com.example.havenspure_kotlin_prototype.models

data class Tour(
    val id: String,
    val title: String,
    val progress: Int,
    val description: String = "",
    val imageId: Int? = null,
    val points: List<String> = emptyList()
)