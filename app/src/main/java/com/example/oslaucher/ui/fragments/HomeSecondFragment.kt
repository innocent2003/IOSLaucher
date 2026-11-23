package com.example.oslaucher.ui.fragments

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.os.SystemClock
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.oslaucher.R
import com.example.oslaucher.models.AppInfo
import com.example.oslaucher.utils.AppIconUtils
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

class HomeSecondFragment : HomeScreenFragment() {
    override val layoutResId = R.layout.screen_home_second

    private val folderCategories = listOf(
        FolderCategory(R.string.category_other, emptyList()),
        FolderCategory(R.string.category_social, listOf(
            "facebook", "instagram", "messenger", "zalo", "telegram", "whatsapp",
            "discord", "wechat", "line", "snapchat", "tiktok", "social"
        )),
        FolderCategory(R.string.category_productivity, listOf(
            "office", "word", "excel", "powerpoint", "drive", "docs", "sheets",
            "calendar", "todo", "task", "authenticator", "scanner", "productivity"
        )),
        FolderCategory(R.string.category_photos, listOf(
            "photo", "gallery", "camera", "image", "picsart", "lightroom", "snapseed"
        )),
        FolderCategory(R.string.category_video, listOf(
            "youtube", "netflix", "video", "player", "capcut", "vlc", "movie", "stream"
        )),
        FolderCategory(R.string.category_news, listOf(
            "news", "newspaper", "magazine", "vnexpress", "dantri", "bbc", "cnn"
        )),
        FolderCategory(R.string.category_music, listOf(
            "music", "spotify", "soundcloud", "audio", "podcast", "mp3", "youtube music"
        )),
        FolderCategory(R.string.category_games, listOf(
            "game", "play games", "pubg", "mobile legends", "roblox", "minecraft"
        ))
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.home_app_library).setOnClickListener {
            (activity as? HomeScreenActions)?.openApps()
        }

