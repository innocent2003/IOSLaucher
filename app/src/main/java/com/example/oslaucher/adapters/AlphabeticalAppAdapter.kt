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
import com.example.oslaucher.utils.AppIconUtils
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

class AlphabeticalAppAdapter(
    context: Context,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: ((AppInfo, View) -> Unit)? = null
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private val collator = Collator.getInstance(Locale.getDefault())
    private var allApps: List<AppInfo> = emptyList()
    private var visibleApps: List<AppInfo> = emptyList()

    fun setApps(apps: List<AppInfo>) {
        allApps = apps.sortedWith { first, second -> collator.compare(first.name, second.name) }
        filter("")
    }

    fun filter(query: String) {
        visibleApps = allApps.filter {
            it.name.contains(query.trim(), ignoreCase = true)
        }
        notifyDataSetChanged()
    }

    fun positionFor(letter: Char): Int {
        val target = letter.uppercaseChar()
        val exactPosition = visibleApps.indexOfFirst { sectionFor(it.name) == target }
        if (exactPosition >= 0) return exactPosition

        val nextPosition = visibleApps.indexOfFirst { sectionFor(it.name) > target }
        if (nextPosition >= 0) return nextPosition
        return visibleApps.indexOfLast { sectionFor(it.name) < target }
    }

    override fun getCount(): Int = visibleApps.size

    override fun getItem(position: Int): AppInfo = visibleApps[position]

    override fun getItemId(position: Int): Long = getItem(position).packageName.hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView ?: inflater.inflate(R.layout.item_app_library, parent, false)
        val app = getItem(position)
        val section = row.findViewById<TextView>(R.id.app_library_section)
        val startsSection = position == 0 ||
            sectionFor(getItem(position - 1).name) != sectionFor(app.name)
        section.visibility = if (startsSection) View.VISIBLE else View.GONE
        if (startsSection) section.text = sectionFor(app.name).toString()

        row.findViewById<ImageView>(R.id.app_library_icon)
            .setImageDrawable(AppIconUtils.getIcon(inflater.context, app))
        row.findViewById<TextView>(R.id.app_library_name).text = app.name
        row.findViewById<View>(R.id.app_library_row).apply {
            setOnClickListener { onAppClick(app) }
            setOnLongClickListener {
                onAppLongClick?.invoke(app, this)
                onAppLongClick != null
            }
        }
        return row
    }

    private fun sectionFor(name: String): Char {
        val normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .uppercase(Locale.ROOT)
        return normalized.firstOrNull()?.takeIf { it in 'A'..'Z' } ?: '#'
    }
}
