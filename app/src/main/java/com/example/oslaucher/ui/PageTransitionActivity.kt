package com.example.oslaucher.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oslaucher.R

class PageTransitionActivity : AppCompatActivity() {
    private var selectedEffect = DEFAULT_EFFECT
    private lateinit var optionRows: List<Pair<LinearLayout, TextView>>
    private lateinit var previewContainer: FrameLayout
    private lateinit var previewGrid: GridLayout
    private var previewAnimationRunning = false
    private var previewPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_page_transition)

        val root = findViewById<View>(R.id.page_transition_root)
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                baseLeft + systemBars.left,
                baseTop + systemBars.top,
                baseRight + systemBars.right,
                baseBottom + systemBars.bottom
            )
            insets
        }

        val preferences = getSharedPreferences(
            PageTransitionAnimator.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        selectedEffect = preferences.getInt(PageTransitionAnimator.EFFECT_KEY, DEFAULT_EFFECT)
            .coerceIn(DEFAULT_EFFECT, FADE_EFFECT)

        optionRows = listOf(
            R.id.page_transition_default to R.id.page_transition_default_check,
            R.id.page_transition_rotate to R.id.page_transition_rotate_check,
            R.id.page_transition_flip to R.id.page_transition_flip_check,
            R.id.page_transition_zoom to R.id.page_transition_zoom_check,
            R.id.page_transition_fade to R.id.page_transition_fade_check
        ).map { (rowId, checkId) ->
            findViewById<LinearLayout>(rowId) to findViewById(checkId)
        }

        optionRows.forEachIndexed { index, (row, _) ->
            row.background = roundedBackground(OPTION_BACKGROUND)
            row.setOnClickListener {
                selectedEffect = index
                preferences.edit()
                    .putInt(PageTransitionAnimator.EFFECT_KEY, selectedEffect)
                    .apply()
                updateSelection()
                animatePreview()
            }
        }

        findViewById<View>(R.id.page_transition_back).apply {
            background = circleBackground(0xFF303033.toInt())
            setOnClickListener { finish() }
        }
        createPreviewIcons()
        updateSelection()
    }

    private fun createPreviewIcons() {
        previewContainer = findViewById(R.id.page_transition_preview)
        previewGrid = createPreviewGrid(previewPage)
        previewContainer.addView(
            previewGrid,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                setMargins(dp(58), dp(58), 0, 0)
            }
        )
    }

    private fun createPreviewGrid(page: Int): GridLayout {
        val preview = GridLayout(this).apply {
            columnCount = 4
            rowCount = 6
        }
        val icons = if (page % 2 == 0) PREVIEW_ICONS else NEXT_PAGE_ICONS
        val colors = if (page % 2 == 0) PREVIEW_COLORS else NEXT_PAGE_COLORS
        icons.forEachIndexed { index, icon ->
            val tile = TextView(this).apply {
                text = icon
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                background = roundedBackground(colors[index])
                contentDescription = null
            }
            val margin = dp(2)
            preview.addView(
                tile,
                GridLayout.LayoutParams().apply {
                    width = dp(20)
                    height = dp(20)
                    setMargins(margin, margin, margin, margin)
                }
            )
        }
        return preview
    }

    private fun animatePreview() {
        if (previewAnimationRunning) return
        previewAnimationRunning = true

        val outgoing = previewGrid
        val nextPage = previewPage + 1
        val incoming = createPreviewGrid(nextPage)
        previewContainer.addView(
            incoming,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                setMargins(dp(58), dp(58), 0, 0)
            }
        )
        previewGrid = incoming
        PageTransitionAnimator.animate(
            this,
            outgoing,
            incoming,
            -1,
            selectedEffect,
            PREVIEW_DURATION
        ) {
            previewContainer.removeView(outgoing)
            previewPage = nextPage
            previewAnimationRunning = false
        }
    }

    private fun updateSelection() {
        optionRows.forEachIndexed { index, (row, check) ->
            val selected = index == selectedEffect
            row.background = roundedBackground(
                if (selected) SELECTED_OPTION_BACKGROUND else OPTION_BACKGROUND
            )
            check.visibility = if (selected) View.VISIBLE else View.GONE
        }
    }

    private fun circleBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun roundedBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(25).toFloat()
        setColor(color)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val DEFAULT_EFFECT = 0
        private const val FADE_EFFECT = 4
        private const val PREVIEW_DURATION = 700L
        private const val OPTION_BACKGROUND = 0xFF3C3C3E.toInt()
        private const val SELECTED_OPTION_BACKGROUND = 0xFF414143.toInt()
        private val PREVIEW_ICONS = arrayOf(
            "🌈", "📊", "📝", "🪧",
            "📖", "🧮", "📅", "📷",
            "🕒", "🌑", "🌐", "📹",
            "📁", "🎨", "🎬", "🧭",
            "🗺️", "🎸", "❤️", "🏠",
            "🖌️", "🎮", "🎵", "💬"
        )
        private val PREVIEW_COLORS = intArrayOf(
            0xFFFF9D4D.toInt(), 0xFF38C77C.toInt(), 0xFFFFA53B.toInt(), 0xFF4CA4F5.toInt(),
            0xFFFFB947.toInt(), 0xFFB8B8C3.toInt(), 0xFFFFFFFF.toInt(), 0xFF827B82.toInt(),
            0xFFFFFFFF.toInt(), 0xFF555363.toInt(), 0xFFD6D6D6.toInt(), 0xFF20D36D.toInt(),
            0xFF58B7F5.toInt(), 0xFF5955B8.toInt(), 0xFFFF694F.toInt(), 0xFF42BE79.toInt(),
            0xFF9CDAF2.toInt(), 0xFFFE7454.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),
            0xFF56C7D5.toInt(), 0xFFEE725A.toInt(), 0xFFFFFFFF.toInt(), 0xFF5EA9F5.toInt()
        )
        private val NEXT_PAGE_ICONS = arrayOf(
            "📍", "🎧", "📰", "🧩",
            "🛒", "🗓️", "🎙️", "📦",
            "🌤️", "🧘", "🎯", "🗂️",
            "📈", "💡", "🧵", "🌍",
            "🎹", "🧪", "📚", "🪴",
            "🔔", "📝", "🛠️", "✉️"
        )
        private val NEXT_PAGE_COLORS = intArrayOf(
            0xFF58A9E8.toInt(), 0xFF2D2D32.toInt(), 0xFFFFC34D.toInt(), 0xFF7564D8.toInt(),
            0xFF50BD85.toInt(), 0xFFFFFFFF.toInt(), 0xFF514F59.toInt(), 0xFF4D96E8.toInt(),
            0xFF85C7F2.toInt(), 0xFF62BE83.toInt(), 0xFFFF835E.toInt(), 0xFF6277A9.toInt(),
            0xFF5E9DEB.toInt(), 0xFFFFC95C.toInt(), 0xFF9A79CD.toInt(), 0xFF51B997.toInt(),
            0xFF4A4A51.toInt(), 0xFF56A6D8.toInt(), 0xFFB9A178.toInt(), 0xFF54B975.toInt(),
            0xFFF0BB45.toInt(), 0xFFFFFFFF.toInt(), 0xFF667D9C.toInt(), 0xFF5B9EE8.toInt()
        )
    }
}
