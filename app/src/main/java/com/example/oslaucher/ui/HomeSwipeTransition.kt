package com.example.oslaucher.ui

import android.view.View
import androidx.fragment.app.Fragment

internal data class HomeSwipeTransition(
    val fromScreen: Screen,
    val toScreen: Screen,
    val incomingFragment: Fragment?,
    val incomingView: View,
    val outgoingFragment: Fragment?,
    val outgoingView: View,
    val width: Float,
    val direction: Int
)
