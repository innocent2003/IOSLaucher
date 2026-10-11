package com.example.oslaucher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oslaucher.R

class ScreenGridActivity : AppCompatActivity() {
    private lateinit var preview: ScreenGridPreviewView
    private lateinit var options: List<TextView>
    private var selectedRows = DEFAULT_GRID_ROWS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_screen_grid)

        val root = findViewById<View>(R.id.screen_grid_root)
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

        selectedRows = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getInt(GRID_ROWS_KEY, DEFAULT_GRID_ROWS)
            .coerceIn(MIN_GRID_ROWS, MAX_GRID_ROWS)
        preview = findViewById(R.id.screen_grid_preview)
        options = listOf(
            findViewById(R.id.screen_grid_5),
            findViewById(R.id.screen_grid_6),
            findViewById(R.id.screen_grid_7)
        )
        findViewById<View>(R.id.screen_grid_options).background =
            roundedBackground(0xFF1D1D1F.toInt())
        options.forEachIndexed { index, option ->
            val rows = MIN_GRID_ROWS + index
            option.setOnClickListener { selectGridRows(rows) }
        }
        findViewById<View>(R.id.screen_grid_back).apply {
            background = circleBackground(0xFF303033.toInt())
            setOnClickListener { finish() }
        }
        findViewById<View>(R.id.screen_grid_confirm).apply {
            background = circleBackground(0xFF078BFF.toInt())
            setOnClickListener {
                getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(GRID_ROWS_KEY, selectedRows)
                    .apply()
                setResult(RESULT_OK)
                finish()
            }
        }
        updateSelection()
    }

    private fun selectGridRows(rows: Int) {
        selectedRows = rows.coerceIn(MIN_GRID_ROWS, MAX_GRID_ROWS)
        updateSelection()
    }

    private fun updateSelection() {
        preview.gridRows = selectedRows
        options.forEachIndexed { index, option ->
            val selected = MIN_GRID_ROWS + index == selectedRows
            option.background = if (selected) roundedBackground(0xFF29292B.toInt()) else null
            option.setTextColor(if (selected) Color.WHITE else 0xFF9D9D9F.toInt())
            option.paint.isFakeBoldText = selected
        }
    }

    private fun circleBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun roundedBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(24).toFloat()
        setColor(color)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PREFERENCES_NAME = "launcher_settings"
        const val GRID_ROWS_KEY = "screen_grid_rows"
        const val DEFAULT_GRID_ROWS = 7
        const val MIN_GRID_ROWS = 5
        const val MAX_GRID_ROWS = 7
    }
}
