package com.example.oslaucher.models

internal data class Weather(
    val temperature: Double,
    val high: Double,
    val low: Double,
    val description: String,
    val icon: String
)
