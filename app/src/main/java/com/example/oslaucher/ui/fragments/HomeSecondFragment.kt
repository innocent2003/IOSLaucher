package com.example.oslaucher.ui.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import com.example.oslaucher.R
import com.example.oslaucher.models.AppInfo
import com.example.oslaucher.utils.AppIconUtils

class HomeSecondFragment : HomeScreenFragment() {
    override val layoutResId = R.layout.screen_home_second

    private val folderCategories = listOf(
        "Khác" to emptyList(),
        "Xã hội và truyền thông" to listOf(
            "facebook", "instagram", "messenger", "zalo", "telegram", "whatsapp",
            "discord", "wechat", "line", "snapchat", "tiktok", "social"
        ),
        "Sản xuất" to listOf(
            "office", "word", "excel", "powerpoint", "drive", "docs", "sheets",
            "calendar", "todo", "task", "authenticator", "scanner", "productivity"
        ),
        "Ảnh và hình ảnh" to listOf(
            "photo", "gallery", "camera", "image", "picsart", "lightroom", "snapseed"
        ),
        "Phim và video" to listOf(
            "youtube", "netflix", "video", "player", "capcut", "vlc", "movie", "stream"
        ),
        "Tin tức và tạp chí" to listOf(
            "news", "newspaper", "magazine", "vnexpress", "dantri", "bbc", "cnn"
        ),
        "Nhạc và âm thanh" to listOf(
            "music", "spotify", "soundcloud", "audio", "podcast", "mp3", "youtube music"
        ),
        "Trò chơi" to listOf(
            "game", "play games", "pubg", "mobile legends", "roblox", "minecraft"
        )
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val folders = view.findViewById<GridLayout>(R.id.home_app_folders)
        folders.removeAllViews()
        val viewLifecycle = viewLifecycleOwner.lifecycle
        (activity as? HomeScreenActions)?.loadHomeScreenApps { apps ->
            if (viewLifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                renderFolders(folders, apps)
            }
        }
    }

    private fun renderFolders(folders: GridLayout, apps: List<AppInfo>) {
        val assignedApps = apps.groupBy(::categoryFor)
        val visibleCategories = folderCategories.map { it.first }
            .mapNotNull { category ->
                assignedApps[category].orEmpty().takeIf { it.isNotEmpty() }
                    ?.let { category to it }
            }
        folders.rowCount = (visibleCategories.size + 1) / 2
        folders.visibility = View.VISIBLE
        visibleCategories.forEachIndexed { index, (category, categoryApps) ->

            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
            }
            val iconGrid = GridLayout(requireContext()).apply {
                columnCount = 2
                rowCount = 2
                background = requireContext().getDrawable(R.drawable.bg_glass)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val displayedApps = categoryApps.take(8)
            val previewApps = if (displayedApps.size > 4) {
                displayedApps.drop(3)
            } else {
                emptyList()
            }
            val primaryApps = if (previewApps.isEmpty()) {
                displayedApps
            } else {
                displayedApps.take(3)
            }
            primaryApps.forEachIndexed { appIndex, app ->
                iconGrid.addView(
                    createAppIcon(app),
                    GridLayout.LayoutParams(
                        GridLayout.spec(appIndex / 2, 1f),
                        GridLayout.spec(appIndex % 2, 1f)
                    ).apply {
                        width = 0
                        height = dp(62)
                        setMargins(dp(2), dp(2), dp(2), dp(2))
                    }
                )
            }
            if (previewApps.isNotEmpty()) {
                val previewGrid = GridLayout(requireContext()).apply {
                    columnCount = 3
                    rowCount = 2
                    alignmentMode = GridLayout.ALIGN_BOUNDS
                    useDefaultMargins = false
                }
                previewApps.forEachIndexed { appIndex, app ->
                    previewGrid.addView(
                        createAppIcon(app),
                        GridLayout.LayoutParams(
                            GridLayout.spec(appIndex / 3, 1f),
                            GridLayout.spec(appIndex % 3, 1f)
                        ).apply {
                            width = 0
                            height = dp(28)
                            setMargins(dp(1), dp(1), dp(1), dp(1))
                        }
                    )
                }
                iconGrid.addView(
                    previewGrid,
                    GridLayout.LayoutParams(
                        GridLayout.spec(1, 1f),
                        GridLayout.spec(1, 1f)
                    ).apply {
                        width = 0
                        height = dp(62)
                        setMargins(dp(2), dp(2), dp(2), dp(2))
                    }
                )
            }
            card.addView(
                iconGrid,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            card.addView(TextView(requireContext()).apply {
                text = category
                gravity = Gravity.CENTER
                setTextColor(0xFFF3F7FB.toInt())
                textSize = 14f
                maxLines = 1
                setPadding(0, dp(6), 0, 0)
            })

            val row = index / 2
            val column = index % 2
            val cardParams = GridLayout.LayoutParams(
                GridLayout.spec(row),
                GridLayout.spec(column, 1f)
            ).apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(
                    if (column == 0) 0 else dp(8),
                    0,
                    if (column == 0) dp(8) else 0,
                    dp(16)
                )
            }
            folders.addView(card, cardParams)
        }
    }

    private fun createAppIcon(app: AppInfo): ImageView =
        ImageView(requireContext()).apply {
            setImageDrawable(AppIconUtils.getIcon(requireContext(), app))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = app.name
            isClickable = true
            isFocusable = true
            setOnClickListener {
                (activity as? HomeScreenActions)?.launchHomeScreenApp(app.packageName)
            }
        }

    private fun categoryFor(app: AppInfo): String {
        val searchable = "${app.name} ${app.packageName}".lowercase()
        return folderCategories.drop(1).firstOrNull { (_, keywords) ->
            keywords.any(searchable::contains)
        }?.first ?: folderCategories.first().first
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
