package com.example.oslaucher.ui.fragments

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import com.example.oslaucher.R
import com.example.oslaucher.models.AppInfo
import com.example.oslaucher.utils.AppIconUtils
import com.example.oslaucher.utils.IconMetrics
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Screen 1: native ad, a 2 x 4 card of shortcut apps, the calendar, clock and battery.
 * The built page is re-used across swipes (see HomeScreenFragment), so the ad and the app
 * card are only (re)loaded when missing or stale, never on every swipe.
 */
class HomeFragment : HomeScreenFragment() {
    override val layoutResId = R.layout.screen_home

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindShortcutApps(view)
        loadNativeAdIfNeeded(view)
    }

    // ---- Shortcut apps (the 2 x 4 card) ----

    private fun bindShortcutApps(root: View) {
        val grid = root.findViewById<GridLayout?>(R.id.home_shortcut_apps) ?: return
        if (isReusedView && grid.childCount > 0) return
        val actions = activity as? HomeScreenActions ?: return
        actions.loadHomeScreenApps { apps ->
            if (!isAdded || view?.findViewById<GridLayout>(R.id.home_shortcut_apps) !== grid) {
                return@loadHomeScreenApps
            }
            renderShortcutApps(grid, pickShortcutApps(apps), actions)
        }
    }

    private fun pickShortcutApps(apps: List<AppInfo>): List<AppInfo> {
        val byPackage = apps.associateBy { it.packageName }
        val picked = linkedMapOf<String, AppInfo>()
        PREFERRED_PACKAGES.forEach { candidates ->
            candidates.firstNotNullOfOrNull { byPackage[it] }
                ?.let { picked.putIfAbsent(it.packageName, it) }
        }
        apps.firstOrNull { it.packageName.contains("vnpay", ignoreCase = true) }
            ?.let { picked.putIfAbsent(it.packageName, it) }
        // Fill the rest of the card with other installed apps.
        apps.asSequence()
            .filter { it.packageName != requireContext().packageName }
            .forEach { if (picked.size < SHORTCUT_COUNT) picked.putIfAbsent(it.packageName, it) }
        return picked.values.take(SHORTCUT_COUNT)
    }

    private fun renderShortcutApps(
        grid: GridLayout,
        apps: List<AppInfo>,
        actions: HomeScreenActions
    ) {
        grid.removeAllViews()
        val context = grid.context
        val iconSize = IconMetrics.iconSizePx(context)
        val cellPadding = (6 * grid.resources.displayMetrics.density).toInt()
        apps.forEachIndexed { index, app ->
            val icon = ImageView(context).apply {
                setImageDrawable(AppIconUtils.getIcon(context, app))
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = app.name
                isClickable = true
                isFocusable = true
                // Capture the activity, not this fragment: the page outlives the fragment.
                setOnClickListener { actions.launchHomeScreenApp(app.packageName) }
            }
            grid.addView(
                icon,
                GridLayout.LayoutParams(
                    GridLayout.spec(index / 4),
                    GridLayout.spec(index % 4, 1f)
                ).apply {
                    width = 0
                    height = iconSize + cellPadding * 2
                    setMargins(0, cellPadding, 0, cellPadding)
                }
            )
            icon.setPadding(cellPadding, 0, cellPadding, 0)
        }
    }

    // ---- AdMob native ad ----

    private fun loadNativeAdIfNeeded(root: View) {
        val adView = root.findViewById<NativeAdView?>(R.id.home_ad_view) ?: return
        val loadedAt = adView.getTag(R.id.home_ad_view) as? Long
        if (loadedAt != null && SystemClock.elapsedRealtime() - loadedAt < AD_REFRESH_INTERVAL_MS) {
            return
        }
        if (adView.getTag(R.id.home_ad_content) == true) return // a request is in flight
        adView.setTag(R.id.home_ad_content, true)
        val appContext = requireContext().applicationContext
        AdLoader.Builder(appContext, getString(R.string.admob_native_home_unit_id))
            .forNativeAd { nativeAd ->
                adView.setTag(R.id.home_ad_content, false)
                (adView.getTag(R.id.home_ad_media) as? NativeAd)?.destroy()
                adView.setTag(R.id.home_ad_media, nativeAd)
                adView.setTag(R.id.home_ad_view, SystemClock.elapsedRealtime())
                populateNativeAd(adView, nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    adView.setTag(R.id.home_ad_content, false)
                    Log.w(TAG, "Native ad failed to load: ${error.message}")
                    // Keep a previously shown ad; hide the empty card otherwise.
                    if (adView.getTag(R.id.home_ad_media) == null) {
                        adView.visibility = View.GONE
                    }
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    private fun populateNativeAd(adView: NativeAdView, nativeAd: NativeAd) {
        val media = adView.findViewById<MediaView>(R.id.home_ad_media)
        val headline = adView.findViewById<TextView>(R.id.home_ad_headline)
        val rating = adView.findViewById<TextView>(R.id.home_ad_rating)
        val cta = adView.findViewById<Button>(R.id.home_ad_cta)

        // Clip the ad image to the rounded media background.
        media.clipToOutline = true
        adView.mediaView = media
        adView.headlineView = headline
        adView.starRatingView = rating
        adView.callToActionView = cta

        headline.text = nativeAd.headline
        nativeAd.mediaContent?.let { media.mediaContent = it }
        val stars = nativeAd.starRating
        rating.visibility = if (stars != null && stars > 0) View.VISIBLE else View.GONE
        // Use the view's resources, never the fragment's: the ad usually arrives a few seconds
        // later, when the user may already have swiped away and this fragment is detached
        // (fragment.resources then throws and crashed the launcher).
        val locale = adView.resources.configuration.locales[0]
        rating.text = stars?.let { String.format(locale, "%.1f ★", it) }
        cta.visibility = if (nativeAd.callToAction.isNullOrBlank()) View.INVISIBLE else View.VISIBLE
        cta.text = nativeAd.callToAction

        adView.setNativeAd(nativeAd)
        adView.findViewById<ViewGroup>(R.id.home_ad_content).visibility = View.VISIBLE
        adView.visibility = View.VISIBLE
    }

    private companion object {
        const val TAG = "HomeFragment"
        const val SHORTCUT_COUNT = 8
        const val AD_REFRESH_INTERVAL_MS = 60 * 60 * 1000L

        /** Preferred apps for the card, in order; the first installed package of each wins. */
        val PREFERRED_PACKAGES = listOf(
            listOf("com.facebook.katana", "com.facebook.lite"),
            listOf("com.zing.zalo"),
            listOf("com.ss.android.ugc.trill", "com.zhiliaoapp.musically"),
            listOf("com.facebook.orca", "com.facebook.mlite"),
            listOf("com.instagram.barcelona"),
            listOf("com.instagram.android"),
            listOf("com.example.oslaucher")
        )
    }
}
