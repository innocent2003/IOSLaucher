package com.example.oslaucher.models

import android.view.View

internal data class AppPageSwipeTransition(
    val fromPage: Int,
    val toPage: Int,
    val outgoingView: View,
    val incomingView: View,
    val width: Float,
    val direction: Int,
    var progress: Float = 0f
)
