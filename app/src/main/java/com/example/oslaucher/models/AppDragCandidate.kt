package com.example.oslaucher.models

import android.view.View
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import com.example.oslaucher.adapters.AppAdapter

internal data class AppDragCandidate(
    val app: AppInfo,
    val source: View
)

internal data class FolderDragState(
    val folder: AppFolder,
    val draggedApp: AppInfo,
    var apps: List<AppInfo>,
    val grid: GridView,
    val adapter: AppAdapter,
    val root: FrameLayout,
    val popupWindow: PopupWindow,
    val source: View,
    val sourceVisibility: Int,
    val shadow: ImageView,
    val label: TextView,
    val downX: Float,
    val downY: Float,
    var moved: Boolean = false
)
