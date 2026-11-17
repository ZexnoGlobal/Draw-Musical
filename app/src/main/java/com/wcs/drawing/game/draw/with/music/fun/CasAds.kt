package com.wcs.drawing.game.draw.with.music.`fun`

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import com.cleveradssolutions.sdk.AdContentInfo
import com.cleveradssolutions.sdk.AdFormat
import com.cleveradssolutions.sdk.screen.CASAppOpen
import com.cleveradssolutions.sdk.screen.CASInterstitial
import com.cleveradssolutions.sdk.screen.CASRewarded
import com.cleveradssolutions.sdk.screen.OnRewardEarnedListener
import com.cleveradssolutions.sdk.screen.ScreenAdContentCallback
import com.cleversolutions.ads.AdError
import com.cleversolutions.ads.AdSize
import com.cleversolutions.ads.ConsentFlow
import com.cleversolutions.ads.android.CAS
import com.cleversolutions.ads.android.CASBannerView

val LocalCasAds = staticCompositionLocalOf<CasAdsController?> { null }

enum class PortraitRewardResult { EARNED, CLOSED_WITHOUT_REWARD, FAILED }

private class PortraitRewardRequest(
    val grant: () -> Unit,
    var finished: ((PortraitRewardResult) -> Unit)?,
    var earned: Boolean = false
)

/** Process state: no Activity retained by the initialization callback. */
object CasRuntime {
    var appOpenLaunchEligible: Boolean? = null
    var appOpenLaunchConsumed = false
    var appOpenWaitStartedMs = 0L
    var ready by mutableStateOf(false)
        private set
    var initializationError by mutableStateOf<String?>(null)
        private set
    private var started = false
    private val main = Handler(Looper.getMainLooper())
    private var lastExitRequestId: String? = null
    private var lastExportRequestId: String? = null
    private var lastLoopRequestId:String?=null
    fun claimLoopRequest(requestId:String):Boolean {
        if(lastLoopRequestId==requestId) return false
        lastLoopRequestId=requestId
        return true
    }

    fun claimExportRequest(requestId:String):Boolean {
        if(lastExportRequestId==requestId) return false
        lastExportRequestId=requestId
        return true
    }

    fun claimExitRequest(requestId:String):Boolean {
        if(lastExitRequestId==requestId) return false
        lastExitRequestId=requestId
        return true
    }

    fun initialize(activity: ComponentActivity) {
        if (!CasAdConfig.ENABLED || started) return
        started = true
        initializationError = null
        runCatching {
            CAS.buildManager()
                .withCasId(CasAdConfig.CAS_ID)
                .withTestAdMode(CasAdConfig.TEST_ADS)
                // CAS's automatic consent flow remains enabled.
                .withCompletionListener { config ->
                    main.post {
                        ready = config.error == null
                        initializationError = config.error
                        if (config.error != null) Log.w("DrawMusicAds", "CAS initialization: ${config.error}")
                    }
                }
                .build(activity)
        }.onFailure {
            started = false
            initializationError = "CAS initialization failed"
            Log.w("DrawMusicAds", "CAS unavailable", it)
        }
    }

    fun retryInitialization(activity: ComponentActivity) {
        initializationError = null
        // CAS retries async initialization failures itself; do not build a second manager.
        initialize(activity)
    }


}

class CasAdsController(private val activity: ComponentActivity) {
    private var interstitial: CASInterstitial? = null
    private var interstitialFinished: (() -> Unit)? = null
    private var appOpen: CASAppOpen? = null
    private var appOpenPreparationAttempted=false
    var appOpenLoadError by mutableStateOf<String?>(null)
        private set
    private var appOpenFinished: (() -> Unit)? = null
    private var showing = false
    private var destroyed = false
    private var rewarded: CASRewarded? = null
    private var rewardRequest: PortraitRewardRequest? = null
    var rewardReady by mutableStateOf(false)
        private set
    var rewardBusy by mutableStateOf(false)
        private set
    var rewardStatus by mutableStateOf("Loading ad…")
        private set

    fun initialize() = CasRuntime.initialize(activity)

    fun configureAppOpenLaunch(eligible: Boolean) {
        if(CasRuntime.appOpenLaunchEligible==null) CasRuntime.appOpenLaunchEligible=eligible
    }

