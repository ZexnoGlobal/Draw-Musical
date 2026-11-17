package com.wcs.drawing.game.draw.with.music.`fun`

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class CanvasBackground(val name: String,val color: Color)
val canvasBackgrounds=listOf(
    CanvasBackground("Cream",Color(0xFFF5F5ED)),
    CanvasBackground("White",Color.White),
    CanvasBackground("Blush",Color(0xFFFFE8E3)),
    CanvasBackground("Mint",Color(0xFFE5F2E9)),
    CanvasBackground("Lavender",Color(0xFFEEE8FA))
)

@Composable
fun GlyphTile(kind: String,description: String,enabled: Boolean=true,selected: Boolean=false,
    compact: Boolean=false,onClick:()->Unit) {
    val dimension=if(compact) 42.dp else 46.dp
    Surface(Modifier.size(dimension).semantics {contentDescription=description}
        .clip(RoundedCornerShape(15.dp)).clickable(enabled=enabled,onClick=onClick),
        shape=RoundedCornerShape(15.dp),color=if(selected) Color(0xFFE9ECE7) else Color.White) {
        Box(contentAlignment=Alignment.Center) {ToolbarGlyph(kind,Modifier.size(25.dp),enabled)}
    }
}

@Composable
fun ToolbarGlyph(kind: String,modifier: Modifier=Modifier,enabled: Boolean=true) {
    Canvas(modifier) {
        val ink=Color(0xFF263F37).copy(alpha=if(enabled) 1f else .3f)
        val w=size.width;val h=size.height
        fun p(x:Float,y:Float)=Offset(x*w,y*h)
        val width=w*.07f
        when(kind) {
            "pixel" -> {
                for((x,y) in listOf(.1f to .52f,.42f to .52f,.42f to .2f,.74f to .2f))
                    drawRect(ink,p(x,y),Size(w*.25f,h*.25f))
            }
            "background" -> {
                drawRoundRect(ink,p(.1f,.12f),Size(w*.78f,h*.76f),
                    androidx.compose.ui.geometry.CornerRadius(w*.12f),style=Stroke(width))
                drawLine(ink,p(.2f,.76f),p(.73f,.23f),width,cap=StrokeCap.Round)
                drawCircle(ink,w*.06f,p(.32f,.3f))
                drawCircle(ink,w*.045f,p(.72f,.68f))
            }
            else -> {
                val path=Path().apply {
                    moveTo(w*.1f,h*.7f)
                    cubicTo(w*.2f,h*.28f,w*.78f,h*.08f,w*.71f,h*.3f)
                    cubicTo(w*.64f,h*.5f,w*.21f,h*.56f,w*.3f,h*.73f)
                    cubicTo(w*.41f,h*.94f,w*.75f,h*.48f,w*.84f,h*.52f)
                    cubicTo(w*.95f,h*.59f,w*.68f,h*.96f,w*.95f,h*.79f)
                }
                drawPath(path,ink,style=Stroke(width,cap=StrokeCap.Round))
            }
        }
    }
}
