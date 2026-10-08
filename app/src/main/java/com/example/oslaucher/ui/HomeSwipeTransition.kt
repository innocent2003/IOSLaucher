package com.example.oslaucher.ui

import android.view.View
import androidx.fragment.app.Fragment
import com.example.oslaucher.utils.Screen

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

internal data class AppPageSwipeTransition(
    val fromPage: Int,
    val toPage: Int,
    val outgoingView: View,
    val incomingView: View,
    val width: Float,
    val direction: Int
)
