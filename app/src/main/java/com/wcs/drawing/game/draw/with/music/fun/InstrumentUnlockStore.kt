package com.wcs.drawing.game.draw.with.music.`fun`

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale

internal const val INSTRUMENT_UNLOCK_DURATION_MS=3_600_000L
internal val FREE_INSTRUMENT_NAMES=setOf("piano","marimba","bells","synth","guitar","flute")
internal fun instrumentKey(name:String)=name.trim().lowercase(Locale.ROOT)

internal data class InstrumentAccessWindow(val grantedAt:Long,val expiresAt:Long) {
    fun active(now:Long)=now>=grantedAt && now<expiresAt
    companion object {
        fun earned(now:Long)=InstrumentAccessWindow(now,now+INSTRUMENT_UNLOCK_DURATION_MS)
    }
}

class InstrumentUnlockStore(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("draw_music_instrument_access",Context.MODE_PRIVATE)
    private val prefix=if(CasAdConfig.TEST_ADS) "test_" else ""
    private val stateKey=prefix+"instrument_windows_v1"
    private val receiptKey=prefix+"instrument_receipts_v1"
    private var windows by mutableStateOf(readWindows())
    private var clockMs by mutableStateOf(System.currentTimeMillis())
    companion object {private val lock=Any()}

    fun isFree(name:String)=instrumentKey(name) in FREE_INSTRUMENT_NAMES
    fun canUse(name:String):Boolean = PurchaseAccess.active || isFree(name) || windows[instrumentKey(name)]?.active(clockMs)==true
    fun canUseNow(name:String):Boolean = PurchaseAccess.active || isFree(name) || windows[instrumentKey(name)]?.active(System.currentTimeMillis())==true
    fun remainingMinutes(name:String):Long = windows[instrumentKey(name)]?.let {
        if(it.active(clockMs)) (it.expiresAt-clockMs+59_999L)/60_000L else 0L
    } ?: 0L

    private fun readWindows():Map<String,InstrumentAccessWindow> = runCatching {
        val json=JSONObject(prefs.getString(stateKey,"{}"))
        json.keys().asSequence().associateWith { id ->
            val entry=json.getJSONObject(id)
            InstrumentAccessWindow(entry.getLong("grantedAt"),entry.getLong("expiresAt"))
        }
    }.getOrDefault(emptyMap())

    private fun serialize(value:Map<String,InstrumentAccessWindow>):String {
        val json=JSONObject()
        value.forEach {(key,window)->json.put(key,JSONObject().put("grantedAt",window.grantedAt).put("expiresAt",window.expiresAt))}
        return json.toString()
    }

    fun refreshTime() {clockMs=System.currentTimeMillis()}
    fun observe():()->Unit {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,key ->
            if(key==stateKey) {windows=readWindows();refreshTime()}
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return {prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }

    /** Only the earned-reward callback may call this; one instrument per receipt. */
    fun grant(name:String,receipt:String)=synchronized(lock) {
        if(isFree(name)) return@synchronized
        val id=instrumentKey(name)
        val now=System.currentTimeMillis()
        val latest=readWindows()
        if(latest[id]?.active(now)==true) return@synchronized
        val receipts=prefs.getStringSet(receiptKey,emptySet())!!.toSet()
        if(receipt in receipts) return@synchronized
        val updated=latest+(id to InstrumentAccessWindow.earned(now))
        prefs.edit().putString(stateKey,serialize(updated)).putStringSet(receiptKey,receipts+receipt).apply()
        windows=updated;refreshTime()
    }
}

@Composable
fun rememberInstrumentUnlocks():InstrumentUnlockStore {
    val context=LocalContext.current
    val access=remember {InstrumentUnlockStore(context.applicationContext)}
    val lifecycleOwner=remember(context) {
        generateSequence(context) {(it as? ContextWrapper)?.baseContext}.filterIsInstance<LifecycleOwner>().firstOrNull()
    }
    DisposableEffect(access) {val stop=access.observe();onDispose {stop()}}
    LaunchedEffect(access,lifecycleOwner) {
        suspend fun tick() {while(true) {access.refreshTime();delay(1_000L)}}
        if(lifecycleOwner!=null) lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {tick()}
        else tick()
    }
    return access
}
