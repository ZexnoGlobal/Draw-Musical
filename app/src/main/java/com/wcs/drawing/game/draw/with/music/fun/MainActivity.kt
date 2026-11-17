package com.wcs.drawing.game.draw.with.music.`fun`

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import kotlin.math.*

private val Cream=Color(0xFFFFFEF8)
private val Forest=Color(0xFF263F37)
private val BoardCream=Color(0xFFF5F5ED)
private val Turquoise=Color(0xFF08BFB3)

class MainActivity: ComponentActivity() {
    private val audio by lazy { MelodyAudio(applicationContext) }
    private val ads by lazy { CasAdsController(this) }
    private val purchases by lazy {PurchaseController(applicationContext)}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PurchaseAccess.initialize(applicationContext)
        purchases.refresh()
        val launchPreferences=getSharedPreferences("draw_music_settings",MODE_PRIVATE)
        ads.configureAppOpenLaunch(launchPreferences.contains("language") &&
            launchPreferences.getBoolean("onboarding_complete",false))
        setContent {
            MaterialTheme(colorScheme=lightColorScheme(primary=Forest, background=Cream,
                surface=Cream, onSurface=Forest, onBackground=Forest)) {
                CompositionLocalProvider(LocalCasAds provides ads,LocalPurchases provides purchases) {
                    Surface(Modifier.fillMaxSize(), color=Cream) { StartupFlow(audio) }
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        DrawingReminders.appVisible=true
        DrawingReminders.scheduleNext(applicationContext)
    }
    override fun onResume() {super.onResume();purchases.onForeground()}
    override fun onStop() { purchases.onBackground();DrawingReminders.appVisible=false; audio.stop(); super.onStop() }
    override fun onDestroy() { purchases.close();ads.destroy(); audio.stop(); super.onDestroy() }
}

@Composable
fun MusicEditor(audio: MelodyAudio,onHome:()->Unit,exitPending:Boolean=false) {
    val context=LocalContext.current
    val store=remember { MelodyStore(context) }
    val instrumentAccess=rememberInstrumentUnlocks()
    val rootAccess=rememberRootNoteUnlocks()
    var rootUnlockIndex by rememberSaveable {mutableStateOf<Int?>(null)}
    var pendingRootAction by rememberSaveable {mutableStateOf<String?>(null)}
    var rootRewardInfo by rememberSaveable {mutableStateOf<String?>(null)}
    val ads=LocalCasAds.current
    var soundUnlockIndex by rememberSaveable {mutableStateOf<Int?>(null)}
    var pendingSoundAction by rememberSaveable {mutableStateOf<String?>(null)}
    var resumeSoundAction by rememberSaveable {mutableStateOf<String?>(null)}
    var soundRewardInfo by rememberSaveable {mutableStateOf<String?>(null)}
    val initial=remember { store.load("draft") ?: MelodyProject() }
    var drawing by remember {
        val cells=visibleCells(initial.strokes)
        mutableStateOf(DrawingState(cells,initial.visuals.ifEmpty {legacySmoothPaths(cells)}))
    }
    val board=drawing.cells
    var smooth by remember {mutableStateOf(initial.smooth)}
    var activePopup by remember {mutableStateOf("")}
    var backgroundIndex by remember {mutableIntStateOf(initial.backgroundIndex.coerceIn(0,canvasBackgrounds.lastIndex))}
    var backgroundPhoto by remember {mutableStateOf(initial.backgroundPhoto)}
    var backgroundOpacity by remember {mutableFloatStateOf(initial.backgroundOpacity)}
    var photoBitmap by remember {mutableStateOf<ImageBitmap?>(null)}
    var showBackgroundPicker by remember {mutableStateOf(false)}
    var backgroundDraft by remember {mutableStateOf<BackgroundSelection?>(null)}
    val displayBackground=backgroundDraft ?: BackgroundSelection(backgroundIndex,backgroundPhoto,backgroundOpacity)
    var preview by remember { mutableStateOf<DrawingState?>(null) }
    var undo by remember { mutableStateOf(listOf<DrawingState>()) }
    var redo by remember { mutableStateOf(listOf<DrawingState>()) }
    var tempo by remember { mutableIntStateOf(initial.tempo) }
    var root by remember { mutableIntStateOf(initial.root) }
    var scale by remember { mutableIntStateOf(initial.scale) }
    var backingSounds by remember {mutableStateOf(initial.backingSounds)}
    var selected by remember { mutableIntStateOf(initial.instrument) }
    var loop by remember { mutableStateOf(initial.loop) }
    var eraser by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var brush by remember { mutableIntStateOf(1) }
    var menu by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Draw, pick a sound, then press Play") }
    var playing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var pending by remember { mutableStateOf<MelodyProject?>(null) }
    var pendingLabel by remember { mutableStateOf("") }
    var overwriteSlot by remember { mutableStateOf<Int?>(null) }
    var showExportChoice by remember {mutableStateOf(false)}
    var exportJob by remember {mutableStateOf<Job?>(null)}
    val exportLibrary=remember {ExportLibrary(context.applicationContext)}
    val scope=rememberCoroutineScope()
    var showExitConfirmation by rememberSaveable {mutableStateOf(false)}
    var exportAdRequest by rememberSaveable {mutableStateOf<String?>(null)}
    var loopAdBusy by remember {mutableStateOf(false)}
    val busy=exporting || exportAdRequest!=null || loopAdBusy || exitPending || ads?.rewardBusy==true
    var preparedPhrase by remember {mutableStateOf<ShortArray?>(null)}
    val latestRepeat by rememberUpdatedState(loop)
    fun project()=MelodyProject(listOf(Stroke(board)),tempo,root,scale,loop,selected,visuals=drawing.paths,smooth=smooth,backgroundIndex=backgroundIndex,backgroundPhoto=backgroundPhoto,backgroundOpacity=backgroundOpacity,backingSounds=backingSounds)
    fun requestRoot(index:Int,action:String) {
        if(index !in rootNames.indices) return
        soundUnlockIndex=null;pendingSoundAction=null
        rootUnlockIndex=index;pendingRootAction=action;rootRewardInfo=null
    }
    fun chooseRoot(index:Int) {
        if(rootAccess.canUseNow(rootNames[index])) root=index
        else requestRoot(index,"SELECT")
    }
    fun requestSound(index:Int,action:String) {
        if(index !in instruments.indices) return
        soundUnlockIndex=index;pendingSoundAction=action;soundRewardInfo=null
    }
    fun firstLockedSound():Int?=board.asSequence().map {it.instrument}.distinct()
        .filter {it in instruments.indices}.firstOrNull {!instrumentAccess.canUseNow(instruments[it].name)}
    fun requireProjectSounds(action:String):Boolean {
        if(!rootAccess.canUseNow(rootNames[root])) {requestRoot(root,action);return true}
        val locked=firstLockedSound() ?: return false
        requestSound(locked,action)
        return true
    }
    fun chooseInstrument(index:Int) {
        if(instrumentAccess.canUseNow(instruments[index].name)) {selected=index;eraser=false}
        else requestSound(index,"SELECT")
    }
    fun edit(next: DrawingState) {
        if(next==drawing) return
        undo=(undo+listOf(drawing)).takeLast(80); redo=emptyList(); drawing=next
    }
    fun restore(p: MelodyProject) {
        val cells=visibleCells(p.strokes)
        edit(DrawingState(cells,p.visuals.ifEmpty {legacySmoothPaths(cells)}))
        smooth=p.smooth;backgroundIndex=p.backgroundIndex.coerceIn(0,canvasBackgrounds.lastIndex);tempo=p.tempo; root=p.root; scale=p.scale
        backgroundPhoto=p.backgroundPhoto;backgroundOpacity=p.backgroundOpacity;backingSounds=p.backingSounds
        loop=p.loop; selected=p.instrument; eraser=false
    }
    val selectedSoundAvailable=instruments.getOrNull(selected)?.let {instrumentAccess.canUse(it.name)}==true
    val lockedUsedSounds=board.map {it.instrument}.distinct().filter {it in instruments.indices &&
        !instrumentAccess.canUse(instruments[it].name)}
    LaunchedEffect(selected,selectedSoundAvailable) {
        if(!selectedSoundAvailable) {
            selected=0;preview=null
            status="Instrument access ended. Piano is selected; your notes are preserved."
        }
    }
    val selectedRootAvailable=rootAccess.canUse(rootNames[root])
    LaunchedEffect(playing,selectedRootAvailable,lockedUsedSounds) {
        if(playing && !selectedRootAvailable) {
            playJob?.cancel();audio.stop();playing=false;progress=-1f
            requestRoot(root,"PLAY")
        } else if(playing && lockedUsedSounds.isNotEmpty() && rootUnlockIndex==null) {
            playJob?.cancel();audio.stop();playing=false;progress=-1f
            requestSound(lockedUsedSounds.first(),"PLAY")
        }
    }
    LaunchedEffect(drawing,smooth,backgroundIndex,backgroundPhoto,backgroundOpacity,tempo,root,scale,loop,selected,backingSounds) { store.save("draft",project()) }
    LaunchedEffect(playing,board,tempo,root,scale,backingSounds,loop) {
        if(playing) {
            try {
                val notes=board.toList();val bpm=tempo;val key=root;val scaleChoice=scale
                val backing=backingSounds.toSet();val wrap=loop
                preparedPhrase=withContext(Dispatchers.Default) {
                    audio.render(notes,bpm,key,scaleChoice,looping=wrap,backingSounds=backing)
                }
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {Log.e("DrawMusicAudio","Next loop failed",e);status="Could not update playback. Try again."}
        }
    }
    DisposableEffect(Unit) { onDispose { audio.stop() } }
    fun returnHome() {
        if(exporting || exitPending) return
        showExitConfirmation=true
    }
    fun confirmExit() {
        if(exporting || exitPending) return
        try {store.save("draft",project())}
        catch(e:Exception) {
            showExitConfirmation=false
            status="Could not save your drawing. Please try again."
            Log.e("DrawMusicDraft","Exit save failed",e)
            return
        }
        showExitConfirmation=false
        playJob?.cancel();audio.stop();playing=false;progress=-1f
        onHome()
    }
    BackHandler {if(!exporting && !exitPending) returnHome()}
    if(showExitConfirmation && !exitPending) ExitDrawingPanel(
        onBack={confirmExit()},onStay={showExitConfirmation=false})

    LaunchedEffect(displayBackground.photoId) {
        photoBitmap=null
        displayBackground.photoId?.let {id ->
            try {photoBitmap=withContext(Dispatchers.IO) {loadBuiltInBackground(context,id)}}
            catch(e: CancellationException) {throw e}
            catch(e: Exception) {status="Background unavailable"}
        }
    }
    if(showBackgroundPicker) {
        BackgroundPickerSheet(
            initial=BackgroundSelection(backgroundIndex,backgroundPhoto,backgroundOpacity),
            onPreview={backgroundDraft=it},
            onApply={choice ->
                backgroundIndex=choice.colorIndex;backgroundPhoto=choice.photoId;backgroundOpacity=choice.opacity
                backgroundDraft=null;showBackgroundPicker=false;status="Background applied"
            },
            onDismiss={backgroundDraft=null;showBackgroundPicker=false})
    }

    fun startExport(kind:ExportKind) {
        if(busy) return
        if(requireProjectSounds("EXPORT:${kind.name}")) {showExportChoice=false;return}
        val snapshot=project()
        showExportChoice=false;exporting=true
        exportJob=scope.launch {
            var output: File?=null
            var completed=false
            var savedSuccessfully=false
            try {
                status="Preparing ${kind.extension.uppercase()}…"
                val entry=withContext(Dispatchers.Default) {
                    val file=exportLibrary.createFile(kind);output=file
                    if(kind==ExportKind.MIDI) file.outputStream().use {MidiExporter.write(snapshot,it)}
                    else {
                        val pcm=audio.render(visibleCells(snapshot.strokes),snapshot.tempo,snapshot.root,snapshot.scale,
                            backingSounds=snapshot.backingSounds,includeReleaseTail=true)
                        if(kind==ExportKind.WAV) file.outputStream().use {writeMelodyWav(it,pcm)}
                        else VideoExporter.write(context.applicationContext,snapshot,pcm,file) {percent ->
                            scope.launch {if(exporting) status="Creating MP4… $percent%"}
                        }
                    }
                    ensureActive()
                    exportLibrary.add(file,kind,snapshot).also {completed=true}
                }
                status=if(entry.publicUri!=null) "${kind.extension.uppercase()} saved to Library and device"
                    else "${kind.extension.uppercase()} saved to Library • use Save a copy for other apps"
                savedSuccessfully=true
            } catch(e:CancellationException) {status="Export cancelled";throw e}
            catch(e:Exception) {
                Log.e("DrawMusicExport","Export failed",e);status="Export failed. Please try again."}
            finally {if(!completed) output?.delete();exporting=false}
            if(savedSuccessfully && isActive) {
                playJob?.cancel();audio.stop();playing=false;progress=-1f
                exportAdRequest=UUID.randomUUID().toString()
            }
        }
    }
    LaunchedEffect(exportAdRequest,ads) {
        val request=exportAdRequest
        if(request!=null) {
            fun finishExportAd() {if(exportAdRequest==request) exportAdRequest=null}
            if(ads==null) finishExportAd()
            else ads.showExportInterstitial(request,onFinished={finishExportAd()})
        }
    }
    if(showExportChoice) ExportChoiceDialog(onDismiss={showExportChoice=false},onChoose={startExport(it)})
    if(exporting) AlertDialog(onDismissRequest={},title={Text(uiText("Exporting"))},
        text={Column {CircularProgressIndicator();Text(status,modifier=Modifier.padding(top=12.dp))}},
        confirmButton={TextButton(onClick={exportJob?.cancel()}) {Text(uiText("Cancel export"))}})

    fun playOrStop() {
        if(playing) { playJob?.cancel(); audio.stop(); return }
        if(board.isEmpty() || busy) return
        if(requireProjectSounds("PLAY")) return
        val snapshot=project()
        preparedPhrase=null
        playing=true
        playJob=scope.launch {
            try {
                status="Preparing sound…"
                val generation=audio.generation
                val pcm=withContext(Dispatchers.Default) {
                    audio.render(boardSnapshot(snapshot),snapshot.tempo,snapshot.root,snapshot.scale,
                        looping=snapshot.loop,backingSounds=snapshot.backingSounds)
                }
                if(audio.generation!=generation) return@launch
                status="Playing • edits apply on the next loop"
                var phrase=pcm
                while(isActive) {
                    val fiveLoops=audio.stream(phrase,nextPhrase={preparedPhrase ?: phrase},repeat={latestRepeat},
                        onProgress={progress=it},loopLimit=if(PurchaseAccess.active) null else 5)
                    if(!fiveLoops || !latestRepeat) break
                    // The track is drained and released before showing any ad.
                    if(PurchaseAccess.fullScreenAllowed && !exporting && !exitPending && ads?.rewardBusy!=true) {
                        loopAdBusy=true
                        try {
                            suspendCancellableCoroutine<Unit> {continuation ->
                                val request=UUID.randomUUID().toString()
                                if(ads==null) continuation.resumeWith(Result.success(Unit))
                                else ads.showLoopInterstitial(request,onFinished={
                                    if(continuation.isActive) continuation.resumeWith(Result.success(Unit))
                                })
                            }
                            // Dismissal may precede Activity resume; never restart in the background.
                            val owner=generateSequence(context) {(it as? android.content.ContextWrapper)?.baseContext}
                                .filterIsInstance<androidx.lifecycle.LifecycleOwner>().firstOrNull()
                            while(isActive && owner!=null && !owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                                if(owner.lifecycle.currentState==androidx.lifecycle.Lifecycle.State.DESTROYED) return@launch
                                delay(50)
                            }
                        } finally {loopAdBusy=false}
                    }
                    currentCoroutineContext().ensureActive()
                    if(!latestRepeat || exitPending || requireProjectSounds("PLAY")) break
                    phrase=preparedPhrase ?: phrase
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { Log.e("DrawMusicAudio","Playback failed",e); status="Playback failed. Please try again." }
            finally {
                audio.stop(); playing=false; progress=-1f;loopAdBusy=false
                if(!exporting && !status.startsWith("Playback failed")) status="Ready • ${board.size} colored notes"
            }
        }
    }

    var previousPaidAccess by remember {mutableStateOf(PurchaseAccess.active)}
    LaunchedEffect(PurchaseAccess.active) {
        if(previousPaidAccess && !PurchaseAccess.active && playing) {
            playJob?.cancel();audio.stop();playing=false;progress=-1f
        }
        previousPaidAccess=PurchaseAccess.active
    }

    LaunchedEffect(resumeSoundAction) {
        val action=resumeSoundAction
        resumeSoundAction=null
        if(action=="PLAY") playOrStop()
        else if(action?.startsWith("EXPORT:")==true) {
            ExportKind.values().firstOrNull {it.name==action.substringAfter(":")}?.let {startExport(it)}
        }
    }
    val keyUnlockIndex=rootUnlockIndex?.takeIf {it in rootNames.indices}
    if(keyUnlockIndex!=null && !exitPending && !exporting) {
        val keyName=rootNames[keyUnlockIndex]
        val available=rootAccess.canUse(keyName)
        fun finishRootUnlock() {
            val action=pendingRootAction
            rootUnlockIndex=null;pendingRootAction=null;rootRewardInfo=null
            if(action=="SELECT") root=keyUnlockIndex else resumeSoundAction=action
        }
        UnlockPortraitPanel(portraitTitle=keyName,requiredAds=1,watchedAds=if(available) 1 else 0,
            unlocked=available,busy=ads?.rewardBusy==true,adReady=ads?.rewardReady==true,
            status=rootRewardInfo ?: if(!CasRuntime.ready) "Loading ad…"
                else ads?.rewardStatus ?: "Ad unavailable. Please try again later.",
            panelTitle=if(available) "Key unlocked" else "Unlock key",
            accessLabel="1-hour access",
            introText=if(available) "Your key is ready to use." else "Watch one rewarded ad to use this key for 1 hour.",
            detailText="Only this key unlocks. Its timer starts when the reward is earned; after one hour, watch one ad to unlock it again.",
            actionLabel=if(available) "Use key" else "Watch ad",
            preview={modifier -> Box(modifier.background(Color(0xFFE6F4EF)),contentAlignment=Alignment.Center) {
                Text(keyName,fontSize=42.sp,fontWeight=FontWeight.Bold,color=Color(0xFF263F37))
            }},
            onDismiss={if(ads?.rewardBusy!=true) {rootUnlockIndex=null;pendingRootAction=null;rootRewardInfo=null}},
            onWatch={
                if(rootAccess.canUseNow(keyName)) finishRootUnlock()
                else {
                    playJob?.cancel();audio.stop();playing=false;progress=-1f
                    val receipt=UUID.randomUUID().toString()
                    val rewardStore=rootAccess
                    rootRewardInfo=null
                    val started=ads?.showRootNoteReward(grant={rewardStore.grant(keyName,receipt)},finished={result ->
                        when(result) {
                            PortraitRewardResult.EARNED -> finishRootUnlock()
                            PortraitRewardResult.CLOSED_WITHOUT_REWARD -> rootRewardInfo="Ad closed without a reward. This key is still locked."
                            PortraitRewardResult.FAILED -> rootRewardInfo="Ad unavailable. Please try again later."
                        }
                    })==true
                    if(!started) rootRewardInfo="Ad unavailable. Please try again later."
                }
            })
    }
    val unlockIndex=soundUnlockIndex?.takeIf {it in instruments.indices}
    if(unlockIndex!=null && !exitPending && !exporting) {
        val sound=instruments[unlockIndex]
        val available=instrumentAccess.canUse(sound.name)
        fun finishSoundUnlock() {
            val action=pendingSoundAction
            soundUnlockIndex=null;pendingSoundAction=null;soundRewardInfo=null
            if(action=="SELECT") {selected=unlockIndex;eraser=false}
            else resumeSoundAction=action
        }
        UnlockPortraitPanel(portraitTitle=sound.name,requiredAds=1,watchedAds=if(available) 1 else 0,
            unlocked=available,busy=ads?.rewardBusy==true,adReady=ads?.rewardReady==true,
            status=soundRewardInfo ?: if(!CasRuntime.ready) "Loading ad…"
                else ads?.rewardStatus ?: "Ad unavailable. Please try again later.",
            panelTitle=if(available) "Instrument unlocked" else "Unlock instrument",
            accessLabel="1-hour access",
            introText=if(available) "Your instrument is ready to use." else "Watch one rewarded ad to use this instrument for 1 hour.",
            detailText="Only this instrument unlocks. Its timer starts when the reward is earned; after one hour, watch one ad to unlock it again.",
            actionLabel=if(available) "Use instrument" else "Watch ad",
            preview={modifier -> Box(modifier.background(sound.color),contentAlignment=Alignment.Center) {
                InstrumentSymbol(unlockIndex,Modifier.size(72.dp))
            }},
            onDismiss={if(ads?.rewardBusy!=true) {soundUnlockIndex=null;pendingSoundAction=null;soundRewardInfo=null}},
            onWatch={
                if(instrumentAccess.canUseNow(sound.name)) finishSoundUnlock()
                else {
                    playJob?.cancel();audio.stop();playing=false;progress=-1f
                    val name=sound.name
                    val receipt=UUID.randomUUID().toString()
                    val rewardStore=instrumentAccess
                    soundRewardInfo=null
                    val started=ads?.showInstrumentReward(grant={rewardStore.grant(name,receipt)},finished={result ->
                        when(result) {
                            PortraitRewardResult.EARNED -> finishSoundUnlock()
                            PortraitRewardResult.CLOSED_WITHOUT_REWARD -> soundRewardInfo="Ad closed without a reward. This instrument is still locked."
                            PortraitRewardResult.FAILED -> soundRewardInfo="Ad unavailable. Please try again later."
                        }
                    })==true
                    if(!started) soundRewardInfo="Ad unavailable. Please try again later."
                }
            })
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=12.dp,vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically) {
            Tile(if(LocalAppLanguage.current.rtl) "›" else "‹","Back to home",compact=true,enabled=!busy,onClick={returnHome()})
            Surface(Modifier.weight(1f).height(50.dp),shape=RoundedCornerShape(18.dp),color=Color.White,
                shadowElevation=2.dp) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
                    TextButton(enabled=!busy,onClick={tempo=(tempo-5).coerceAtLeast(40)},contentPadding=PaddingValues(0.dp),modifier=Modifier.size(32.dp)) { Text(uiText("−"),fontSize=28.sp) }
                    Text("$tempo",fontWeight=FontWeight.Bold,fontSize=24.sp)
                    TextButton(enabled=!busy,onClick={tempo=(tempo+5).coerceAtMost(240)},contentPadding=PaddingValues(0.dp),modifier=Modifier.size(32.dp)) { Text(uiText("+"),fontSize=26.sp) }
                }
            }
            GlyphTile("background","Change canvas background",enabled=!busy,compact=true,
                onClick={activePopup="";showBackgroundPicker=true})
            Button(enabled=!busy && board.isNotEmpty(),onClick={
                showExportChoice=true
            },shape=RoundedCornerShape(24.dp),modifier=Modifier.height(50.dp),contentPadding=PaddingValues(horizontal=10.dp)) { Text(uiText("Export"),fontWeight=FontWeight.Bold) }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Surface(Modifier.weight(1f),shape=RoundedCornerShape(18.dp),color=Color.White,shadowElevation=1.dp) {
                Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                    Tile("✎","Draw",selected=!eraser,enabled=!busy,onClick={eraser=false},raised=false)
                    Tile("◇","Eraser",selected=eraser,enabled=!busy,onClick={eraser=true},raised=false)
                    Box {
                        GlyphTile(if(smooth) "smooth" else "pixel","Pixel or smooth drawing",enabled=!busy,onClick={activePopup="Style"})
                        DropdownMenu(expanded=activePopup=="Style",onDismissRequest={activePopup=""},modifier=Modifier.width(58.dp)) {
                            Column(Modifier.padding(horizontal=6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                GlyphTile("smooth","Smooth drawing",selected=smooth,onClick={smooth=true;activePopup=""})
                                GlyphTile("pixel","Pixel drawing",selected=!smooth,onClick={smooth=false;activePopup=""})
                            }
                        }
                    }
                    Box {
                        Tile("♪","Backing sounds",enabled=!busy,onClick={activePopup="Backing"},raised=false)
                        DropdownMenu(expanded=activePopup=="Backing",onDismissRequest={activePopup=""},
                            modifier=Modifier.width(210.dp).heightIn(max=380.dp)) {
                            backingOptions.forEach {option ->
                                val checked=option.id in backingSounds
                                DropdownMenuItem(
                                    text={Text(uiText(option.title),color=if(checked) Forest else Forest.copy(alpha=.55f))},
                                    leadingIcon={Text(option.symbol,fontWeight=FontWeight.Bold)},
                                    trailingIcon={if(checked) Text(uiText("✓"))},
                                    modifier=Modifier.background(if(checked) Forest.copy(alpha=.08f) else Color.Transparent),
                                    onClick={backingSounds=if(checked) backingSounds-option.id else backingSounds+option.id})
                            }
                        }
                    }
                    Box {
                        Box {
                            Tile(rootNames[root],"Root note",enabled=!busy,onClick={activePopup="Root"},raised=false)
                            if(!selectedRootAvailable) OrangeDiamondIcon(Modifier.align(Alignment.TopEnd).size(14.dp))
                        }
                        DropdownMenu(expanded=activePopup=="Root",onDismissRequest={activePopup=""}) {
                            rootNames.forEachIndexed {index,name ->
                                val accessLabel=if(!PurchaseAccess.active && !rootAccess.isFree(name) && rootAccess.canUse(name)) " · ${rootAccess.remainingMinutes(name)}m" else ""
                                DropdownMenuItem(text={Text((if(root==index) "✓ $name" else name)+accessLabel)},
                                    trailingIcon={if(!rootAccess.canUse(name)) OrangeDiamondIcon()},
                                    onClick={activePopup="";chooseRoot(index)})
                            }
                            HorizontalDivider()
                            scaleNames.forEachIndexed {index,name ->
                                DropdownMenuItem(text={Text(if(scale==index) "✓ $name" else name)},onClick={scale=index;activePopup=""})
                            }
                        }
                    }
                    Box {
                        Tile("✦","Starter patterns",enabled=!busy,onClick={activePopup="Patterns"},raised=false)
                        DropdownMenu(expanded=activePopup=="Patterns",onDismissRequest={activePopup=""}) {
                            listOf("Rising","Mountain","Sparkles","Pixel flower").forEachIndexed {index,name ->
                                DropdownMenuItem(text={Text(name)},onClick={
                                    val notes=starterPattern(index)
                                    edit(DrawingState(notes,legacySmoothPaths(notes)))
                                    status="Loaded $name • Undo is available";activePopup=""
                                })
                            }
                        }
                    }
                    Tile("⋯","Save, load, and settings",enabled=!busy,onClick={menu="Projects"},raised=false)
                }
            }
            Tile("↻","Clear drawing",enabled=!busy && (board.isNotEmpty() || drawing.paths.isNotEmpty()),onClick={
                edit(DrawingState(emptyList(),emptyList()));status="Drawing cleared • Undo is available"
            })
        }
        DrawingBoard(preview ?: drawing,progress,showGrid,smooth,Modifier.fillMaxWidth().weight(1f),
            enabled=!busy,selected=selected,eraser=eraser,brush=brush,
            onPreview={if(eraser || instrumentAccess.canUseNow(instruments[selected].name)) preview=it},
            onCommit={
                if(eraser || instrumentAccess.canUseNow(instruments[selected].name)) edit(it)
                else requestSound(selected,"SELECT")
                preview=null
            },backgroundColor=canvasBackgrounds[displayBackground.colorIndex].color,backgroundImage=photoBitmap,backgroundOpacity=displayBackground.opacity)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(shape=RoundedCornerShape(18.dp),shadowElevation=2.dp,color=Color.White) {
                Row {
                    Tile("↶","Undo",enabled=!busy && undo.isNotEmpty(),raised=false,onClick={
                        redo=(redo+listOf(drawing)).takeLast(80); drawing=undo.last(); undo=undo.dropLast(1)
                    })
                    Tile("↷","Redo",enabled=!busy && redo.isNotEmpty(),raised=false,onClick={
                        undo=(undo+listOf(drawing)).takeLast(80); drawing=redo.last(); redo=redo.dropLast(1)
                    })
                }
            }
            Surface(Modifier.size(62.dp).semantics { contentDescription=if(playing) "Stop playback" else "Play melody" }
                .clip(CircleShape).clickable(enabled=playing || (!exporting && board.isNotEmpty())) { playOrStop() },
                shape=CircleShape,color=Turquoise) {
                Box(contentAlignment=Alignment.Center) { Text(if(playing) "■" else "▶",color=Color.White,fontSize=29.sp) }
            }
            Spacer(Modifier.weight(1f))
            TextButton(enabled=!busy,onClick={loop=!loop}) {Text(if(loop) "↻ Loop" else "↻ Once",fontSize=12.sp)}
        }
        Surface(shape=RoundedCornerShape(20.dp),color=Color(0xFFEEEEEC)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                instruments.forEachIndexed { index, instrument ->
                    Column(Modifier.width(58.dp).semantics { contentDescription="${instrument.name} instrument" }
                        .clip(RoundedCornerShape(14.dp)).clickable(enabled=!busy) {chooseInstrument(index)},
                        horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.size(54.dp).border(if(selected==index) 2.dp else 0.dp,
                            if(selected==index) Forest else Color.Transparent,RoundedCornerShape(15.dp)).padding(4.dp)) {
                            Box(Modifier.fillMaxSize().background(instrument.color,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center) {
                                InstrumentSymbol(index,Modifier.size(32.dp))
                            }
                            if(!PurchaseAccess.active && !instrumentAccess.isFree(instrument.name)) {
                                if(instrumentAccess.canUse(instrument.name)) Surface(
                                    modifier=Modifier.align(Alignment.TopEnd),shape=RoundedCornerShape(6.dp),color=Turquoise) {
                                    Text("${instrumentAccess.remainingMinutes(instrument.name)}m",fontSize=8.sp,color=Forest,
                                        modifier=Modifier.padding(horizontal=3.dp,vertical=1.dp))
                                } else OrangeDiamondIcon(Modifier.align(Alignment.TopEnd).size(18.dp))
                            }
                        }
                        Text(uiText(instrument.name),fontSize=9.sp,maxLines=1,color=Forest)
                    }
                }
            }
        }
        if(status.startsWith("Could not") || status.startsWith("Export failed") ||
            status.startsWith("Playback failed") || status=="Background unavailable")
            Text(status,fontSize=11.sp,color=MaterialTheme.colorScheme.error,maxLines=2,modifier=Modifier.padding(horizontal=4.dp))
        CasBanner("editor_banner")
    }