    fun prepareAppOpen() {
        if(!PurchaseAccess.fullScreenAllowed) return
        if(destroyed || !CasAdConfig.APP_OPEN_ENABLED || !CasRuntime.ready || appOpenPreparationAttempted) return
        appOpenPreparationAttempted=true
        appOpenLoadError=null
        runCatching {
            appOpen=CASAppOpen(CasAdConfig.CAS_ID).apply {
                placement="cold_start"
                isAutoloadEnabled=false
                isAutoshowEnabled=false
                contentCallback=object:ScreenAdContentCallback() {
                    override fun onAdLoaded(ad:AdContentInfo) { if(!destroyed) appOpenLoadError=null }
                    override fun onAdFailedToLoad(format:AdFormat,error:AdError) {
                        if(!destroyed) appOpenLoadError="App Open ad failed to load"
                        Log.w("DrawMusicAds","App Open load failed: ${error.message}")
                    }
                    override fun onAdDismissed(ad:AdContentInfo) {finishAppOpen()}
                    override fun onAdFailedToShow(format:AdFormat,error:AdError) {finishAppOpen()}
                }
                load(activity)
            }
        }.onFailure {
            appOpenLoadError="App Open ad unavailable"
            Log.w("DrawMusicAds","App Open unavailable",it)
        }
    }

    fun retryAppOpenLoad() {
        if(destroyed || showing || CasRuntime.appOpenLaunchConsumed) return
        appOpen?.destroy()
        appOpen=null
        appOpenPreparationAttempted=false
        appOpenLoadError=null
        CasRuntime.retryInitialization(activity)
    }

