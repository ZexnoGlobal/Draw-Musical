package com.wcs.drawing.game.draw.with.music.`fun`

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// Only these bundled assets can be selected. No external URI or file paths are accepted.
data class BuiltInBackground(val id:String,val title:String)
val builtInBackgrounds=listOf(
    BuiltInBackground("dunes","Dunes"),BuiltInBackground("mountains","Mountains"),
    BuiltInBackground("sky","Sky"),BuiltInBackground("sea","Sea"))
data class BackgroundSelection(val colorIndex:Int=0,val photoId:String?=null,val opacity:Float=1f)

fun loadBuiltInBackground(context:Context,id:String,thumbnail:Boolean=false):ImageBitmap {
    require(builtInBackgrounds.any {it.id==id})
    val options=BitmapFactory.Options().apply {inSampleSize=if(thumbnail) 8 else 1}
    return context.assets.open("backgrounds/$id.jpg").use {
        requireNotNull(BitmapFactory.decodeStream(it,null,options)) {"Invalid bundled background"}.asImageBitmap()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundPickerSheet(initial:BackgroundSelection,onPreview:(BackgroundSelection)->Unit,
    onApply:(BackgroundSelection)->Unit,onDismiss:()->Unit) {
    val forest=Color(0xFF263F37)
    val turquoise=Color(0xFF08BFB3)
    val context=LocalContext.current
    var draft by remember {mutableStateOf(initial)}
    var tab by remember {mutableIntStateOf(if(initial.photoId==null) 0 else 1)}
    var thumbnails by remember {mutableStateOf<Map<String,ImageBitmap>>(emptyMap())}
    LaunchedEffect(Unit) {
        thumbnails=withContext(Dispatchers.IO) {
            builtInBackgrounds.mapNotNull {preset ->
                try {preset.id to loadBuiltInBackground(context,preset.id,true)}
                catch(e:Exception) {null}
            }.toMap()
        }
    }
    LaunchedEffect(draft) {onPreview(draft)}
    ModalBottomSheet(onDismissRequest=onDismiss,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=Color(0xFFF4F4F5),dragHandle=null) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal=16.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Surface(shape=RoundedCornerShape(22.dp),color=Color.White,shadowElevation=2.dp) {
                Row(Modifier.fillMaxWidth().height(58.dp)) {
                    listOf("Color","Photo").forEachIndexed {index,title ->
                        Box(Modifier.weight(1f).fillMaxHeight()
                            .background(if(tab==index) forest else Color.White)
                            .clickable {tab=index},contentAlignment=Alignment.Center) {
                            Text(uiText(title),color=if(tab==index) Color.White else forest,fontSize=22.sp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(tab==0) {
                    canvasBackgrounds.forEachIndexed {index,option ->
                        BackgroundThumbnail(option.name,draft.photoId==null && draft.colorIndex==index,
                            onClick={draft=draft.copy(colorIndex=index,photoId=null)}) {
                            Box(Modifier.fillMaxSize().background(option.color))
                        }
                    }
                } else {
                    BackgroundThumbnail("None",draft.photoId==null,
                        onClick={draft=draft.copy(photoId=null)}) {
                        Box(Modifier.fillMaxSize().background(Color(0xFFE2E2E2)),contentAlignment=Alignment.Center) {
                            Text(uiText("None"),color=forest)
                        }
                    }
                    builtInBackgrounds.forEach {preset ->
                        BackgroundThumbnail(preset.title,draft.photoId==preset.id,
                            onClick={draft=draft.copy(photoId=preset.id)}) {
                            val bitmap=thumbnails[preset.id]
                            if(bitmap!=null) Image(bitmap,contentDescription=preset.title,
                                modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                            else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {Text(uiText(preset.title))}
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(uiText("Background opacity"),fontSize=18.sp)
                Text("${(draft.opacity*100).roundToInt()}%",fontSize=18.sp)
            }
            Slider(value=draft.opacity,onValueChange={draft=draft.copy(opacity=it)},
                enabled=draft.photoId!=null,valueRange=0f..1f,
                colors=SliderDefaults.colors(thumbColor=turquoise,activeTrackColor=turquoise))
            Button(onClick={onApply(draft)},modifier=Modifier.fillMaxWidth().height(64.dp),
                shape=RoundedCornerShape(32.dp),colors=ButtonDefaults.buttonColors(containerColor=turquoise)) {
                Text(uiText("Apply"),fontSize=22.sp,color=Color.White)
            }
        }
    }
}

@Composable
private fun BackgroundThumbnail(label:String,selected:Boolean,onClick:()->Unit,content:@Composable ()->Unit) {
    val shape=RoundedCornerShape(16.dp)
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(66.dp,100.dp).clip(shape)
            .border(if(selected) 3.dp else 1.dp,if(selected) Color(0xFF263F37) else Color(0xFFDDDDDD),shape)
            .clickable(onClick=onClick).semantics {contentDescription="$label background"}.padding(5.dp)
            .clip(RoundedCornerShape(11.dp))) {content()}
        Text(uiText(label),fontSize=11.sp)
    }
}
