package com.wcs.drawing.game.draw.with.music.`fun`

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PurchaseSettingsCard(modifier:Modifier=Modifier,compact:Boolean=false) {
    val billing=LocalPurchases.current
    var show by rememberSaveable {mutableStateOf(false)}
    IconButton(onClick={show=true;billing?.refresh()},modifier=modifier.size(if(compact) 52.dp else 56.dp)
        .semantics {contentDescription=if(PurchaseAccess.active) "Manage premium" else "Unlock premium"}) {
        Box(Modifier.fillMaxSize().background(Color(0xFFFFF1CA),RoundedCornerShape(17.dp)),contentAlignment=Alignment.Center) {
            PremiumCrown(Modifier.size(39.dp))
        }
    }
    if(show) PremiumPurchasePage(onDismiss={show=false})
}

@Composable
private fun PremiumPurchasePage(onDismiss:()->Unit) {
    val context=LocalContext.current
    val billing=LocalPurchases.current
    val activity=remember(context) {
        generateSequence(context) {(it as? ContextWrapper)?.baseContext}.filterIsInstance<Activity>().firstOrNull()
    }
    var selected by rememberSaveable {mutableStateOf(BillingConfig.WEEKLY_BASE_PLAN)}
    var policy by rememberSaveable {mutableStateOf<String?>(null)}
    var info by remember {mutableStateOf<String?>(null)}
    val forest=Color(0xFF263F37)
    val busy=billing?.busy==true
    val trial=selected==BillingConfig.WEEKLY_BASE_PLAN && billing?.weeklyHasTrial==true
    fun external(url:String) {
        runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}
            .onFailure {info="Could not open this page."}
    }
    Dialog(onDismissRequest={if(!busy) onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=Color(0xFF398B76)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
                    PremiumHero()
                    Column(Modifier.widthIn(max=560.dp).fillMaxWidth().padding(horizontal=20.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(13.dp)) {
                        Text(if(PurchaseAccess.active) "Premium is active" else "Unlock all features",fontSize=29.sp,lineHeight=35.sp,
                            color=Color.White,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("All instruments","All music templates","All musical keys","No full-screen ads").forEach {
                                Text("✓  $it",color=Color.White,fontSize=17.sp,fontWeight=FontWeight.Medium)
                            }
                        }
                        Text("Banner ads remain visible.",color=Color.White.copy(alpha=.9f),fontSize=13.sp)
                        if(!PurchaseAccess.active) {
                            PremiumPlanChoice("Weekly",billing?.weeklyPrice?.let {if(billing?.weeklyHasTrial==true) "Then $it / week" else "$it / week"} ?: "Unavailable",
                                selected==BillingConfig.WEEKLY_BASE_PLAN,billing?.weeklyHasTrial==true,
                                enabled=!busy && billing?.weeklyPrice!=null,onClick={selected=BillingConfig.WEEKLY_BASE_PLAN})
                            PremiumPlanChoice("Monthly",billing?.monthlyPrice?.let {"$it / month"} ?: "Unavailable",
                                selected==BillingConfig.MONTHLY_BASE_PLAN,false,
                                enabled=!busy && billing?.monthlyPrice!=null,onClick={selected=BillingConfig.MONTHLY_BASE_PLAN})
                            Text(if(trial) "3 days free, then ${billing?.weeklyPrice.orEmpty()} per week. Renews automatically until cancelled."
                                else if(selected==BillingConfig.WEEKLY_BASE_PLAN) billing?.weeklyPrice?.let {"$it per week. Renews automatically until cancelled."} ?: "Weekly pricing unavailable."
                                else billing?.monthlyPrice?.let {"$it per month. Renews automatically until cancelled."} ?: "Monthly pricing unavailable.",
                                fontSize=13.sp,lineHeight=18.sp,color=Color.White.copy(alpha=.8f),textAlign=TextAlign.Center)
                        }
                        if(busy || PurchaseAccess.checking) CircularProgressIndicator(Modifier.size(23.dp),color=Color.White)
                        if(!billing?.status.isNullOrBlank()) Text(billing?.status.orEmpty(),fontSize=12.sp,color=Color.White,textAlign=TextAlign.Center)
                        Button(onClick={if(PurchaseAccess.active) onDismiss() else activity?.let {billing?.subscribe(it,selected)}},
                            enabled=!busy && (PurchaseAccess.active || (activity!=null && billing?.canSubscribe(selected)==true)),
                            modifier=Modifier.fillMaxWidth().heightIn(min=59.dp)
                                .background(Brush.horizontalGradient(listOf(Color(0xFF39DAEC),Color(0xFFB5FFE1))),RoundedCornerShape(30.dp)),
                            shape=RoundedCornerShape(30.dp),border=BorderStroke(2.dp,Color.White.copy(alpha=.6f)),
                            colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,contentColor=forest,
                                disabledContainerColor=Color.White.copy(alpha=.25f),disabledContentColor=forest.copy(alpha=.55f))) {
                            Text(if(PurchaseAccess.active) "Continue creating" else if(trial) "Start Free Trial" else "Subscribe",
                                fontSize=23.sp,fontWeight=FontWeight.Bold)
                        }
                        if(!PurchaseAccess.active) {
                            TextButton(enabled=!busy && activity!=null && billing?.canBuy==true,
                                onClick={activity?.let {billing?.buy(it)}}) {
                                Text(billing?.price?.let {"Or buy once · $it"} ?: "One-time purchase unavailable",color=Color.White,fontSize=14.sp)
                            }
                            Text("One-time purchases do not renew.",fontSize=11.sp,color=Color.White.copy(alpha=.7f))
                        }
                        Row(horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                            TextButton(onClick={if(BillingConfig.TERMS_URL.isNotBlank()) external(BillingConfig.TERMS_URL) else policy="Terms of Service"}) {
                                Text("Terms of Service",color=Color.White,fontSize=12.sp)
                            }
                            Text("|",color=Color.White.copy(alpha=.65f))
                            TextButton(onClick={if(BillingConfig.PRIVACY_URL.isNotBlank()) external(BillingConfig.PRIVACY_URL) else policy="Privacy information"}) {
                                Text("Privacy Policy",color=Color.White,fontSize=12.sp)
                            }
                        }
                        Text("Subscriptions are optional. Manage or cancel in Google Play. Uninstalling the app does not cancel a subscription.",
                            fontSize=12.sp,lineHeight=17.sp,color=Color.White.copy(alpha=.7f),textAlign=TextAlign.Center)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                            TextButton(enabled=!busy,onClick={billing?.refresh(restore=true)}) {Text("Restore purchases",color=Color.White,fontSize=12.sp)}
                            if(!PurchaseAccess.lifetime) TextButton(onClick={external(
                                "https://play.google.com/store/account/subscriptions?sku=${BillingConfig.SUBSCRIPTION_ID}&package=${context.packageName}")}) {
                                Text("Manage subscription",color=Color.White,fontSize=12.sp)
                            }
                        }
                        if(info!=null) Text(info!!,color=Color.White,fontSize=12.sp)
                        Spacer(Modifier.height(16.dp))
                    }
                }
                IconButton(enabled=!busy,onClick=onDismiss,modifier=Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .size(48.dp).background(forest,RoundedCornerShape(15.dp)).semantics {contentDescription="Close premium"}) {
                    Text("×",fontSize=34.sp,color=Color.White,fontWeight=FontWeight.Bold)
                }
            }
        }
    }
    if(policy!=null) AlertDialog(onDismissRequest={policy=null},title={Text(policy!!)},text={Text(
        if(policy=="Terms of Service") "Weekly and monthly plans renew at the displayed Google Play price until cancelled in Google Play. Access continues while your subscription is eligible. One-time purchases do not renew. Banner ads remain. Uninstalling does not cancel subscriptions. Restore using the purchasing Google Play account."
        else "Drafts and exported files are stored on your device. Google Play processes purchases. CAS and its advertising partners may process device identifiers, IP addresses, ad interactions and consent choices. Ad privacy options are available in Settings.")},
        confirmButton={TextButton(onClick={policy=null}) {Text("Close")}})
}