    fun tryShowAppOpen(finished:()->Unit):Boolean {
        if(!PurchaseAccess.fullScreenAllowed) return false
        val ad=appOpen ?: return false
        if(destroyed || showing || rewardBusy || !CasRuntime.ready || !ad.isLoaded ||
            CasRuntime.appOpenLaunchConsumed || activity.isFinishing || activity.isDestroyed ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        CasRuntime.appOpenLaunchConsumed=true
        showing=true
        appOpenFinished=finished
        runCatching {ad.show(activity)}.onFailure {
            Log.w("DrawMusicAds","App Open skipped",it);finishAppOpen()
        }
        return true
    }

    private fun finishAppOpen() {
        showing=false
        val finished=appOpenFinished
        appOpenFinished=null
        if(!destroyed) finished?.invoke()
    }

    fun prepareRewarded() {
        if(PurchaseAccess.active) return
        if (destroyed || !CasRuntime.ready || rewarded != null) return
        rewardStatus = "Loading ad…"
        runCatching {
            rewarded = CASRewarded(CasAdConfig.CAS_ID).apply {
                placement = "unlock_portrait"
                isAutoloadEnabled = true
                // Require the rewarded format; no automatic interstitial fallback.
                isExtraFillInterstitialAdEnabled = false
                contentCallback = object : ScreenAdContentCallback() {
                    override fun onAdLoaded(ad: AdContentInfo) {
                        if (!destroyed) { rewardReady = true; rewardStatus = "Ad ready" }
                    }
                    override fun onAdFailedToLoad(format: AdFormat, error: AdError) {
                        if (!destroyed) {
                            rewardReady = false
                            rewardStatus = "Ad unavailable. Please try again later."
                        }
                    }
                    override fun onAdDismissed(ad: AdContentInfo) {
                        finishReward(PortraitRewardResult.CLOSED_WITHOUT_REWARD)
                    }
                    override fun onAdFailedToShow(format: AdFormat, error: AdError) {
                        finishReward(PortraitRewardResult.FAILED)
                    }
                }
                load(activity)
            }
        }.onFailure {
            rewardReady = false
            rewardStatus = "Ad unavailable. Please try again later."
            Log.w("DrawMusicAds", "Rewarded ad unavailable", it)
        }
    }

    /** Returns false without showing or granting if the ad/Activity is unavailable. */
    fun showPortraitReward(grant: () -> Unit, finished: (PortraitRewardResult) -> Unit, placement:String="unlock_portrait"): Boolean {
        if(!PurchaseAccess.fullScreenAllowed) return false
        val ad = rewarded ?: return false
        if (destroyed || showing || rewardBusy || !CasRuntime.ready || !ad.isLoaded ||
            activity.isFinishing || activity.isDestroyed ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
        ad.placement=placement
        val request = PortraitRewardRequest(grant, finished)
        rewardRequest = request
        rewardReady = false
        rewardBusy = true
        showing = true
        runCatching {
            ad.show(activity, object : OnRewardEarnedListener {
                override fun onUserEarnedReward(ad: AdContentInfo) {
                    // Persist immediately, even if the old Activity was recreated.
                    // The grant closure captures only the app-context store and this ID.
                    if (!request.earned) {
                        request.earned = true
                        request.grant()
                    }
                }
            })
        }.onFailure {
            Log.w("DrawMusicAds", "Rewarded ad show failed", it)
            finishReward(PortraitRewardResult.FAILED)
        }
        return true
    }

    fun showRootNoteReward(grant:()->Unit,finished:(PortraitRewardResult)->Unit):Boolean =
        showPortraitReward(grant,finished,placement="unlock_key")

    fun showInstrumentReward(grant:()->Unit,finished:(PortraitRewardResult)->Unit):Boolean =
        showPortraitReward(grant,finished,placement="unlock_instrument")

    private fun finishReward(fallback: PortraitRewardResult) {
        val request = rewardRequest ?: return
        rewardRequest = null
        showing = false
        rewardBusy = false
        rewardReady = rewarded?.isLoaded == true
        rewardStatus = if (rewardReady) "Ad ready" else "Loading ad…"
        val callback = request.finished
        request.finished = null
        if (!destroyed) callback?.invoke(if (request.earned) PortraitRewardResult.EARNED else fallback)
    }

    fun prepareInterstitial() {
        if (destroyed || !CasRuntime.ready || interstitial != null) return
        runCatching {
            interstitial = CASInterstitial(CasAdConfig.CAS_ID).apply {
                placement = "editor_to_home"
                isAutoloadEnabled = true
                isAutoshowEnabled = false
                contentCallback = object : ScreenAdContentCallback() {
                    override fun onAdShowed(ad: AdContentInfo) { showing = true }
                    override fun onAdDismissed(ad: AdContentInfo) { finishInterstitial() }
                    override fun onAdFailedToShow(format: AdFormat, error: AdError) {
                        Log.w("DrawMusicAds", "Interstitial skipped: ${error.message}")
                        finishInterstitial()
                    }
                }
                load(activity)
            }
        }.onFailure { Log.w("DrawMusicAds", "Interstitial unavailable", it) }
    }

    // The editor has already saved and stopped audio. Home stays hidden until this finishes.
    fun showEditorExitInterstitial(requestId:String,onFinished:()->Unit) {
        if(!CasRuntime.claimExitRequest(requestId)) {onFinished();return}
        showReadyInterstitial("editor_to_home",onFinished)
    }

    // One opportunity immediately after a successfully saved export; never autoshow later.
    fun showExportInterstitial(requestId:String,onFinished:()->Unit) {
        if(!CasRuntime.claimExportRequest(requestId)) {onFinished();return}
        showReadyInterstitial("export_complete",onFinished)
    }

    fun showLoopInterstitial(requestId:String,onFinished:()->Unit) {
        if(!CasRuntime.claimLoopRequest(requestId)) {onFinished();return}
        showReadyInterstitial("five_music_loops",onFinished)
    }

    private fun showReadyInterstitial(placement:String,onFinished:()->Unit) {
        if(!PurchaseAccess.fullScreenAllowed) {onFinished();return}
        val ad=interstitial
        val canShow=!destroyed && !showing && !rewardBusy && CasRuntime.ready && ad?.isLoaded==true &&
            !activity.isFinishing && !activity.isDestroyed &&
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if(!canShow) {onFinished();return}
        interstitialFinished=onFinished
        showing=true
        runCatching {ad?.placement=placement;ad?.show(activity)}.onFailure {
            Log.w("DrawMusicAds","Interstitial skipped",it)
            finishInterstitial()
        }
    }

    private fun finishInterstitial() {
        val finished=interstitialFinished ?: return
        interstitialFinished=null
        showing=false
        if(!destroyed) finished()
    }

    fun showPrivacyOptions() {
        if (destroyed || !CasRuntime.ready || showing) return
        runCatching { ConsentFlow().withUIContext(activity).show() }
            .onFailure { Log.w("DrawMusicAds", "Consent form unavailable", it) }
    }

    fun destroy() {
        destroyed = true
        interstitialFinished=null
        appOpenFinished=null
        appOpen?.destroy()
        appOpen=null
        rewardRequest?.finished = null
        rewardRequest = null
        rewarded?.destroy()
        rewarded = null
        interstitial?.destroy()
        interstitial = null
    }
}

/** Reserved space below controls; never overlays the canvas or navigation. */
@Composable
fun CasBanner(placement: String) {
    if (!CasAdConfig.ENABLED || !CasRuntime.ready || LocalInspectionMode.current) return
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center) {
        // Standard banners need 320 dp; omit on smaller split-screen windows.
        if (maxWidth >= 320.dp) {
            val view = remember(context, placement) {
                CASBannerView(context, CasAdConfig.CAS_ID).apply {
                    size = AdSize.BANNER
                    this.placement = placement
                    isAutoloadEnabled = true
                }
            }
            DisposableEffect(view) {
                view.load()
                onDispose { view.destroy() }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(uiText("Advertisement"), fontSize = 9.sp)
                AndroidView(factory = { view }, modifier = Modifier.width(320.dp).height(50.dp))
            }
        }
    }
}
