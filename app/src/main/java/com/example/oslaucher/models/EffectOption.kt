package com.example.oslaucher.models

internal data class EffectOption(
    val rowId: Int,
    val switchId: Int,
    val preferenceKey: String,
    val defaultValue: Boolean
)
