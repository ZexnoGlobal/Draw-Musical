package com.wcs.drawing.game.draw.with.music.`fun`

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** One launch opportunity: show within ten seconds or continue without a late ad. */
@Composable
fun CasAppOpenLaunchGate(ads: CasAdsController?): Boolean {
    var finished by remember { mutableStateOf(CasRuntime.appOpenLaunchConsumed) }
    LaunchedEffect(ads) {
        if (PurchaseAccess.active || CasRuntime.appOpenLaunchConsumed || ads == null || !CasAdConfig.ENABLED ||
            !CasAdConfig.APP_OPEN_ENABLED || CasRuntime.appOpenLaunchEligible != true) {
            CasRuntime.appOpenLaunchConsumed = true
            finished = true
        } else {
            // A monotonic, process-wide start prevents rotation or clock changes restarting the wait.
            if (CasRuntime.appOpenWaitStartedMs == 0L) {
                CasRuntime.appOpenWaitStartedMs = SystemClock.elapsedRealtime()
            }
            val deadline = CasRuntime.appOpenWaitStartedMs + CasAdConfig.APP_OPEN_WAIT_MS
            ads.initialize()
            var shown = false
            while (!finished && !PurchaseAccess.active && !CasRuntime.appOpenLaunchConsumed &&
                SystemClock.elapsedRealtime() < deadline) {
                if (CasRuntime.ready && PurchaseAccess.fullScreenAllowed) {
                    // One ad load attempt; checking readiness does not issue repeated loads.
                    ads.prepareAppOpen()
                    if (SystemClock.elapsedRealtime() < deadline &&
                        ads.tryShowAppOpen { finished = true }) {
                        shown = true
                        break
                    }
                }
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining > 0L) delay(minOf(100L, remaining))
            }
            if (!shown && !finished) {
                // Consume BEFORE releasing Home, so a late load can never show on the next page.
                CasRuntime.appOpenLaunchConsumed = true
                finished = true
            }
        }
    }
    if (!finished) Surface(Modifier.fillMaxSize(), color = Color(0xFFFFFEF8)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            DrawMusicalBrandIcon(Modifier.size(150.dp))
            Spacer(Modifier.height(24.dp))
            Text("Draw Musical", fontSize = 36.sp, color = Color(0xFF263F37))
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = Color(0xFF08BFB3))
            Text(uiText("Loading ad…"), Modifier.padding(top = 16.dp), color = Color(0xFF263F37))
        }
    }
    return finished
}
