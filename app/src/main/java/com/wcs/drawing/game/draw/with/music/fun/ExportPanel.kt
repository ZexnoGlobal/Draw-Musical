package com.wcs.drawing.game.draw.with.music.`fun`

import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reference-style export chooser. Existing export callbacks and file formats are unchanged. */
@Composable
fun ExportChoiceDialog(onDismiss:()->Unit,onChoose:(ExportKind)->Unit) {
    val forest=Color(0xFF263F37)
    val cream=Color(0xFFFFFEF8)
    val context=LocalContext.current
    val book by produceState<ImageBitmap?>(null) {
        value=withContext(Dispatchers.IO) {
            runCatching {context.assets.open("ui/export_book.png").use {
                requireNotNull(BitmapFactory.decodeStream(it)).asImageBitmap()
            }}.getOrNull()
        }
    }
    val screenHeight=LocalConfiguration.current.screenHeightDp.dp
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window?.setDimAmount(.6f)}
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=22.dp)
            .heightIn(max=(screenHeight-40.dp).coerceAtLeast(180.dp)),contentAlignment=Alignment.Center) {
            val compact=maxHeight<560.dp
            val heroSize=if(compact) 132.dp else 164.dp
            val cardTop=if(compact) 54.dp else 72.dp
            Box(Modifier.widthIn(max=480.dp).fillMaxWidth()) {
                Surface(modifier=Modifier.fillMaxWidth().padding(top=cardTop),
                    shape=RoundedCornerShape(22.dp),color=cream) {
                    Column(Modifier.verticalScroll(rememberScrollState())
                        .padding(start=22.dp,end=22.dp,top=heroSize-cardTop+14.dp,bottom=24.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(uiText("Export"),color=forest,fontSize=22.sp,lineHeight=27.sp,
                            fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                        ExportKind.values().forEach {kind ->ReferenceExportOption(kind,onClick={onChoose(kind)})}
                    }
                }
                book?.let {illustration ->
                    Image(illustration,contentDescription=null,
                        modifier=Modifier.align(Alignment.TopCenter).size(heroSize))
                }
                IconButton(onClick=onDismiss,modifier=Modifier.align(Alignment.TopEnd)
                    .padding(top=cardTop+4.dp,end=4.dp).size(48.dp).semantics {contentDescription="Close export"}) {
                    Canvas(Modifier.size(19.dp)) {
                        val padding=size.width*.22f
                        val stroke=size.width*.2f
                        drawLine(forest,Offset(padding,padding),Offset(size.width-padding,size.height-padding),
                            strokeWidth=stroke,cap=androidx.compose.ui.graphics.StrokeCap.Round)
                        drawLine(forest,Offset(size.width-padding,padding),Offset(padding,size.height-padding),
                            strokeWidth=stroke,cap=androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceExportOption(kind:ExportKind,onClick:()->Unit) {
    val forest=Color(0xFF263F37)
    val title=when(kind) {ExportKind.MIDI->"Music notes";ExportKind.WAV->"Audio recording";ExportKind.MP4->"Video recording"}
    OutlinedButton(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=72.dp),
        shape=RoundedCornerShape(40.dp),border=BorderStroke(1.2.dp,Color(0xFFD0D0CB)),
        colors=ButtonDefaults.outlinedButtonColors(containerColor=Color(0xFFFAFAF1),contentColor=forest),
        contentPadding=PaddingValues(horizontal=10.dp,vertical=10.dp)) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                ExportOptionIcon(kind,Modifier.size(20.dp))
                Text(uiText(title),fontSize=20.sp,lineHeight=25.sp,fontWeight=FontWeight.Normal,textAlign=TextAlign.Center)
            }
            Text(kind.extension.uppercase(),color=Color(0xFF6A6A66),fontSize=12.sp,lineHeight=16.sp,textAlign=TextAlign.Center)
        }
    }
}

@Composable
private fun ExportOptionIcon(kind:ExportKind,modifier:Modifier) {
    val forest=Color(0xFF263F37)
    Canvas(modifier) {
        val w=size.width;val h=size.height
        when(kind) {
            ExportKind.MIDI -> {
                drawOval(forest,Offset(w*.12f,h*.62f),Size(w*.43f,h*.3f))
                drawLine(forest,Offset(w*.52f,h*.76f),Offset(w*.52f,h*.10f),strokeWidth=w*.12f)
                val flag=Path().apply {
                    moveTo(w*.52f,h*.1f);cubicTo(w*.6f,h*.35f,w*.86f,h*.18f,w*.83f,h*.45f)
                    cubicTo(w*.74f,h*.3f,w*.61f,h*.4f,w*.52f,h*.28f);close()
                };drawPath(flag,forest)
            }
            ExportKind.WAV -> {
                val speaker=Path().apply {
                    moveTo(w*.08f,h*.35f);lineTo(w*.30f,h*.35f);lineTo(w*.55f,h*.13f)
                    lineTo(w*.55f,h*.87f);lineTo(w*.30f,h*.65f);lineTo(w*.08f,h*.65f);close()
                };drawPath(speaker,forest)
                drawArc(forest,-58f,116f,false,Offset(w*.43f,h*.23f),Size(w*.36f,h*.54f),style=Stroke(w*.08f))
                drawArc(forest,-58f,116f,false,Offset(w*.48f,h*.08f),Size(w*.5f,h*.84f),style=Stroke(w*.08f))
            }
            ExportKind.MP4 -> {
                drawCircle(forest,w*.46f,Offset(w*.5f,h*.5f))
                val triangle=Path().apply {moveTo(w*.40f,h*.28f);lineTo(w*.70f,h*.5f);lineTo(w*.40f,h*.72f);close()}
                drawPath(triangle,Color(0xFFFFFEF8))
            }
        }
    }
}
