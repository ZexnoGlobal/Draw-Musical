package com.wcs.drawing.game.draw.with.music.`fun`

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

internal const val PORTRAIT_UNLOCK_DURATION_MS = 86_400_000L

/** Plain time policy; partial credits have no expiry until the final ad is earned. */
internal data class PortraitRewardState(
    val watched: Int = 0,
    val unlockedAtMs: Long = 0L,
    val expiresAtMs: Long = 0L
) {
    fun isUnlocked(now: Long) = unlockedAtMs > 0L && now >= unlockedAtMs && now < expiresAtMs
    fun effectiveWatched(required: Int, now: Long): Int = when {
        required <= 0 -> 0
        expiresAtMs > 0L -> if (isUnlocked(now)) required else 0
        else -> watched.coerceIn(0, required - 1)
    }
    fun earned(required: Int, now: Long): PortraitRewardState {
        if (required <= 0 || isUnlocked(now)) return this
        val count = effectiveWatched(required, now) + 1
        return if (count >= required) PortraitRewardState(required, now, now + PORTRAIT_UNLOCK_DURATION_MS)
            else PortraitRewardState(count)
    }
}

data class PortraitAdProgress(val id: String, val requiredAds: Int, val watched: Int) {
    val remainingAds: Int get() = (requiredAds - watched).coerceAtLeast(0)
}

class PortraitUnlockStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "draw_music_portrait_access", Context.MODE_PRIVATE)
    private val prefix = if (CasAdConfig.TEST_ADS) "test_" else ""
    private val orderKey = prefix + "grouped_order_v2"
    private val stateKey = prefix + "portrait_timed_state_v4"
    private val migrationKey = prefix + "portrait_timed_migrated_v4"
    private val receiptsKey = prefix + "portrait_receipts_v3"
    private var order by mutableStateOf(readOrder())
    private var states by mutableStateOf(readStates())
    private var nowMs by mutableStateOf(System.currentTimeMillis())
    companion object { private val lock = Any() }

    private fun readOrder(): List<String> = runCatching {
        val array = JSONArray(prefs.getString(orderKey, "[]"))
        (0 until array.length()).map { array.getString(it) }.distinct()
    }.getOrDefault(emptyList())

    private fun readStates(): Map<String, PortraitRewardState> = runCatching {
        val json = JSONObject(prefs.getString(stateKey, "{}"))
        json.keys().asSequence().associateWith { id ->
            val entry = json.getJSONObject(id)
            PortraitRewardState(entry.optInt("watched").coerceAtLeast(0),
                entry.optLong("unlockedAt"), entry.optLong("expiresAt"))
        }
    }.getOrDefault(emptyMap())

    private fun serialize(value: Map<String, PortraitRewardState>): String {
        val json = JSONObject()
        value.forEach { (id, state) -> json.put(id, JSONObject()
            .put("watched", state.watched).put("unlockedAt", state.unlockedAtMs)
            .put("expiresAt", state.expiresAtMs)) }
        return json.toString()
    }

    private fun refresh() {
        order = readOrder()
        states = readStates()
        refreshTime()
    }

    fun refreshTime() { nowMs = System.currentTimeMillis() }

    fun configureCatalog(ids: List<String>) = synchronized(lock) {
        val previous = readOrder()
        val merged = previous + ids.distinct().filterNot { it in previous }
        if (merged != previous) prefs.edit().putString(orderKey, JSONArray(merged).toString()).apply()
        order = merged
        migrateIndividualUnlocksOnce()
        refresh()
    }

    private fun migrateIndividualUnlocksOnce() {
        if (order.isEmpty() || prefs.getBoolean(migrationKey, false)) return
        val old = runCatching { JSONObject(prefs.getString(prefix + "portrait_progress_v3", "{}")) }
            .getOrDefault(JSONObject())
        val legacy = prefs.getStringSet(prefix + "unlocked_ids", emptySet())!!.toSet()
        val now = System.currentTimeMillis()
        val updated = readStates().toMutableMap()
        order.forEachIndexed { index, id ->
            val required = index / 4
            if (required > 0 && id !in updated) {
                val count = old.optInt(id).coerceIn(0, required)
                if (id in legacy || count >= required) {
                    // One-time 24-hour window for an existing individual earned unlock.
                    updated[id] = PortraitRewardState(required, now, now + PORTRAIT_UNLOCK_DURATION_MS)
                } else if (count > 0) updated[id] = PortraitRewardState(count)
            }
        }
        prefs.edit().putString(stateKey, serialize(updated)).putBoolean(migrationKey, true).apply()
    }

    fun progressFor(id: String): PortraitAdProgress? {
        val position = order.indexOf(id)
        if (position < 0) return null
        val required = position / 4
        return PortraitAdProgress(id, required,
            (states[id] ?: PortraitRewardState()).effectiveWatched(required, nowMs))
    }

    fun canOpen(id: String): Boolean {
        // progressFor also observes the clock tick so visible cards relock automatically.
        val progress = progressFor(id) ?: return false
        return PurchaseAccess.active || progress.requiredAds == 0 || states[id]?.isUnlocked(System.currentTimeMillis()) == true
    }

    fun observe(): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == orderKey || changed == stateKey) refresh()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun recordReward(portraitId: String, receipt: String) = synchronized(lock) {
        refresh()
        val portrait = progressFor(portraitId) ?: return@synchronized
        if (portrait.requiredAds == 0 || canOpen(portraitId)) return@synchronized
        val receipts = prefs.getStringSet(receiptsKey, emptySet())!!.toSet()
        if (receipt in receipts) return@synchronized
        val state = (states[portraitId] ?: PortraitRewardState())
            .earned(portrait.requiredAds, System.currentTimeMillis())
        val updated = readStates() + (portraitId to state)
        prefs.edit().putString(stateKey, serialize(updated))
            .putStringSet(receiptsKey, receipts + receipt).apply()
        states = updated
        refreshTime()
    }
}
