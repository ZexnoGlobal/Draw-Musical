package com.wcs.drawing.game.draw.with.music.`fun`

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.util.UUID
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import kotlin.math.*

data class TemplateLine(val instrument: Int,val points: List<Offset>)
data class MusicTemplate(val id: String,val title: String,val bpm: Int,
    val beat: Boolean,val lines: List<TemplateLine>,val project: MelodyProject,val pixel: Boolean=false)

private fun readTemplates(context: Context): List<MusicTemplate> {
    val text=context.assets.open("music_templates.json").bufferedReader().use {it.readText()}
    val json=JSONObject(text)
    require(json.getInt("version")==1)
    val array=json.getJSONArray("templates")
    return (0 until array.length()).map { index ->
        val item=array.getJSONObject(index)
        val source=item.getJSONArray("strokes")
        fun parseLines(raw: JSONArray): List<TemplateLine> = (0 until raw.length()).map { i ->
            val stroke=raw.getJSONObject(i);val instrument=stroke.getInt("instrument")
            require(instrument in instruments.indices)
            val points=stroke.getJSONArray("points")
            TemplateLine(instrument,(0 until points.length()).map { j ->
                val point=points.getJSONArray(j)
                val x=point.getDouble(0).toFloat();val y=point.getDouble(1).toFloat()
                require(x in 0f..1f && y in 0f..1f)
                Offset(x,y)
            })
        }
        val lines=parseLines(source)
        val visualLines=parseLines(item.optJSONArray("smooth_strokes") ?: source)
        val cells=linkedMapOf<Pair<Int,Int>,Cell>()
        fun put(point: Offset,instrument: Int) {
            val x=(point.x*(MUSIC_COLUMNS-1)).roundToInt().coerceIn(0,MUSIC_COLUMNS-1)
            val y=(point.y*(MUSIC_ROWS-1)).roundToInt().coerceIn(0,MUSIC_ROWS-1)
            cells[x to y]=Cell(x,y,instrument)
        }
        lines.forEach { line ->
            line.points.firstOrNull()?.let {put(it,line.instrument)}
            line.points.zipWithNext().forEach { (a,b) ->
                val steps=ceil(max(abs(b.x-a.x)*MUSIC_COLUMNS,abs(b.y-a.y)*MUSIC_ROWS)*2).toInt().coerceAtLeast(1)
                for(i in 1..steps) put(a+(b-a)*(i.toFloat()/steps),line.instrument)
            }
        }
        // Pixel templates store exact cells; do not interpolate extra connecting notes.
        item.optJSONArray("cells")?.let { raw ->
            for(i in 0 until raw.length()) {
                val c=raw.getJSONArray(i)
                val x=c.getInt(0);val y=c.getInt(1);val instrument=c.getInt(2)
                require(x in 0 until MUSIC_COLUMNS && y in 0 until MUSIC_ROWS && instrument in instruments.indices)
                cells[x to y]=Cell(x,y,instrument)
            }
        }
        val bpm=item.getInt("bpm").coerceIn(40,240)
        MusicTemplate(item.getString("id"),item.getString("title"),bpm,
            item.optString("mode")=="beat",lines,
            MelodyProject(strokes=listOf(Stroke(cells.values.toList())),tempo=bpm,root=0,scale=0,
                visuals=visualLines.map {line -> VisualStroke(line.instrument,line.points.map {
                    DrawPoint(it.x*(MUSIC_COLUMNS-1)+.5f,it.y*(MUSIC_ROWS-1)+.5f)
                }) }.ifEmpty {legacySmoothPaths(cells.values.toList())}),
            pixel=item.optString("mode")=="pixels")
    }
}

object TemplateCatalog {
    private val mutex= Mutex()
    private var cached:List<MusicTemplate>?=null
    suspend fun load(context:Context):List<MusicTemplate> = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {readTemplates(context)}.also {cached=it}
    }
}

