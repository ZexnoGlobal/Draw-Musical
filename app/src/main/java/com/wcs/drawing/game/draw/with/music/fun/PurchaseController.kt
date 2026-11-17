package com.wcs.drawing.game.draw.with.music.`fun`

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.*
import com.android.billingclient.api.*

val LocalPurchases=staticCompositionLocalOf<PurchaseController?> {null}

class PurchaseController(context:Context) {
    private val main=Handler(Looper.getMainLooper())
    private var closed=false
    private var connecting=false
    private var foreground=false
    private var querySerial=0
    private var trackedSubscription=PurchaseAccess.subscriptionActive
    private val expectedTokens=mutableMapOf<String,String>()
    private val acknowledging=mutableSetOf<String>()
    private val pendingTypes=mutableSetOf<String>()
    private var onceProduct:ProductDetails?=null
    private var onceOffer:ProductDetails.OneTimePurchaseOfferDetails?=null
    private var subProduct:ProductDetails?=null
    private var subOffers=emptyMap<String,ProductDetails.SubscriptionOfferDetails>()
    var price by mutableStateOf<String?>(null)
        private set
    var weeklyPrice by mutableStateOf<String?>(null)
        private set
    var weeklyHasTrial by mutableStateOf(false)
        private set
    var monthlyPrice by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var pending by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    private var onceReady by mutableStateOf(false)
    private var subReady by mutableStateOf(false)
    private val mayBuy:Boolean get()=!busy && !pending && !PurchaseAccess.checking &&
        !PurchaseAccess.active && BillingProof.validKey(BillingConfig.PLAY_LICENSE_PUBLIC_KEY)
    val canBuy:Boolean get()=mayBuy && onceReady
    fun canSubscribe(plan:String)=mayBuy && subReady && plan in subOffers
    private fun dispatch(block:()->Unit) {
        if(Looper.myLooper()==Looper.getMainLooper()) {if(!closed) block()}
        else main.post {if(!closed) block()}
    }
    private val client=BillingClient.newBuilder(context.applicationContext)
        .setListener {result,purchases -> dispatch {
            PurchaseAccess.checkout=false;PurchaseAccess.checking=false;busy=false
            when(result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    querySerial++
                    purchases.orEmpty().groupBy {if(BillingConfig.SUBSCRIPTION_ID in it.products) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP}
                        .forEach {(type,list)->process(list,type,false)}
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> status="Purchase cancelled"
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh(true)
                else -> {status="Purchase unavailable. Please try again.";log(result)}
            }
        }}
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()
    private val poll=object:Runnable {
        override fun run() {
            if(closed || !foreground) return
            PurchaseAccess.expireSubscriptionLease()
            if(client.isReady && (trackedSubscription || pending)) queryOwned(false)
            main.postDelayed(this,60_000)
        }
    }
    init {PurchaseAccess.initialize(context)}
    fun onForeground() {
        foreground=true;main.removeCallbacks(poll);main.postDelayed(poll,60_000);refresh()
    }
    fun onBackground() {foreground=false;main.removeCallbacks(poll)}
    fun refresh(restore:Boolean=false) {
        if(closed) return
        PurchaseAccess.expireSubscriptionLease()
        if(!client.isReady) {
            if(connecting) return
            connecting=true;PurchaseAccess.checking=true
            main.postDelayed({if(!closed && connecting) {PurchaseAccess.checking=false;status="Store unavailable. Try again later."}},3_000)
            client.startConnection(object:BillingClientStateListener {
                override fun onBillingSetupFinished(result:BillingResult)=dispatch {
                    connecting=false
                    if(result.responseCode==BillingClient.BillingResponseCode.OK) {queryProducts();queryOwned(restore)}
                    else {PurchaseAccess.checking=false;status="Store unavailable. Try again later.";log(result)}
                }
                override fun onBillingServiceDisconnected()=dispatch {PurchaseAccess.checking=false}
            })
        } else {queryProducts();queryOwned(restore)}
    }
    private fun queryProducts() {
        listOf(BillingClient.ProductType.INAPP to BillingConfig.PRODUCT_ID,
            BillingClient.ProductType.SUBS to BillingConfig.SUBSCRIPTION_ID).forEach {(type,id)->
            val params=QueryProductDetailsParams.newBuilder().setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build())).build()
            client.queryProductDetailsAsync(params) {result,details -> dispatch {
                val detail=if(result.responseCode==BillingClient.BillingResponseCode.OK) details.productDetailsList.firstOrNull {it.productId==id} else null
                if(type==BillingClient.ProductType.INAPP) {
                    onceProduct=detail
                    onceOffer=detail?.oneTimePurchaseOfferDetailsList?.firstOrNull {
                        it.purchaseOptionId==BillingConfig.PURCHASE_OPTION_ID && it.rentalDetails==null && it.preorderDetails==null
                    }
                    price=onceOffer?.formattedPrice;onceReady=onceOffer!=null
                } else {
                    subProduct=detail
                    val eligible=detail?.subscriptionOfferDetails.orEmpty()
                    fun regular(plan:String,period:String)=eligible.firstOrNull {
                        val phase=it.pricingPhases.pricingPhaseList.singleOrNull() ?: return@firstOrNull false
                        it.basePlanId==plan && it.offerId==null && phase.billingPeriod==period &&
                            phase.recurrenceMode==ProductDetails.RecurrenceMode.INFINITE_RECURRING
                    }
                    val trial=eligible.firstOrNull {
                        val phases=it.pricingPhases.pricingPhaseList
                        it.basePlanId==BillingConfig.WEEKLY_BASE_PLAN && it.offerId==BillingConfig.WEEKLY_TRIAL_OFFER_ID &&
                            phases.size==2 && phases[0].priceAmountMicros==0L && phases[0].billingPeriod=="P3D" &&
                            phases[0].billingCycleCount==1 &&
                            phases[1].billingPeriod=="P1W" && phases[1].recurrenceMode==ProductDetails.RecurrenceMode.INFINITE_RECURRING
                    }
                    val weekly=trial ?: regular(BillingConfig.WEEKLY_BASE_PLAN,"P1W")
                    val monthly=regular(BillingConfig.MONTHLY_BASE_PLAN,"P1M")
                    subOffers=listOfNotNull(weekly,monthly).associateBy {it.basePlanId}
                    weeklyHasTrial=trial!=null
                    weeklyPrice=weekly?.let {it.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice}
                    monthlyPrice=monthly?.let {it.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice}
                    subReady=subOffers.isNotEmpty()
                }
                if(result.responseCode!=BillingClient.BillingResponseCode.OK) log(result)
                if(!BillingProof.validKey(BillingConfig.PLAY_LICENSE_PUBLIC_KEY)) status="Purchases unavailable"
            }}
        }
    }
    private fun queryOwned(restore:Boolean) {
        val serial=++querySerial
        val finished=mutableSetOf<String>()
        var allSucceeded=true
        PurchaseAccess.checking=true
        main.postDelayed({if(!closed && serial==querySerial) PurchaseAccess.checking=false},3_000)
        listOf(BillingClient.ProductType.INAPP,BillingClient.ProductType.SUBS).forEach {type ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) {result,list -> dispatch {
                if(serial!=querySerial) return@dispatch
                if(result.responseCode==BillingClient.BillingResponseCode.OK) process(list,type,true)
                else {allSucceeded=false;status="Could not check purchases. Try again later.";log(result)}
                finished+=type
                if(finished.size==2) {
                    PurchaseAccess.checking=false
                    if(restore && !pending && !busy && allSucceeded)
                        status=if(PurchaseAccess.active) "Purchases restored" else "No active purchase found for this Google Play account."
                }
            }}
        }
    }
    private fun process(purchases:List<Purchase>,type:String,authoritative:Boolean) {
        val id=if(type==BillingClient.ProductType.SUBS) BillingConfig.SUBSCRIPTION_ID else BillingConfig.PRODUCT_ID
        val relevant=purchases.filter {id in it.products}
        val owned=relevant.firstOrNull {PurchaseAccess.verified(it)}
        if(relevant.any {it.purchaseState==Purchase.PurchaseState.PENDING}) pendingTypes+=type else pendingTypes-=type
        pending=pendingTypes.isNotEmpty()
        if(owned!=null) {
            if(type==BillingClient.ProductType.SUBS) trackedSubscription=true
            pendingTypes-=type;pending=pendingTypes.isNotEmpty();expectedTokens[type]=owned.purchaseToken
            if(owned.isAcknowledged) {PurchaseAccess.grant(owned);status="Purchase active"}
            else if(acknowledging.add(owned.purchaseToken)) {
                busy=true;PurchaseAccess.checkout=true;status="Activating purchase…"
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(owned.purchaseToken).build()) {result -> dispatch {
                    acknowledging.remove(owned.purchaseToken);busy=acknowledging.isNotEmpty();PurchaseAccess.checkout=busy
                    if(expectedTokens[type]!=owned.purchaseToken) return@dispatch
                    if(result.responseCode==BillingClient.BillingResponseCode.OK) {PurchaseAccess.grant(owned);status="Purchase active"}
                    else {status="Payment received. Tap Restore purchases to finish activation.";log(result)}
                }}
            }
        } else {
            if(authoritative) {
                expectedTokens.remove(type);PurchaseAccess.revoke(type)
                if(type==BillingClient.ProductType.SUBS) trackedSubscription=false
            }
            if(pending) status="Payment pending. Access activates when payment completes."
            else if(relevant.any {it.purchaseState==Purchase.PurchaseState.PURCHASED}) status="Could not verify this purchase. Please contact support."
        }
    }
    fun buy(activity:Activity) {
        if(!canBuy) return
        val detail=onceProduct ?: return
        val offer=onceOffer ?: return
        launch(activity,detail,offer.offerToken)
    }
    fun subscribe(activity:Activity,plan:String) {
        if(!canSubscribe(plan)) return
        val detail=subProduct ?: return
        val offer=subOffers[plan] ?: return
        launch(activity,detail,offer.offerToken)
    }
    private fun launch(activity:Activity,detail:ProductDetails,token:String?) {
        if(activity.isFinishing || activity.isDestroyed) return
        val selectedToken=token?.takeIf {it.isNotBlank()}
        if(detail.productType==BillingClient.ProductType.SUBS && selectedToken==null) {
            status="Subscription unavailable. Please refresh and try again."
            return
        }
        val productParams=BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(detail)
        // Older/legacy one-time purchase offers can have no token. Never force-unwrap
        // or pass an empty string; subscription offers must always have a valid token.
        if(selectedToken!=null) productParams.setOfferToken(selectedToken)
        busy=true;PurchaseAccess.checkout=true;status=""
        val result=client.launchBillingFlow(activity,BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build())).build())
        if(result.responseCode!=BillingClient.BillingResponseCode.OK) {
            busy=false;PurchaseAccess.checkout=false
            if(result.responseCode==BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) refresh(true)
            else {status="Could not start purchase. Please try again.";log(result)}
        }
    }
    private fun log(result:BillingResult) {Log.w("DrawMusicalBilling","Billing ${result.responseCode}: ${result.debugMessage}")}
    fun close() {closed=true;main.removeCallbacksAndMessages(null);client.endConnection();PurchaseAccess.checking=false;PurchaseAccess.checkout=false}
}
