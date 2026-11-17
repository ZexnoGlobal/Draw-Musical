package com.wcs.drawing.game.draw.with.music.`fun`

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ExitDrawingPanel(onBack:()->Unit,onStay:()->Unit) {
    val forest=Color(0xFF263F37)
    val mint=Color(0xFF08BFB3)
    val height=LocalConfiguration.current.screenHeightDp.dp
    Dialog(onDismissRequest=onStay,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(modifier=Modifier.padding(horizontal=24.dp,vertical=24.dp)
            .widthIn(max=420.dp).fillMaxWidth().heightIn(max=(height-48.dp).coerceAtLeast(180.dp)),
            shape=RoundedCornerShape(30.dp),color=Color(0xFFFFFEF8),tonalElevation=0.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Surface(Modifier.size(64.dp),shape=CircleShape,color=Color(0xFFE6F5EF)) {
                    Box(contentAlignment=Alignment.Center) {Text("♪",fontSize=36.sp,color=forest)}
                }
                Text("Do you really want to exit?",fontSize=23.sp,lineHeight=29.sp,
                    fontWeight=FontWeight.Bold,color=forest,textAlign=TextAlign.Center)
                Text("Your drawing will be saved to Draft.",fontSize=15.sp,lineHeight=21.sp,
                    color=forest.copy(alpha=.75f),textAlign=TextAlign.Center)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Button(onClick=onBack,modifier=Modifier.weight(1f).heightIn(min=54.dp),
                        shape=RoundedCornerShape(28.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=mint,contentColor=forest)) {
                        Text(uiText("Back"),fontSize=16.sp,fontWeight=FontWeight.Bold)
                    }
                    OutlinedButton(onClick=onStay,modifier=Modifier.weight(1f).heightIn(min=54.dp),
                        shape=RoundedCornerShape(28.dp),border=BorderStroke(1.dp,Color(0xFFD4DBD0)),
                        colors=ButtonDefaults.outlinedButtonColors(contentColor=forest)) {
                        Text(uiText("Stay"),fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