@Composable
fun MusicAppRoot(audio:MelodyAudio) {
    val ads=LocalCasAds.current
    if(!StartupNotificationPermissionGate()) return
    if(!CasAppOpenLaunchGate(ads)) return
    LaunchedEffect(ads) { ads?.initialize() }
    LaunchedEffect(ads,CasRuntime.ready,PurchaseAccess.active) {
        if(CasRuntime.ready) {ads?.prepareInterstitial();ads?.prepareRewarded()}
    }
    val context=LocalContext.current
    val store=remember {MelodyStore(context).also {it.migrateWork()}}
    val unlocks=remember {PortraitUnlockStore(context.applicationContext)}
    val lifecycleOwner=remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<LifecycleOwner>().firstOrNull()
    }
    LaunchedEffect(unlocks,lifecycleOwner) {
        suspend fun tick() { while(true) {unlocks.refreshTime();delay(1_000L)} }
        if(lifecycleOwner!=null) lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {tick()}
        else tick()
    }
    DisposableEffect(unlocks) {
        val stopObserving=unlocks.observe()
        onDispose {stopObserving()}
    }
    var lockedId by rememberSaveable {mutableStateOf<String?>(null)}
    var rewardInfo by rememberSaveable {mutableStateOf<String?>(null)}
    var editing by rememberSaveable {mutableStateOf(false)}
    var tab by rememberSaveable {mutableIntStateOf(0)}
    var exitRequest by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(exitRequest,ads) {
        val request=exitRequest
        if(request!=null) {
            fun finishExit() {
                if(exitRequest==request) {tab=0;editing=false;exitRequest=null}
            }
            if(ads==null) finishExit()
            else ads.showEditorExitInterstitial(request,onFinished={finishExit()})
        }
    }
    var templates by remember {mutableStateOf(emptyList<MusicTemplate>())}
    var error by remember {mutableStateOf<String?>(null)}
    var loading by remember {mutableStateOf(true)}
    fun openTemplate(template:MusicTemplate) {
        // Also guard the navigation boundary; browsing/previewing never grants access.
        if(!unlocks.canOpen(template.id)) {lockedId=template.id;return}
        store.beginWork(template.project,template.title);audio.stop();editing=true
    }
    LaunchedEffect(Unit) {
        try {
            val loaded=TemplateCatalog.load(context)
            unlocks.configureCatalog(loaded.map {it.id})
            templates=loaded
        }
        catch(e:Exception) {error="Could not load patterns"}
        finally {loading=false}
    }
    if(editing) MusicEditor(audio,onHome={if(exitRequest==null) exitRequest=UUID.randomUUID().toString()},
        exitPending=exitRequest!=null)
    else {
        var works by remember(editing) {mutableStateOf(store.workItems())}
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.weight(1f)) {
        HomeShell(tab,onTab={tab=it},onNew={
            store.beginWork(MelodyProject(),"Draw");audio.stop();editing=true
        }) {
            when(tab) {
                0 -> LazyVerticalGrid(columns=GridCells.Fixed(2),modifier=Modifier.fillMaxSize(),
                    contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),
                    verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    if(loading) item(span={GridItemSpan(maxLineSpan)}) {CircularProgressIndicator()}
                    if(error!=null) item(span={GridItemSpan(maxLineSpan)}) {Text(error!!)}
                    items(templates,key={it.id}) {template ->
                        Surface(shape=RoundedCornerShape(22.dp),color=Color.White,shadowElevation=1.dp) {
                            Column(Modifier.clickable {
                                if(unlocks.canOpen(template.id)) openTemplate(template)
                                else {lockedId=template.id;rewardInfo=null}
                            }) {
                                Box {
                                    TemplatePreview(template,Modifier.fillMaxWidth().aspectRatio(1.15f))
                                    if(!unlocks.canOpen(template.id)) Surface(
                                        modifier=Modifier.align(Alignment.TopEnd).padding(8.dp),
                                        color=Color(0xFF263F37),shape=RoundedCornerShape(12.dp)) {
                                        val adsLeft=unlocks.progressFor(template.id)?.remainingAds ?: 0
                                        Row(Modifier.padding(horizontal=8.dp,vertical=5.dp),
                                            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                            OrangeDiamondIcon(description="Watch $adsLeft ads to unlock this portrait")
                                            Text("$adsLeft",color=Color.White,fontSize=12.sp)
                                        }
                                    }
                                }
                                Text(uiText(template.title),fontSize=22.sp,modifier=Modifier.padding(14.dp),color=Color(0xFF263F37))
                            }
                        }
                    }
                }
                1 -> DrawingLibrary(works,onOpen={id ->if(store.openWork(id)) {audio.stop();editing=true}},
                    onChanged={works=store.workItems()})
                else -> AppSettings()
            }
        }
        }
        }
    }
    val lockedTemplate=templates.firstOrNull {it.id==lockedId}
    if(!editing && lockedTemplate!=null) {
        val portraitProgress=unlocks.progressFor(lockedTemplate.id)
        val alreadyUnlocked=unlocks.canOpen(lockedTemplate.id)
        val busy=ads?.rewardBusy==true
        UnlockPortraitPanel(
            portraitTitle=lockedTemplate.title,
            requiredAds=portraitProgress?.requiredAds ?: 0,
            watchedAds=portraitProgress?.watched ?: 0,
            unlocked=alreadyUnlocked,
            busy=busy,
            adReady=ads?.rewardReady==true,
            status=rewardInfo ?: if(!CasRuntime.ready) "Loading ad…"
                else ads?.rewardStatus ?: "Ad unavailable. Please try again later.",
            preview={modifier -> TemplatePreview(lockedTemplate,modifier)},
            onDismiss={if(!busy) {lockedId=null;rewardInfo=null}},
            onWatch={
                if(alreadyUnlocked) {lockedId=null;rewardInfo=null;openTemplate(lockedTemplate)}
                else {
                    audio.stop()
                    // Capture this portrait ID and a unique receipt before showing.
                    val rewardedPortrait=lockedTemplate.id
                    val receipt=UUID.randomUUID().toString()
                    val rewardStore=unlocks
                    rewardInfo=null
                    val started=ads?.showPortraitReward(grant={rewardStore.recordReward(rewardedPortrait,receipt)},finished={result ->
                        when(result) {
                            PortraitRewardResult.EARNED -> {
                                if(unlocks.canOpen(lockedTemplate.id)) {
                                    lockedId=null;rewardInfo=null;openTemplate(lockedTemplate)
                                } else rewardInfo="Ad counted for this portrait. Watch the remaining ads to unlock it."
                            }
                            PortraitRewardResult.CLOSED_WITHOUT_REWARD ->
                                rewardInfo="Ad closed without a reward. Your saved portrait progress is unchanged."
                            PortraitRewardResult.FAILED ->
                                rewardInfo="Ad unavailable. Please try again later."
                        }
                    })==true
                    if(!started) rewardInfo="Ad unavailable. Please try again later."
                }
            }
        )
    }
}

