package com.wcs.drawing.game.draw.with.music.`fun`

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Visual shell only: ad callbacks, pricing and expiry stay in the existing owner. */
@Composable
fun UnlockPortraitPanel(
    portraitTitle: String,
    requiredAds: Int,
    watchedAds: Int,
    unlocked: Boolean,
    busy: Boolean,
    adReady: Boolean,
    status: String,
    preview: @Composable (Modifier) -> Unit,
    onDismiss: () -> Unit,
    onWatch: () -> Unit,
    panelTitle: String? = null,
    accessLabel: String = "24-hour access",
    introText: String? = null,
    detailText: String? = null,
    actionLabel: String? = null
) {
    val cream=Color(0xFFFFFEF8)
    val forest=Color(0xFF263F37)
    val mint=Color(0xFF08BFB3)
    val pale=Color(0xFFE6F5EF)
    val line=Color(0xFFDCE2D8)
    val screen=LocalConfiguration.current
    val completed=watchedAds.coerceIn(0,requiredAds.coerceAtLeast(0))
    val remaining=(requiredAds-completed).coerceAtLeast(0)
    val heroSize=if(screen.screenHeightDp<650) 94.dp else 116.dp
    Dialog(onDismissRequest={if(!busy) onDismiss()},properties=DialogProperties(
        usePlatformDefaultWidth=false,dismissOnBackPress=!busy,dismissOnClickOutside=!busy)) {
        Surface(modifier=Modifier.padding(horizontal=22.dp,vertical=24.dp)
            .widthIn(max=440.dp).fillMaxWidth()
            .heightIn(max=(screen.screenHeightDp.dp-48.dp).coerceAtLeast(180.dp)),
            shape=RoundedCornerShape(30.dp),color=cream,tonalElevation=0.dp,shadowElevation=12.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                horizontalAlignment=Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(uiText(panelTitle ?: if(unlocked) "Portrait unlocked" else "Unlock portrait"),
                        modifier=Modifier.weight(1f),fontSize=23.sp,lineHeight=28.sp,
                        fontWeight=FontWeight.Bold,color=forest)
                    IconButton(enabled=!busy,onClick=onDismiss,modifier=Modifier.size(48.dp)
                        .semantics {contentDescription="Close unlock panel"}) {
                        Canvas(Modifier.size(18.dp)) {
                            val color=if(busy) forest.copy(alpha=.35f) else forest
                            drawLine(color,Offset(3f,3f),Offset(size.width-3f,size.height-3f),
                                strokeWidth=3.dp.toPx(),cap=StrokeCap.Round)
                            drawLine(color,Offset(size.width-3f,3f),Offset(3f,size.height-3f),
                                strokeWidth=3.dp.toPx(),cap=StrokeCap.Round)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.size(heroSize+24.dp),contentAlignment=Alignment.Center) {
                    Box(Modifier.matchParentSize().background(pale,CircleShape))
                    Surface(Modifier.size(heroSize),shape=RoundedCornerShape(22.dp),
                        color=Color(0xFFFAFAF0),border=BorderStroke(3.dp,Color.White),shadowElevation=2.dp) {
                        preview(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)))
                    }
                    Surface(Modifier.align(Alignment.BottomEnd).size(36.dp),shape=CircleShape,
                        color=mint,border=BorderStroke(3.dp,cream)) {
                        Canvas(Modifier.padding(9.dp)) {
                            if(unlocked) {
                                drawLine(forest,Offset(0f,size.height*.5f),Offset(size.width*.4f,size.height*.85f),
                                    strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)
                                drawLine(forest,Offset(size.width*.4f,size.height*.85f),Offset(size.width,size.height*.12f),
                                    strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)
                            } else {
                                drawRoundRect(forest,Offset(0f,size.height*.43f),
                                    androidx.compose.ui.geometry.Size(size.width,size.height*.55f),
                                    androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                                drawArc(forest,180f,180f,false,Offset(size.width*.2f,0f),
                                    androidx.compose.ui.geometry.Size(size.width*.6f,size.height*.8f),
                                    style=Stroke(2.dp.toPx()))
                            }
                        }
                    }
                }
                Text(uiText(portraitTitle),modifier=Modifier.padding(top=12.dp),fontSize=23.sp,
                    fontWeight=FontWeight.SemiBold,color=forest,textAlign=TextAlign.Center)
                Surface(Modifier.padding(top=9.dp),shape=RoundedCornerShape(50),color=pale) {
                    Text(uiText(accessLabel),modifier=Modifier.padding(horizontal=14.dp,vertical=7.dp),
                        color=forest,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                }
                Text(introText ?: if(unlocked) "Your portrait is ready to open."
                    else "Watch ${if(remaining==1) "one more ad" else "$remaining more ads"} to unlock this portrait.",
                    modifier=Modifier.padding(top=14.dp,bottom=14.dp),color=forest.copy(alpha=.8f),
                    fontSize=15.sp,lineHeight=21.sp,textAlign=TextAlign.Center)
                if(!unlocked) {
                    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),
                        color=Color.White,border=BorderStroke(1.dp,line)) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Text("Ad progress",modifier=Modifier.weight(1f),color=forest,fontSize=14.sp)
                                Text("$completed / $requiredAds",color=forest,fontSize=14.sp,fontWeight=FontWeight.Bold)
                            }
                            LinearProgressIndicator(progress={completed.toFloat()/requiredAds.coerceAtLeast(1)},
                                modifier=Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                                color=mint,trackColor=Color(0xFFEAF0E8))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        if(!adReady && !status.startsWith("Ad unavailable") && !status.startsWith("Ad closed"))
                            CircularProgressIndicator(Modifier.size(14.dp),color=mint,strokeWidth=2.dp)
                        else Box(Modifier.size(7.dp).background(if(adReady) mint else forest.copy(alpha=.4f),CircleShape))
                        Text(uiText(status),color=forest.copy(alpha=.75f),fontSize=12.sp,lineHeight=17.sp)
                    }

                }
                Button(onClick=onWatch,enabled=!busy && (unlocked || adReady),
                    modifier=Modifier.fillMaxWidth().padding(top=18.dp).heightIn(min=56.dp),
                    shape=RoundedCornerShape(28.dp),contentPadding=PaddingValues(16.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=mint,contentColor=forest,
                        disabledContainerColor=mint.copy(alpha=.23f),disabledContentColor=forest.copy(alpha=.5f))) {
                    Text(uiText(actionLabel ?: if(unlocked) "Open portrait" else "Watch ad"),fontSize=17.sp,fontWeight=FontWeight.Bold)
                }
                TextButton(onClick=onDismiss,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Text(uiText("Cancel"),color=forest.copy(alpha=if(busy) .35f else .75f),fontSize=14.sp)
                }
            }
        }
    }
}
