package com.example.oslaucher.adapters

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.oslaucher.R
import com.example.oslaucher.models.AppInfo
import com.example.oslaucher.utils.AppIconUtils

class AppAdapter(
    context: Context,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: ((AppInfo, View) -> Unit)? = null,
    private val gridRows: Int = 7,
    private val selectedPackages: Set<String> = emptySet(),
    private val isSelectionMode: () -> Boolean = { false },
    private val canSelectApp: (AppInfo) -> Boolean = { true },
    private val onEmptySlotClick: (() -> Unit)? = null,
    private val selectionBarHeight: Int = 0
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private val iconMaxSize = (58 * context.resources.displayMetrics.density).toInt()
    private var allApps: List<AppInfo?> = emptyList()
    private var visibleApps: List<AppInfo?> = emptyList()

    fun setApps(apps: List<AppInfo>) {
        allApps = apps
        visibleApps = apps
        notifyDataSetChanged()
    }

    fun setGridApps(apps: List<AppInfo?>) {
        allApps = apps
        visibleApps = apps
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        visibleApps = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter { it?.name?.contains(query.trim(), ignoreCase = true) == true }
        }
        notifyDataSetChanged()
    }

    override fun getCount(): Int = visibleApps.size

    override fun getItem(position: Int): AppInfo? = visibleApps[position]

    override fun getItemId(position: Int): Long =
        getItem(position)?.packageName?.hashCode()?.toLong() ?: Long.MIN_VALUE + position

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val itemView = convertView ?: inflater.inflate(R.layout.item_app, parent, false)
        val rowHeight = parent.height.takeIf { it > 0 }
            ?.plus(if (isSelectionMode()) selectionBarHeight else 0)
            ?.div(gridRows)
        if (rowHeight != null) {
            itemView.layoutParams = (itemView.layoutParams
                ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight)).apply {
                height = rowHeight
            }
            val iconSize = minOf(iconMaxSize, (rowHeight * 0.68f).toInt())
            val icon = itemView.findViewById<ImageView>(R.id.app_icon)
            icon.layoutParams = icon.layoutParams.apply {
                width = iconSize
                height = iconSize
            }
            val iconFrame = itemView.findViewById<View>(R.id.app_icon_frame)
            iconFrame.layoutParams = iconFrame.layoutParams?.apply {
                width = iconSize
                height = iconSize
            }
        }
        val app = getItem(position)
        val icon = itemView.findViewById<ImageView>(R.id.app_icon)
        val name = itemView.findViewById<TextView>(R.id.app_name)
        val hoverGlow = itemView.findViewById<View>(R.id.app_hover_glow)
        val iconFrame = itemView.findViewById<View>(R.id.app_icon_frame)
        hoverGlow.visibility = View.GONE
        iconFrame.scaleX = 1f
        iconFrame.scaleY = 1f
        if (app == null) {
            icon.setImageDrawable(null)
            name.text = ""
            itemView.contentDescription = null
            itemView.findViewById<TextView>(R.id.app_selection_badge).visibility = View.GONE
            itemView.setOnClickListener { onEmptySlotClick?.invoke() }
            itemView.setOnLongClickListener(null)
            itemView.isClickable = onEmptySlotClick != null
            itemView.isLongClickable = false
            return itemView
        }
        icon.setImageDrawable(AppIconUtils.getIcon(inflater.context, app))
        name.text = app.name
        itemView.contentDescription = app.name
        val canSelect = canSelectApp(app)
        val selectionBadge = itemView.findViewById<TextView>(R.id.app_selection_badge)
        if (isSelectionMode() && canSelect) {
            val selected = app.packageName in selectedPackages
            selectionBadge.visibility = View.VISIBLE
            selectionBadge.text = if (selected) "✓" else ""
            selectionBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (selected) 0xFF1685E8.toInt() else 0x66505050)
                setStroke((1.5f * inflater.context.resources.displayMetrics.density).toInt(), Color.WHITE)
            }
        } else {
            selectionBadge.visibility = View.GONE
        }
        itemView.isClickable = true
        itemView.isLongClickable = true
        itemView.setOnClickListener { onAppClick(app) }
        itemView.setOnLongClickListener {
            onAppLongClick?.invoke(app, itemView)
            true
        }
        return itemView
    }
}
