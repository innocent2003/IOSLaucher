package com.example.oslaucher.ui

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oslaucher.R

class LanguageActivity : AppCompatActivity() {
    private val languages = listOf(
        Language("en", "English"),
        Language("vi", "Tiếng Việt"),
        Language("fr", "Français"),
        Language("id", "Bahasa Indonesia"),
        Language("th", "ไทย"),
        Language("ja", "日本語")
    )
    private lateinit var languageOptions: LinearLayout
    private var selectedLanguage = DEFAULT_LANGUAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_language)

        val root = findViewById<View>(R.id.language_root)
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

        val appLocales = AppCompatDelegate.getApplicationLocales()
        selectedLanguage = appLocales.get(0)?.language
            ?.takeIf { code -> languages.any { it.code == code } }
            ?: DEFAULT_LANGUAGE

        findViewById<View>(R.id.language_back).setOnClickListener { finish() }
        findViewById<View>(R.id.language_confirm).apply {
            background = circleBackground(ACCENT_COLOR)
            setOnClickListener {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(selectedLanguage)
                )
                setResult(RESULT_OK)
                finish()
            }
        }

        languageOptions = findViewById(R.id.language_options)
        languages.forEach { language ->
            languageOptions.addView(
                createLanguageRow(language),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(51)
                ).apply {
                    bottomMargin = dp(18)
                }
            )
        }
        updateSelection()
    }

    private fun createLanguageRow(language: Language): View {
        val row = FrameLayout(this).apply {
            background = roundedBackground(
                ContextCompat.getColor(this@LanguageActivity, R.color.settings_surface)
            )
            isClickable = true
            isFocusable = true
            contentDescription = language.label
            setOnClickListener {
                selectedLanguage = language.code
                updateSelection()
            }
        }
        val label = TextView(this).apply {
            text = language.label
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@LanguageActivity, R.color.settings_text_primary))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(48), 0)
        }
        val check = TextView(this).apply {
            tag = CHECK_TAG
            text = "✓"
            textSize = 21f
            setTextColor(ContextCompat.getColor(this@LanguageActivity, R.color.settings_text_primary))
            gravity = Gravity.CENTER
        }
        row.addView(
            label,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        row.addView(
            check,
            FrameLayout.LayoutParams(dp(44), FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.END
            }
        )
        return row
    }

    private fun updateSelection() {
        for (index in 0 until languageOptions.childCount) {
            val row = languageOptions.getChildAt(index) as FrameLayout
            val language = languages[index]
            val check = row.findViewWithTag<TextView>(CHECK_TAG)
            check.visibility = if (language.code == selectedLanguage) View.VISIBLE else View.INVISIBLE
            row.isSelected = language.code == selectedLanguage
        }
    }

    private fun roundedBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(16).toFloat()
        setColor(color)
    }

    private fun circleBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class Language(val code: String, val label: String)

    companion object {
        private const val DEFAULT_LANGUAGE = "vi"
        private const val CHECK_TAG = "language_check"
        private const val ACCENT_COLOR = 0xFF078BFF.toInt()
    }
}
