package com.example.oslaucher.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GDPR / EEA + UK consent for AdMob, using Google's User Messaging Platform (UMP).
 *
 * - On every launch the consent status is refreshed; users in regions that require consent
 *   (EEA, UK, Switzerland) see the consent form before any ad is requested.
 * - The Mobile Ads SDK is initialised and ads are requested only once
 *   [ConsentInformation.canRequestAds] is true (consent given, or not required where the
 *   user lives). Until then ad slots stay empty.
 * - [showPrivacyOptionsForm] lets users change or withdraw consent at any time (Settings →
 *   "Privacy options"), as GDPR requires.
 *
 * The form's text, vendors and purposes are configured in the AdMob console
 * (Privacy & messaging → GDPR); a GDPR message must be published there.
 */
object AdConsentManager {
    private const val TAG = "AdConsentManager"

    private val mobileAdsInitialised = AtomicBoolean(false)
    private val gatheringConsent = AtomicBoolean(false)
    private val pendingAdLoads = mutableListOf<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun consentInformation(context: Context): ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context.applicationContext)

    /** True when ads may be requested now (consent obtained or not required). */
    fun canRequestAds(context: Context): Boolean = consentInformation(context).canRequestAds()

    /** True when the user must be offered a way to change their consent choice. */
    fun isPrivacyOptionsRequired(context: Context): Boolean =
        consentInformation(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Refreshes the consent status and shows the consent form when it is required. Call from
     * the launcher activity on every start. [onFinished] runs on the main thread afterwards.
     */
    fun gatherConsent(activity: Activity, onFinished: () -> Unit = {}) {
        val information = consentInformation(activity)
        // A previous session may already allow ads: start the SDK without waiting.
        if (information.canRequestAds()) initialiseAdsAndFlush(activity)
        if (!gatheringConsent.compareAndSet(false, true)) return

        val parameters = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        information.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    formError?.let { Log.w(TAG, "Consent form error ${it.errorCode}: ${it.message}") }
                    finish(activity, onFinished)
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update failed ${requestError.errorCode}: ${requestError.message}")
                finish(activity, onFinished)
            }
        )
    }

    /** Opens the form where the user can review, change or withdraw consent. */
    fun showPrivacyOptionsForm(activity: Activity, onDismissed: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            formError?.let { Log.w(TAG, "Privacy options form error ${it.errorCode}: ${it.message}") }
            if (canRequestAds(activity)) initialiseAdsAndFlush(activity)
            onDismissed()
        }
    }

    /**
     * Runs [load] now if ads may be requested, otherwise once consent allows it. Ad slots call
     * this instead of loading directly, so no ad request is ever made without consent.
     */
    fun runWhenAdsAllowed(context: Context, load: () -> Unit) {
        if (canRequestAds(context) && mobileAdsInitialised.get()) {
            load()
            return
        }
        synchronized(pendingAdLoads) { pendingAdLoads += load }
        if (canRequestAds(context)) initialiseAdsAndFlush(context)
    }

    private fun finish(activity: Activity, onFinished: () -> Unit) {
        gatheringConsent.set(false)
        if (canRequestAds(activity)) initialiseAdsAndFlush(activity)
        mainHandler.post(onFinished)
    }

    private fun initialiseAdsAndFlush(context: Context) {
        if (mobileAdsInitialised.compareAndSet(false, true)) {
            val appContext = context.applicationContext
            // Initialise off the main thread so it never delays a launcher frame.
            Thread {
                MobileAds.initialize(appContext) { mainHandler.post(::flushPendingLoads) }
            }.apply {
                name = "admob-init"
                start()
            }
        } else {
            mainHandler.post(::flushPendingLoads)
        }
    }

    private fun flushPendingLoads() {
        val loads = synchronized(pendingAdLoads) {
            pendingAdLoads.toList().also { pendingAdLoads.clear() }
        }
        loads.forEach { it() }
    }
}
