package com.wcs.drawing.game.draw.with.music.`fun`

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val IntroForest=Color(0xFF263F37)
private val IntroMint=Color(0xFF08BFB3)

@Composable
fun OnboardingFlow(onFinish:()->Unit) {
    var page by rememberSaveable {mutableIntStateOf(0)}
    val titles=listOf("Draw your imagination","Hear your drawing","Keep every creation")
    val descriptions=listOf(
        "Start with a blank canvas or choose a pattern. Draw, erase, and switch between smooth lines and pixels.",
        "Choose instruments and backing sounds, then press Play. Keep drawing while your changes join the next loop.",
        "Your work saves automatically to Draft. Export MIDI, WAV, or MP4 and find your files in Library.")
    BackHandler(enabled=page>0) {page--}
    Surface(modifier=Modifier.fillMaxSize(),color=Color(0xFFFFFEF8)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=24.dp,vertical=16.dp)) {
            val heroHeight=if(maxHeight<600.dp) 190.dp else 285.dp
            Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row {
                            Text("Draw ",fontSize=25.sp,fontStyle=FontStyle.Italic,fontWeight=FontWeight.Bold,color=IntroForest)
                            Text("Musical",fontSize=25.sp,fontStyle=FontStyle.Italic,fontWeight=FontWeight.Bold,color=IntroMint)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick=onFinish) {Text(uiText("Skip"),color=IntroForest)}
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top=24.dp,bottom=16.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    Surface(modifier=Modifier.fillMaxWidth().height(heroHeight),shape=RoundedCornerShape(36.dp),
                        color=listOf(Color(0xFFE6F6ED),Color(0xFFEEE8FA),Color(0xFFFFF1D9))[page]) {
                        OnboardingIllustration(page,Modifier.fillMaxSize().padding(18.dp))
                    }
                    Text(uiText(titles[page]),fontSize=30.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold,
                        color=IntroForest,textAlign=TextAlign.Center)
                }
                Row(Modifier.padding(vertical=16.dp).semantics {contentDescription="${page+1} / 3"},
                    horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    repeat(3) {index ->
                        Box(Modifier.width(if(index==page) 28.dp else 8.dp).height(8.dp)
                            .background(if(index==page) IntroMint else Color(0xFFDCDFD8),CircleShape))
                    }
                }
                Button(onClick={if(page==2) onFinish() else page++},
                    modifier=Modifier.fillMaxWidth().heightIn(min=60.dp),shape=RoundedCornerShape(32.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=IntroMint)) {
                    Text(uiText(if(page==2) "Get started" else "Next"),fontSize=20.sp,color=Color.White)
                }
                if(page>0) TextButton(onClick={page--}) {Text(uiText("Back"),color=IntroForest)}
                else Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun OnboardingIllustration(page:Int,modifier:Modifier) {
    Canvas(modifier) {
        // Artwork is code-native and scales with the screen; no new image assets are required.
        val side=minOf(size.width,size.height)
        val left=(size.width-side)/2;val top=(size.height-side)/2
        fun pt(x:Float,y:Float)=Offset(left+x*side,top+y*side)
        fun rect(x:Float,y:Float,w:Float,h:Float,color:Color,r:Float=.05f) {
            drawRoundRect(color,pt(x,y),Size(w*side,h*side),androidx.compose.ui.geometry.CornerRadius(r*side))
        }
        val purple=Color(0xFF8878D7);val gold=Color(0xFFE9A500);val orange=Color(0xFFCD582A)
        when(page) {
            0 -> {
                rect(.10f,.08f,.72f,.84f,Color.White)
                val heart=Path().apply {
                    moveTo(pt(.46f,.73f).x,pt(.46f,.73f).y)
                    cubicTo(pt(.02f,.43f).x,pt(.02f,.43f).y,pt(.27f,.14f).x,pt(.27f,.14f).y,pt(.46f,.38f).x,pt(.46f,.38f).y)
                    cubicTo(pt(.68f,.13f).x,pt(.68f,.13f).y,pt(.94f,.43f).x,pt(.94f,.43f).y,pt(.46f,.73f).x,pt(.46f,.73f).y)
                }
                drawPath(heart,orange,style=Stroke(side*.025f,cap=StrokeCap.Round))
                drawLine(IntroForest,pt(.66f,.65f),pt(.87f,.21f),side*.065f,StrokeCap.Round)
                drawLine(gold,pt(.67f,.64f),pt(.63f,.73f),side*.045f,StrokeCap.Round)
                for(i in 0..3) rect(.20f+i*.13f,.82f,.08f,.04f,listOf(purple,gold,IntroMint,orange)[i],.01f)
            }
            1 -> {
                rect(.10f,.53f,.80f,.35f,Color.White)
                repeat(7) {i ->
                    val x=.15f+i*.10f
                    rect(x,.58f,.075f,.23f,if(i%2==0) purple else Color(0xFFE9E4F5),.012f)
                }
                drawCircle(IntroMint,side*.17f,pt(.5f,.32f))
                val play=Path().apply {moveTo(pt(.46f,.23f).x,pt(.46f,.23f).y);lineTo(pt(.60f,.32f).x,pt(.60f,.32f).y);lineTo(pt(.46f,.41f).x,pt(.46f,.41f).y);close()}
                drawPath(play,Color.White)
                drawLine(orange,pt(.16f,.27f),pt(.16f,.11f),side*.018f)
                drawOval(orange,pt(.09f,.24f),Size(side*.09f,side*.06f))
                drawLine(gold,pt(.84f,.36f),pt(.84f,.15f),side*.018f)
                drawOval(gold,pt(.77f,.33f),Size(side*.09f,side*.06f))
            }
            else -> {
                rect(.14f,.14f,.32f,.65f,Color.White)
                rect(.21f,.24f,.18f,.03f,purple,.01f);rect(.21f,.32f,.18f,.03f,gold,.01f)
                rect(.21f,.40f,.13f,.03f,IntroMint,.01f)
                rect(.52f,.24f,.32f,.65f,Color.White)
                drawCircle(IntroMint,side*.09f,pt(.68f,.44f))
                drawLine(Color.White,pt(.635f,.44f),pt(.67f,.475f),side*.018f,StrokeCap.Round)
                drawLine(Color.White,pt(.67f,.475f),pt(.73f,.405f),side*.018f,StrokeCap.Round)
                for(i in 0..2) rect(.57f,.61f+i*.07f,.22f,.035f,listOf(purple,gold,IntroMint)[i],.01f)
            }
        }
    }
}
