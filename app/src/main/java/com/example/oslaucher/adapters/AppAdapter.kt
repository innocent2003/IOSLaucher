package com.example.oslaucher.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.oslaucher.R
import com.example.oslaucher.models.AppInfo

class AppAdapter(
    context: Context,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: ((AppInfo, View) -> Unit)? = null,
    private val gridRows: Int = 7
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private val iconMaxSize = (58 * context.resources.displayMetrics.density).toInt()
    private var allApps: List<AppInfo> = emptyList()
    private var visibleApps: List<AppInfo> = emptyList()

    fun setApps(apps: List<AppInfo>) {
        allApps = apps
        visibleApps = apps
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        visibleApps = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }
        notifyDataSetChanged()
    }

    override fun getCount(): Int = visibleApps.size

    override fun getItem(position: Int): AppInfo = visibleApps[position]

    override fun getItemId(position: Int): Long = getItem(position).packageName.hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val itemView = convertView ?: inflater.inflate(R.layout.item_app, parent, false)
        val rowHeight = parent.height.takeIf { it > 0 }?.div(gridRows)
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
        }
        val app = getItem(position)
        itemView.findViewById<ImageView>(R.id.app_icon).setImageDrawable(app.icon)
        itemView.findViewById<TextView>(R.id.app_name).text = app.name
        itemView.contentDescription = app.name
        itemView.setOnClickListener { onAppClick(app) }
        itemView.setOnLongClickListener {
            onAppLongClick?.invoke(app, itemView)
            true
        }
        return itemView
    }
}