@Composable
private fun PremiumPlanChoice(title:String,price:String,selected:Boolean,trial:Boolean,enabled:Boolean,onClick:()->Unit) {
    val forest=Color(0xFF263F37)
    Surface(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(40.dp),
        color=if(selected) Color.White else Color(0xFF32A68B)) {
        Row(Modifier.padding(horizontal=13.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(25.dp).border(1.5.dp,if(selected) Color(0xFF08BFB3) else Color.White.copy(alpha=.5f),CircleShape)
                .background(if(selected) Color(0xFF08BFB3) else Color.Transparent,CircleShape),contentAlignment=Alignment.Center) {
                if(selected) Text("✓",color=Color.White,fontSize=17.sp,fontWeight=FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(title,fontWeight=FontWeight.Bold,fontSize=23.sp,color=if(selected) forest else Color.White)
                Text(price,fontSize=14.sp,color=if(selected) forest.copy(alpha=.6f) else Color.White.copy(alpha=.75f))
            }
            if(trial) Box(Modifier.background(Color(0xFFFFD52E),RoundedCornerShape(30.dp)).padding(horizontal=10.dp,vertical=9.dp)) {
                Text("3 days free",fontSize=11.sp,fontWeight=FontWeight.Bold,color=Color(0xFF805000))
            }
        }
    }
}
