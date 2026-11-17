package com.wcs.drawing.game.draw.with.music.`fun`

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PremiumCrown(modifier:Modifier=Modifier) {
    Canvas(modifier) {
        val w=size.width;val h=size.height
        val crown=Path().apply {
            moveTo(w*.16f,h*.32f);lineTo(w*.34f,h*.47f);lineTo(w*.50f,h*.18f)
            lineTo(w*.66f,h*.47f);lineTo(w*.84f,h*.32f);lineTo(w*.76f,h*.76f)
            lineTo(w*.24f,h*.76f);close()
        }
        drawPath(crown,Brush.verticalGradient(listOf(Color(0xFFFFE770),Color(0xFFFFB40C))))
        drawLine(Color(0xFFE99A00),Offset(w*.25f,h*.80f),Offset(w*.75f,h*.80f),strokeWidth=h*.08f,cap=StrokeCap.Round)
        listOf(Offset(w*.16f,h*.26f),Offset(w*.5f,h*.12f),Offset(w*.84f,h*.26f)).forEach {
            drawCircle(Color(0xFFEF856D),w*.065f,it)
        }
        drawLine(Color.White.copy(alpha=.6f),Offset(w*.31f,h*.56f),Offset(w*.28f,h*.69f),strokeWidth=w*.04f,cap=StrokeCap.Round)
    }
}

@Composable
fun PremiumHero(modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val templates by produceState<List<MusicTemplate>>(emptyList()) {
        value=runCatching {TemplateCatalog.load(context.applicationContext)}.getOrDefault(emptyList())
    }
    val bird=templates.firstOrNull {it.id=="bird"} ?: templates.firstOrNull {it.id=="chicken"}
    Box(modifier.fillMaxWidth().height(320.dp)
        .background(Brush.verticalGradient(listOf(Color(0xFF99F1DE),Color(0xFFD9FFF2))))) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha=.22f),size.width*.31f,Offset(0f,size.height*.35f))
            drawCircle(Color.White.copy(alpha=.26f),size.width*.34f,Offset(size.width,size.height*.24f))
            val wave=Path().apply {
                moveTo(0f,size.height*.79f)
                cubicTo(size.width*.16f,size.height*.69f,size.width*.26f,size.height*.98f,size.width*.44f,size.height*.86f)
                cubicTo(size.width*.62f,size.height*.77f,size.width*.74f,size.height*.98f,size.width,size.height*.77f)
                lineTo(size.width,size.height);lineTo(0f,size.height);close()
            }
            drawPath(wave,Color(0xFF398B76))
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top=13.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("SUPER",color=Color.White,fontWeight=FontWeight.Black,fontStyle=FontStyle.Italic,fontSize=22.sp)
            Text("Draw Musical",color=Color(0xFF263F37),fontWeight=FontWeight.Bold,fontStyle=FontStyle.Italic,fontSize=31.sp)
        }
        Box(Modifier.align(Alignment.Center).padding(top=34.dp).width(244.dp).height(171.dp)
            .rotate(-12f).shadow(9.dp,RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFFEF6))) {
            Canvas(Modifier.fillMaxSize()) {
                val step=size.width/12
                for(i in 0..12) drawLine(Color(0xFFE8EAE1).copy(alpha=.6f),Offset(i*step,0f),Offset(i*step,size.height),strokeWidth=1f)
                for(i in 0..9) drawLine(Color(0xFFE8EAE1).copy(alpha=.6f),Offset(0f,i*step),Offset(size.width,i*step),strokeWidth=1f)
                bird?.lines?.forEach {stroke ->
                    stroke.points.zipWithNext().forEach {(a,b)->
                        drawLine(instruments[stroke.instrument].color,
                            Offset(size.width*(.12f+a.x*.76f),size.height*(.08f+a.y*.84f)),
                            Offset(size.width*(.12f+b.x*.76f),size.height*(.08f+b.y*.84f)),
                            strokeWidth=3.dp.toPx(),cap=StrokeCap.Round)
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end=9.dp,bottom=44.dp).size(72.dp).rotate(25f)) {
            Canvas(Modifier.fillMaxSize()) {
                val w=size.width;val h=size.height
                drawLine(Color(0xFFF2CB62),Offset(w*.2f,h*.7f),Offset(w*.8f,h*.1f),strokeWidth=w*.27f,cap=StrokeCap.Round)
                drawLine(Color(0xFF8B7CDD),Offset(w*.25f,h*.74f),Offset(w*.84f,h*.15f),strokeWidth=w*.09f,cap=StrokeCap.Round)
                val tip=Path().apply {moveTo(w*.12f,h*.70f);lineTo(w*.27f,h*.86f);lineTo(w*.04f,h*.95f);close()}
                drawPath(tip,Color(0xFF263F37))
            }
        }
        listOf(Triple(Alignment.TopStart,"♪",Color(0xFFF18778)),Triple(Alignment.CenterEnd,"♫",Color(0xFF8878D7))).forEach {(align,note,color)->
            Text(note,modifier=Modifier.align(align).padding(horizontal=24.dp,vertical=73.dp),color=color,fontSize=36.sp)
        }
        Box(Modifier.align(Alignment.BottomStart).padding(start=23.dp,bottom=23.dp).size(57.dp).rotate(-14f)
            .shadow(3.dp,RoundedCornerShape(13.dp)).background(Color(0xFFFFFEF6),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) {
            Text("✿",color=Color(0xFFE79120),fontSize=37.sp)
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end=23.dp,bottom=12.dp).size(66.dp).rotate(13f)
            .shadow(3.dp,RoundedCornerShape(15.dp)).background(Color(0xFFFFFEF6),RoundedCornerShape(15.dp)),contentAlignment=Alignment.Center) {
            PremiumCrown(Modifier.size(48.dp))
        }
    }
}