@Composable
private fun TemplatePreview(template: MusicTemplate,modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Color(0xFFFAFAF0))
        if(template.pixel) {
            val cells=visibleCells(template.project.strokes)
            if(cells.isNotEmpty()) {
                val minX=cells.minOf {it.x}-1;val maxX=cells.maxOf {it.x}+1
                val minY=cells.minOf {it.y}-1;val maxY=cells.maxOf {it.y}+1
                val unit=min(size.width/(maxX-minX+1),size.height/(maxY-minY+1))*.9f
                val left=(size.width-unit*(maxX-minX+1))/2
                val top=(size.height-unit*(maxY-minY+1))/2
                cells.forEach {cell ->
                    drawRoundRect(instruments[cell.instrument].color,
                        Offset(left+(cell.x-minX)*unit+unit*.03f,top+(cell.y-minY)*unit+unit*.03f),
                        Size(unit*.94f,unit*.94f),
                        CornerRadius(unit*.12f)
                    )
                }
            }
            return@Canvas
        }
        val padding=size.width*.08f;val width=size.width-2*padding;val height=size.height-2*padding
        template.lines.forEach {line ->
            val color=instruments[line.instrument].color
            val stroke=size.width*(if(template.beat) .018f else .021f)
            fun position(p: Offset)=Offset(padding+p.x*width,padding+p.y*height)
            if(line.points.size==1) drawCircle(color,stroke/2,position(line.points.first()))
            line.points.zipWithNext().forEach { (a,b) ->
                drawLine(color,position(a),position(b),strokeWidth=stroke,cap=StrokeCap.Round)
            }
        }
    }
}