    if(menu.isNotEmpty()) AlertDialog(onDismissRequest={menu=""},title={Text(menu)},
        text={ Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            when(menu) {
                "Root" -> rootNames.forEachIndexed { index,name ->
                    TextButton(onClick={menu="";chooseRoot(index)}) {
                        Text(if(root==index) "✓ $name" else name)
                        if(!rootAccess.canUse(name)) OrangeDiamondIcon(Modifier.padding(start=6.dp))
                    }
                }
                "Scale" -> scaleNames.forEachIndexed { index,name ->
                    TextButton(onClick={scale=index;menu=""}) { Text(if(scale==index) "✓ $name" else name) }
                }
                "Canvas" -> {
                    Row(verticalAlignment=Alignment.CenterVertically) { Text(uiText("Show grid ")); Switch(showGrid,{showGrid=it}) }
                    Text(uiText("Brush size"))
                    Row { (1..3).forEach { size -> TextButton(onClick={brush=size}) { Text(if(brush==size) "✓ $size" else "$size") } } }
                }
                "Patterns" -> listOf("Rising","Mountain","Sparkles","Pixel flower").forEachIndexed { index,name ->
                    OutlinedButton(onClick={val notes=starterPattern(index)
                            pending=project().copy(strokes=listOf(Stroke(notes)),visuals=legacySmoothPaths(notes)); pendingLabel=name;menu=""}) { Text(name) }
                }
                else -> {
                    for(slot in 1..3) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={
                            if(store.load("slot$slot")==null) { store.save("slot$slot",project());status="Saved slot $slot" }
                            else { overwriteSlot=slot;menu="" }
                        }) { Text("Save $slot") }
                        OutlinedButton(onClick={
                            val loaded=store.load("slot$slot")
                            if(loaded==null) status="Slot $slot is empty or unreadable"
                            else {pending=loaded;pendingLabel="slot $slot";menu=""}
                        }) { Text("Load $slot") }
                    }
                    Text("Tempo: $tempo BPM")
                    Slider(tempo.toFloat(),{tempo=it.roundToInt()},valueRange=40f..240f)
                    Text("Selected sound: ${instruments[selected].name}")
                    HorizontalDivider()
                    Text(uiText("Canvas settings"))
                    Row(verticalAlignment=Alignment.CenterVertically) {Text(uiText("Show grid "));Switch(showGrid,{showGrid=it})}
                    Row { (1..3).forEach {size -> TextButton(onClick={brush=size}) {Text(if(brush==size) "✓ Brush $size" else "Brush $size")} } }
                }
            }
        } },confirmButton={TextButton(onClick={menu=""}){Text(uiText("Done"))}})
    if(pending!=null) Confirm("Load $pendingLabel?","This replaces the current drawing. Save it to a slot first if you want a separate copy.",
        onCancel={pending=null},onConfirm={pending?.let {restore(it)};pending=null;status="Loaded $pendingLabel"})
    if(overwriteSlot!=null) Confirm("Overwrite slot $overwriteSlot?","Replace the saved drawing in this slot?",
        onCancel={overwriteSlot=null},onConfirm={store.save("slot${overwriteSlot!!}",project());status="Saved slot $overwriteSlot";overwriteSlot=null})
}

private fun boardSnapshot(p: MelodyProject)=visibleCells(p.strokes)

@Composable
private fun Confirm(title: String,body: String,onCancel:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest=onCancel,title={Text(uiText(title))},text={Text(body)},
        confirmButton={TextButton(onClick=onConfirm){Text(uiText("Continue"))}},
        dismissButton={TextButton(onClick=onCancel){Text(uiText("Cancel"))}})
}

@Composable
private fun Tile(label: String,description: String,selected: Boolean=false,enabled: Boolean=true,
    raised: Boolean=true,compact: Boolean=false,onClick:()->Unit) {
    Surface(Modifier.size(if(compact) 34.dp else 46.dp).semantics {contentDescription=description}.clip(RoundedCornerShape(16.dp))
        .clickable(enabled=enabled,onClick=onClick),shape=RoundedCornerShape(16.dp),
        color=if(selected) Color(0xFFE9ECE7) else Color.White,shadowElevation=if(raised) 1.dp else 0.dp) {
        Box(contentAlignment=Alignment.Center) {Text(uiText(label),color=Forest.copy(alpha=if(enabled) 1f else .3f),fontSize=24.sp,fontWeight=FontWeight.Medium)}
    }
}

