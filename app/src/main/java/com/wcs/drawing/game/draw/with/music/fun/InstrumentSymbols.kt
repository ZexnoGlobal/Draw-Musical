package com.wcs.drawing.game.draw.with.music.`fun`

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

/** Original code-drawn symbols; no icon pack, bitmap, or paid sound assets required. */
@Composable
fun InstrumentSymbol(index: Int, modifier: Modifier=Modifier) {
    Canvas(modifier) {
        val w=size.width;val h=size.height;val white=Color.White.copy(alpha=.93f)
        val line=Stroke(width=w*.045f)
        fun p(x:Float,y:Float)=Offset(x*w,y*h)
        fun segment(x:Float,y:Float,x2:Float,y2:Float) { drawLine(white,p(x,y),p(x2,y2),w*.045f) }
        when(index) {
            0 -> {
                drawRoundRect(white,p(.08f,.12f),Size(w*.84f,h*.76f),CornerRadius(w*.07f),style=line)
                for(i in 1..3) segment(.08f+.21f*i,.13f,.08f+.21f*i,.86f)
                for(x in listOf(.23f,.44f,.65f)) drawRoundRect(white,p(x,.14f),Size(w*.1f,h*.42f),CornerRadius(w*.015f))
            }
            1,7 -> {
                for(i in 0..4) {
                    val width=.78f-i*.10f
                    drawRoundRect(white,p((1-width)/2,.14f+i*.145f),Size(w*width,h*.075f),CornerRadius(w*.025f))
                }
                segment(.24f,.2f,.34f,.9f);segment(.76f,.2f,.66f,.9f)
            }
            2 -> {
                val path=Path().apply {
                    moveTo(w*.2f,h*.7f);cubicTo(w*.4f,h*.5f,w*.18f,h*.25f,w*.5f,h*.22f)
                    cubicTo(w*.82f,h*.25f,w*.6f,h*.5f,w*.8f,h*.7f);close()
                }
                drawPath(path,white,style=line);segment(.2f,.75f,.8f,.75f)
                drawCircle(white,w*.06f,p(.5f,.85f));drawCircle(white,w*.035f,p(.5f,.15f))
            }
            3,10 -> {
                val path=Path()
                for(i in 0..80) {
                    val x=.08f+i/80f*.84f
                    val y=.5f+sin(i/80.0*PI*2)*.25
                    if(i==0) path.moveTo(x*w,(y*h).toFloat()) else path.lineTo(x*w,(y*h).toFloat())
                }
                drawPath(path,white,style=line)
                if(index==10) segment(.08f,.87f,.92f,.87f)
            }
            4,8 -> {
                drawOval(white,p(.1f,.5f),Size(w*.5f,h*.4f),style=line)
                drawCircle(white,w*.07f,p(.4f,.65f),style=line)
                segment(.4f,.65f,.78f,.16f);segment(.46f,.69f,.84f,.2f)
                segment(.73f,.13f,.88f,.25f);segment(.23f,.78f,.42f,.78f)
            }
            5 -> {
                segment(.12f,.83f,.78f,.12f);segment(.22f,.91f,.88f,.2f)
                segment(.12f,.83f,.22f,.91f);segment(.78f,.12f,.88f,.2f)
                for(i in 0..4) drawCircle(white,w*.025f,p(.3f+i*.1f,.74f-i*.11f))
            }
            6 -> {
                drawOval(white,p(.25f,.45f),Size(w*.46f,h*.45f),style=line)
                segment(.46f,.7f,.46f,.08f);segment(.55f,.7f,.55f,.08f)
                segment(.37f,.09f,.63f,.09f);segment(.25f,.63f,.72f,.63f)
                segment(.5f,.9f,.5f,.98f)
            }
            9 -> {
                val path=Path().apply {
                    moveTo(w*.08f,h*.7f);lineTo(w*.08f,h*.3f);lineTo(w*.35f,h*.3f)
                    lineTo(w*.35f,h*.7f);lineTo(w*.62f,h*.7f);lineTo(w*.62f,h*.3f);lineTo(w*.91f,h*.3f)
                };drawPath(path,white,style=line)
            }
            else -> {
                val path=Path().apply {
                    moveTo(w*.7f,h*.1f);lineTo(w*.55f,h*.1f);lineTo(w*.55f,h*.63f)
                    cubicTo(w*.55f,h*.93f,w*.18f,h*.93f,w*.22f,h*.62f)
                };drawPath(path,white,style=line)
                drawOval(white,p(.12f,.47f),Size(w*.25f,h*.15f),style=line)
                for(i in 0..2) drawCircle(white,w*.025f,p(.65f,.3f+i*.13f))
            }
        }
    }
}
