package com.wcs.drawing.game.draw.with.music.`fun`

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.compose.runtime.*
import com.android.billingclient.api.Purchase
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal object BillingProof {
    fun validKey(key:String):Boolean=runCatching {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key)))
        true
    }.getOrDefault(false)
    fun verify(key:String,data:String,signature:String):Boolean=runCatching {
        if(key.isBlank() || data.isBlank() || signature.isBlank()) return@runCatching false
        val publicKey=KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key)))
        Signature.getInstance("SHA1withRSA").run {
            initVerify(publicKey);update(data.toByteArray(Charsets.UTF_8))
            verify(Base64.getDecoder().decode(signature))
        }
    }.getOrDefault(false)
}

internal const val SUBSCRIPTION_OFFLINE_LEASE_MS=3_600_000L
internal fun subscriptionLeaseValid(checkedAt:Long,now:Long)=checkedAt>=0 && now>=checkedAt && now-checkedAt<SUBSCRIPTION_OFFLINE_LEASE_MS

/** Shared observable policy. Banners never consult this entitlement. */
object PurchaseAccess {
    var active by mutableStateOf(false)
        private set
    var lifetime by mutableStateOf(false)
        private set
    var subscriptionActive by mutableStateOf(false)
        private set
    private var subscriptionCheckedAt=-1L
    private fun update() {active=lifetime || subscriptionActive}
    fun expireSubscriptionLease() {
        if(subscriptionActive && !subscriptionLeaseValid(subscriptionCheckedAt,SystemClock.elapsedRealtime())) {
            subscriptionActive=false;update()
        }
    }
    var checking by mutableStateOf(false)
        internal set
    var checkout by mutableStateOf(false)
        internal set
    val fullScreenAllowed:Boolean get()=!active && !checking && !checkout
    private var prefs:SharedPreferences?=null
    private var packageName=""
    fun initialize(context:Context) {
        if(prefs!=null) return
        packageName=context.packageName
        prefs=context.applicationContext.getSharedPreferences("draw_musical_purchase_proof_v1",Context.MODE_PRIVATE)
        val json=prefs!!.getString("signed_purchase",null)
        val signature=prefs!!.getString("signature",null)
        lifetime=if(json!=null && signature!=null) runCatching {
            val purchase=Purchase(json,signature)
            BillingConfig.PRODUCT_ID in purchase.products && verified(purchase)
        }.getOrDefault(false) else false
        update()
    }
    fun verified(purchase:Purchase):Boolean = purchase.purchaseState==Purchase.PurchaseState.PURCHASED &&
        purchase.packageName==packageName && purchase.products.any {
            it==BillingConfig.PRODUCT_ID || it==BillingConfig.SUBSCRIPTION_ID
        } &&
        purchase.purchaseToken.isNotBlank() &&
        BillingProof.verify(BillingConfig.PLAY_LICENSE_PUBLIC_KEY,purchase.originalJson,purchase.signature)

    // Subscription state comes from a fresh Play query/update, never from a lifetime cache.
    internal fun grant(purchase:Purchase) {
        if(!verified(purchase)) return
        if(BillingConfig.PRODUCT_ID in purchase.products) {
            prefs?.edit()?.putString("signed_purchase",purchase.originalJson)
                ?.putString("signature",purchase.signature)?.apply()
            lifetime=true
        } else {
            subscriptionCheckedAt=SystemClock.elapsedRealtime();subscriptionActive=true
        }
        update()
    }
    internal fun revoke(productType:String) {
        if(productType==com.android.billingclient.api.BillingClient.ProductType.INAPP) {
            prefs?.edit()?.remove("signed_purchase")?.remove("signature")?.apply()
            lifetime=false
        } else {subscriptionActive=false;subscriptionCheckedAt=-1L}
        update()
    }
}