        val folders = view.findViewById<GridLayout>(R.id.home_app_folders)
        folders.visibility = View.VISIBLE
        // A re-used page already shows its folders; rebuilding them (and reading every app's
        // install time) on each swipe was what made swiping into this page stutter.
        loadLibraryAdIfNeeded(view)
        if (isReusedView && folders.childCount > 0) return
        (activity as? HomeScreenActions)?.loadHomeScreenApps { apps ->
            if (isAdded && this.view?.findViewById<GridLayout>(R.id.home_app_folders) === folders) {
                renderFolders(folders, apps)
            }
        }
    }

    // ---- AdMob native ad between the folder rows ----

    /** Puts the ad block into the folder grid as row 1, spanning both columns. */
    private fun placeLibraryAd(folders: GridLayout) {
        val root = view ?: return
        val block = adBlock ?: root.findViewById<View?>(R.id.home_library_ad)?.also {
            adBlock = it
        } ?: return
        (block.parent as? ViewGroup)?.removeView(block)
        folders.addView(
            block,
            GridLayout.LayoutParams(GridLayout.spec(1), GridLayout.spec(0, 2, 1f)).apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(0, 0, 0, dp(16))
            }
        )
    }

    /** The ad block, remembered once it has been moved out of its XML parent. */
    private var adBlock: View? = null

    private fun loadLibraryAdIfNeeded(root: View) {
        val adView = root.findViewById<NativeAdView?>(R.id.home_library_ad_view) ?: return
        val loadedAt = adView.getTag(R.id.home_library_ad_view) as? Long
        if (loadedAt != null && SystemClock.elapsedRealtime() - loadedAt < AD_REFRESH_INTERVAL_MS) {
            return
        }
        if (adView.getTag(R.id.home_library_ad_icon) == true) return // request in flight
        adView.setTag(R.id.home_library_ad_icon, true)
        AdLoader.Builder(
            requireContext().applicationContext,
            getString(R.string.admob_native_library_unit_id)
        )
            .forNativeAd { nativeAd ->
                adView.setTag(R.id.home_library_ad_icon, false)
                (adView.getTag(R.id.home_library_ad_media) as? NativeAd)?.destroy()
                adView.setTag(R.id.home_library_ad_media, nativeAd)
                adView.setTag(R.id.home_library_ad_view, SystemClock.elapsedRealtime())
                populateLibraryAd(adView, nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    adView.setTag(R.id.home_library_ad_icon, false)
                    Log.w(TAG, "Library native ad failed to load: ${error.message}")
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    private fun populateLibraryAd(adView: NativeAdView, nativeAd: NativeAd) {
        val media = adView.findViewById<MediaView>(R.id.home_library_ad_media)
        val icon = adView.findViewById<ImageView>(R.id.home_library_ad_icon)
        val headline = adView.findViewById<TextView>(R.id.home_library_ad_headline)
        val body = adView.findViewById<TextView>(R.id.home_library_ad_body)
        val cta = adView.findViewById<Button>(R.id.home_library_ad_cta)

        media.clipToOutline = true
        icon.clipToOutline = true
        adView.mediaView = media
        adView.iconView = icon
        adView.headlineView = headline
        adView.bodyView = body
        adView.callToActionView = cta

        nativeAd.mediaContent?.let { media.mediaContent = it }
        val adIcon = nativeAd.icon?.drawable
        icon.visibility = if (adIcon != null) View.VISIBLE else View.GONE
        icon.setImageDrawable(adIcon)
        headline.text = nativeAd.headline
        body.text = nativeAd.body
        body.visibility = if (nativeAd.body.isNullOrBlank()) View.INVISIBLE else View.VISIBLE
        cta.text = nativeAd.callToAction
        cta.visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE

        adView.setNativeAd(nativeAd)
        // The ad block is the ad view's parent; do not go through the fragment, which may be
        // detached by the time the ad arrives.
        (adView.parent as? View)?.visibility = View.VISIBLE
    }

    private fun renderFolders(folders: GridLayout, apps: List<AppInfo>) {
        view?.findViewById<View?>(R.id.home_folders_loading)?.visibility = View.GONE
        // Capture the activity (not this fragment) in click handlers: the built page outlives
        // this fragment instance when it is re-used.
        val actions = activity as? HomeScreenActions
        folders.removeAllViews()
        if (apps.isEmpty()) {
            folders.rowCount = 1
            folders.addView(TextView(requireContext()).apply {
                text = getString(R.string.no_apps)
                gravity = Gravity.CENTER
                setTextColor(0xFFF3F7FB.toInt())
                textSize = 14f
            }, GridLayout.LayoutParams(
                GridLayout.spec(0),
                GridLayout.spec(0, 1f)
            ).apply {
                width = 0
                height = dp(72)
                columnSpec = GridLayout.spec(0, 2, 1f)
            })
            folders.visibility = View.VISIBLE
            return
        }
        val assignedApps = apps.groupBy(::categoryFor)
        val socialApps = assignedApps[getString(R.string.category_social)].orEmpty()
        val suggestedApps = socialApps.take(4).ifEmpty { apps.take(4) }
        val packageManager = requireContext().packageManager
        val recentApps = apps.map { app ->
            val installTime = try {
                packageManager.getPackageInfo(app.packageName, 0).firstInstallTime
            } catch (exception: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Could not read install time for ${app.packageName}", exception)
                0L
            }
            app to installTime
        }.sortedByDescending { it.second }
            .take(8)
            .map { it.first }
        val visibleCategories = listOf(
            getString(R.string.category_suggested) to suggestedApps,
            getString(R.string.category_recent) to recentApps
        ) + folderCategories.map { getString(it.titleResId) }
            .mapNotNull { category ->
                assignedApps[category].orEmpty().takeIf { it.isNotEmpty() }
                    ?.let { category to it }
            }
        val visibleFolders = visibleCategories.filter { it.second.isNotEmpty() }
        // One extra row for the ad, which sits right after the first row of folders.
        folders.rowCount = (visibleFolders.size + 1) / 2 + 1
        folders.visibility = View.VISIBLE
        visibleFolders.forEachIndexed { index, (category, categoryApps) ->

            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                contentDescription = getString(
                    R.string.folder_accessibility,
                    category,
                    categoryApps.size
                )
                setOnClickListener {
                    actions
                        ?.openHomeScreenFolder(category, categoryApps)
                }
            }
            val iconGrid = GridLayout(requireContext()).apply {
                columnCount = 2
                rowCount = 2
                background = requireContext().getDrawable(R.drawable.bg_glass)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener {
                    actions
                        ?.openHomeScreenFolder(category, categoryApps)
                }
            }
            categoryApps.take(4).forEachIndexed { appIndex, app ->
                iconGrid.addView(
                    createAppIcon(app, actions),
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
            card.addView(
                iconGrid,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(148)
                )
            )
            card.addView(TextView(requireContext()).apply {
                text = category
                gravity = Gravity.CENTER
                setTextColor(0xFFF3F7FB.toInt())
                textSize = 14f
                maxLines = 1
                setPadding(0, dp(6), 0, 0)
                setOnClickListener {
                    actions
                        ?.openHomeScreenFolder(category, categoryApps)
                }
            })

            val row = index / 2 + if (index >= 2) 1 else 0
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
        placeLibraryAd(folders)
    }

    private fun createAppIcon(app: AppInfo, actions: HomeScreenActions?): ImageView =
        ImageView(requireContext()).apply {
            setImageDrawable(AppIconUtils.getIcon(requireContext(), app))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = app.name
            isClickable = true
            isFocusable = true
            setOnClickListener {
                actions?.launchHomeScreenApp(app.packageName)
            }
        }

    private fun categoryFor(app: AppInfo): String {
        val searchable = "${app.name} ${app.packageName}".lowercase()
        return folderCategories.drop(1).firstOrNull { category ->
            category.keywords.any(searchable::contains)
        }?.let { getString(it.titleResId) } ?: getString(folderCategories.first().titleResId)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "HomeSecondFragment"
        private const val AD_REFRESH_INTERVAL_MS = 60 * 60 * 1000L
    }

    private data class FolderCategory(
        val titleResId: Int,
        val keywords: List<String>
    )
}
