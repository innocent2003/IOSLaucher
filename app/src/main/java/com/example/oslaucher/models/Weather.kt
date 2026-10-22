package com.example.oslaucher.models

internal data class Weather(
    val temperature: Double,
    val high: Double,
    val low: Double,
    val descriptionResId: Int,
    val icon: String
)
