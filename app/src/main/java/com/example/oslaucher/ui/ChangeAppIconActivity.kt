package com.example.oslaucher.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.oslaucher.R
import com.example.oslaucher.models.AppInfo
import com.example.oslaucher.repositories.InstalledAppRepository
import com.example.oslaucher.utils.AppIconUtils
import java.util.concurrent.Executors

class ChangeAppIconActivity : AppCompatActivity() {
    private val appRepository by lazy { InstalledAppRepository(applicationContext) }
    private val appLoadExecutor = Executors.newSingleThreadExecutor()
    private var apps: List<AppInfo> = emptyList()
    private var selectedPackageName: String? = null
    private lateinit var appList: LinearLayout

    private val iconPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) applySelectedIcon(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_change_app_icon)

        val root = findViewById<View>(R.id.change_app_icon_root)
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

        appList = findViewById(R.id.change_app_icon_list)
        findViewById<View>(R.id.change_app_icon_back).setOnClickListener { finish() }
        loadApps()
    }

    override fun onDestroy() {
        appLoadExecutor.shutdown()
        super.onDestroy()
    }

    private fun loadApps() {
        appLoadExecutor.execute {
            val loadedApps = appRepository.loadInstalledApps()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                apps = loadedApps
                showApps()
            }
        }
    }

    private fun showApps() {
        appList.removeAllViews()
        if (apps.isEmpty()) {
            appList.addView(TextView(this).apply {
                text = "Không tìm thấy ứng dụng nào."
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(dp(16), dp(20), dp(16), dp(20))
            })
            return
        }

        apps.forEachIndexed { index, app ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4), dp(10), dp(4), dp(10))
            }
            val icon = ImageView(this).apply {
                setImageDrawable(AppIconUtils.getIcon(this@ChangeAppIconActivity, app))
                contentDescription = app.name
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))

            val name = TextView(this).apply {
                text = app.name
                setTextColor(Color.WHITE)
                textSize = 15f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            row.addView(
                name,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(14)
                    marginEnd = dp(8)
                }
            )

            val hasCustomIcon = AppIconUtils.hasCustomIcon(this, app.packageName)
            val action = TextView(this).apply {
                text = if (hasCustomIcon) "Đặt lại" else "Đổi"
                setTextColor(if (hasCustomIcon) 0xFFFF7777.toInt() else 0xFF168BFF.toInt())
                textSize = 14f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setPadding(dp(8), dp(10), dp(8), dp(10))
                setOnClickListener {
                    if (hasCustomIcon) {
                        AppIconUtils.clearCustomIcon(this@ChangeAppIconActivity, app.packageName)
                        showApps()
                    } else {
                        selectedPackageName = app.packageName
                        iconPicker.launch(arrayOf("image/*"))
                    }
                }
            }
            row.addView(action, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            appList.addView(row)

            if (index < apps.lastIndex) {
                appList.addView(View(this).apply {
                    setBackgroundColor(0xFF555555.toInt())
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1)
                ).apply { marginStart = dp(64) })
            }
        }
    }

    private fun applySelectedIcon(uri: Uri) {
        val packageName = selectedPackageName
        if (packageName == null) {
            Toast.makeText(this, "Chưa chọn ứng dụng.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (exception: SecurityException) {
            Toast.makeText(this, "Không thể lưu quyền truy cập ảnh đã chọn.", Toast.LENGTH_LONG).show()
            return
        }

        AppIconUtils.setCustomIcon(this, packageName, uri)
        selectedPackageName = null
        showApps()
        Toast.makeText(this, "Đã cập nhật biểu tượng ứng dụng.", Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
