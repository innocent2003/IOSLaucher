package com.example.oslaucher.models

internal data class SwitchOption(
    val rowId: Int,
    val switchId: Int,
    val preferenceKey: String,
    val defaultValue: Boolean
)
