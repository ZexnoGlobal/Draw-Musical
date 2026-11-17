package com.wcs.drawing.game.draw.with.music.`fun`

import android.content.Intent
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HomeForest=Color(0xFF263F37)
private val HomeTurquoise=Color(0xFF08BFB3)

@Composable
fun HomeShell(tab:Int,onTab:(Int)->Unit,onNew:()->Unit,content:@Composable ()->Unit) {
    BackHandler(enabled=tab!=0) {onTab(0)}
    Column(Modifier.fillMaxSize().safeDrawingPadding().background(Color(0xFFFFFEF8))) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp)) {
            val brandSize=if(maxWidth<300.dp) 30.sp else 34.sp
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) {
                    Text("Draw ",fontSize=brandSize,fontStyle=FontStyle.Italic,fontWeight=FontWeight.Bold,color=HomeForest,maxLines=1)
                    Text("Musical",fontSize=brandSize,fontStyle=FontStyle.Italic,fontWeight=FontWeight.Bold,color=HomeTurquoise,maxLines=1)
                }
                if(tab==0) PurchaseSettingsCard(modifier=Modifier.size(52.dp),compact=true)
            }
        }
        }
        Box(Modifier.weight(1f)) {content()}
        Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Surface(modifier=Modifier.weight(1f),shape=RoundedCornerShape(40.dp),color=Color.White,shadowElevation=5.dp) {
                Row(Modifier.padding(7.dp).height(62.dp),verticalAlignment=Alignment.CenterVertically) {
                    listOf("Home","Library","Setting").forEachIndexed {index,label ->
                        Surface(modifier=Modifier.weight(if(tab==index) 2.1f else 1f).fillMaxHeight(),
                            shape=RoundedCornerShape(32.dp),color=if(tab==index) Color(0xFFF0F0F0) else Color.White,
                            onClick={onTab(index)}) {
                            Row(horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                                Text(listOf("⌂","▣","⚙")[index],fontSize=26.sp,color=HomeForest)
                                if(tab==index) Text(" ${uiText(label)}",fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=HomeForest)
                            }
                        }
                    }
                }
            }
            Button(onClick=onNew,modifier=Modifier.size(70.dp),shape=CircleShape,contentPadding=PaddingValues(0.dp),
                colors=ButtonDefaults.buttonColors(containerColor=HomeTurquoise)) {
                Text(uiText("+"),fontSize=36.sp,color=Color.White)
            }
        }
        // One shared banner below navigation, retained across Home/Library/Settings.
        CasBanner("home_banner")
    }
}

@Composable
fun DrawingLibrary(items:List<SavedDrawing>,onOpen:(String)->Unit,onChanged:()->Unit={}) {
    val context=LocalContext.current
    val repository=remember {ExportLibrary(context.applicationContext)}
    val store=remember {MelodyStore(context.applicationContext)}
    var exports by remember {mutableStateOf(repository.list())}
    var drafts by remember(items) {mutableStateOf(items)}
    var deleteIds by rememberSaveable {mutableStateOf(emptyList<String>())}
    var selectedIds by rememberSaveable {mutableStateOf(emptyList<String>())}
    var selectionMode by rememberSaveable {mutableStateOf(false)}
    var deleteIsExport by rememberSaveable {mutableStateOf(false)}
    var deleting by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    var showExports by rememberSaveable {mutableStateOf(false)}
    var info by remember {mutableStateOf<String?>(null)}
    var copying by remember {mutableStateOf(false)}
    var playingExport by remember {mutableStateOf<ExportedFile?>(null)}
    var pendingCopy by rememberSaveable {mutableStateOf<String?>(null)}
    val copyLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {result ->
        val entry=exports.firstOrNull {it.id==pendingCopy};pendingCopy=null
        val uri=result.data?.data
        if(result.resultCode==Activity.RESULT_OK && uri!=null && entry!=null) scope.launch {
            copying=true
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri,"w")).use {output ->
                        repository.file(entry).inputStream().use {it.copyTo(output)}
                    }
                }
                info="Copy saved"
            } catch(e:kotlinx.coroutines.CancellationException) {throw e}
            catch(e:Exception) {info="Could not save a copy. Please try again."}
            finally {copying=false}
        }
    }
    fun toggleSelection(id:String) {
        selectedIds=if(id in selectedIds) selectedIds-id else selectedIds+id
    }
    fun clearSelection() {selectionMode=false;selectedIds=emptyList()}
    BackHandler(enabled=selectionMode && deleteIds.isEmpty() && !deleting) {clearSelection()}
    fun openExternal(entry:ExportedFile) {
        try {repository.open(entry)}
        catch(e:android.content.ActivityNotFoundException) {info="No compatible app found. Try Play here or Save a copy."}
        catch(e:IllegalArgumentException) {info="Could not share this file. Try Save a copy."}
        catch(e:SecurityException) {info="File access denied. Try Save a copy."}
        catch(e:Exception) {info="Could not open this file."}
    }
    Column(Modifier.fillMaxSize()) {
        Surface(modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),shape=RoundedCornerShape(36.dp),color=HomeForest) {
            Row(Modifier.padding(5.dp).height(48.dp)) {
                listOf("Draft","Library").forEachIndexed {index,title ->
                    val selected=showExports==(index==1)
                    Surface(modifier=Modifier.weight(1f).fillMaxHeight(),shape=RoundedCornerShape(30.dp),
                        color=if(selected) Color(0xFFFFFEF8) else HomeForest,onClick={if(!deleting) {showExports=index==1;clearSelection()}}) {
                        Box(contentAlignment=Alignment.Center) {Text(uiText(title),color=if(selected) HomeForest else Color.White,
                            fontWeight=FontWeight.Bold)}
                    }
                }
            }
        }
        val visibleIds=if(showExports) exports.map {it.id} else drafts.map {it.id}
        LaunchedEffect(visibleIds) {selectedIds=selectedIds.filter {it in visibleIds}}
        if(visibleIds.isNotEmpty()) Surface(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),
            shape=RoundedCornerShape(18.dp),color=Color(0xFFF0F4EE)) {
            Column(Modifier.padding(horizontal=8.dp,vertical=4.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(selectionMode) "${selectedIds.size} selected" else "",
                        modifier=Modifier.weight(1f),color=HomeForest)
                    TextButton(enabled=!deleting && !copying && pendingCopy==null,onClick={
                        if(selectionMode) clearSelection() else selectionMode=true
                    }) {Text(if(selectionMode) "Cancel" else "Select",color=HomeForest)}
                }
                if(selectionMode) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(enabled=!deleting,onClick={
                        selectedIds=if(selectedIds.size==visibleIds.size) emptyList() else visibleIds
                    },modifier=Modifier.weight(1f)) {
                        Text(if(selectedIds.size==visibleIds.size) "Deselect all" else "Select all",color=HomeForest)
                    }
                    Button(enabled=selectedIds.isNotEmpty() && !deleting && !copying && pendingCopy==null,
                        onClick={deleteIsExport=showExports;deleteIds=selectedIds.toList()},
                        shape=RoundedCornerShape(24.dp),colors=ButtonDefaults.buttonColors(containerColor=HomeTurquoise)) {
                        Text("Delete (${selectedIds.size})")
                    }
                }
            }
        }
        if(!showExports) {
            if(drafts.isEmpty()) Box(Modifier.fillMaxSize().padding(28.dp),contentAlignment=Alignment.Center) {
                Text(uiText("No drafts yet"),color=HomeForest)
            } else LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(12.dp),
                horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                items(drafts,key={it.id}) {entry ->
                    var menu by remember {mutableStateOf(false)}
                    Surface(shape=RoundedCornerShape(22.dp),color=Color.White,shadowElevation=1.dp,
                        border=BorderStroke(if(entry.id in selectedIds) 2.dp else 1.dp,
                            if(entry.id in selectedIds) HomeTurquoise else Color(0xFFE9E9E2))) {
                        Column(Modifier.clickable(enabled=!deleting) {if(selectionMode) toggleSelection(entry.id) else onOpen(entry.id)}.padding(7.dp)) {
                            Box {
                                SavedDrawingPreview(entry.project,Modifier.fillMaxWidth().aspectRatio(.9f))
                                if(selectionMode) Checkbox(checked=entry.id in selectedIds,
                                    onCheckedChange={toggleSelection(entry.id)},enabled=!deleting,
                                    modifier=Modifier.align(Alignment.TopStart).background(Color.White.copy(alpha=.9f),CircleShape),
                                    colors=CheckboxDefaults.colors(checkedColor=HomeTurquoise))
                                if(!selectionMode) Box(Modifier.align(Alignment.TopEnd)) {
                                    TextButton(enabled=!deleting && !copying,onClick={menu=true},
                                        contentPadding=PaddingValues(0.dp),modifier=Modifier.size(48.dp)) {Text("⋮",fontSize=24.sp)}
                                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                                        DropdownMenuItem(text={Text("Delete",color=Color(0xFFB94F2D))},
                                            onClick={menu=false;deleteIsExport=false;deleteIds=listOf(entry.id)})
                                    }
                                }
                            }
                            Text("✎ ${entry.title}",fontSize=16.sp,color=HomeForest,maxLines=1,modifier=Modifier.padding(top=10.dp,start=4.dp))
                            Text(SimpleDateFormat("MM/dd/yy",Locale.getDefault()).format(Date(entry.updated)),
                                fontSize=12.sp,color=HomeForest,modifier=Modifier.padding(start=4.dp,bottom=8.dp))
                        }
                    }
                }
            }
        } else {
            if(exports.isEmpty()) Box(Modifier.fillMaxSize().padding(28.dp),contentAlignment=Alignment.Center) {
                Text(uiText("No exports yet"),color=HomeForest)
            } else LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(12.dp),
                horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                items(exports,key={it.id}) {entry ->
                    var menu by remember {mutableStateOf(false)}
                    val snapshot=remember(entry.id) {store.load(entry.projectKey)}
                    Surface(shape=RoundedCornerShape(22.dp),color=Color.White,shadowElevation=1.dp,
                        border=BorderStroke(if(entry.id in selectedIds) 2.dp else 1.dp,
                            if(entry.id in selectedIds) HomeTurquoise else Color(0xFFE9E9E2))) {
                        Column(Modifier.clickable(enabled=selectionMode && !deleting) {toggleSelection(entry.id)}.padding(7.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(.9f).clip(RoundedCornerShape(18.dp))
                                .clickable(enabled=!deleting) {if(selectionMode) toggleSelection(entry.id) else playingExport=entry}) {
                                if(entry.kind==ExportKind.MP4 && snapshot!=null) SavedDrawingPreview(snapshot,Modifier.fillMaxSize())
                                else Box(Modifier.fillMaxSize().background(Color(0xFFF5F5ED)),contentAlignment=Alignment.Center) {
                                    Text(if(entry.kind==ExportKind.MIDI) "♪" else "◖))",fontSize=48.sp,color=HomeForest)
                                }
                                Text(entry.kind.extension.uppercase(),modifier=Modifier.align(Alignment.BottomStart)
                                    .background(Color.White.copy(alpha=.9f)).padding(6.dp),color=HomeForest,fontWeight=FontWeight.Bold)
                                if(selectionMode) Checkbox(checked=entry.id in selectedIds,
                                    onCheckedChange={toggleSelection(entry.id)},enabled=!deleting,
                                    modifier=Modifier.align(Alignment.TopStart).background(Color.White.copy(alpha=.9f),CircleShape),
                                    colors=CheckboxDefaults.colors(checkedColor=HomeTurquoise))
                                if(!selectionMode) Box(Modifier.align(Alignment.TopEnd)) {
                                    TextButton(enabled=!deleting && !copying,onClick={menu=true},contentPadding=PaddingValues(0.dp),modifier=Modifier.size(36.dp)) {Text(uiText("⋮"),fontSize=24.sp)}
                                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                                        DropdownMenuItem(text={Text("Delete",color=Color(0xFFB94F2D))},
                                            onClick={menu=false;deleteIsExport=true;deleteIds=listOf(entry.id)})
                                        DropdownMenuItem(text={Text(uiText("Play here"))},onClick={menu=false;playingExport=entry})
                                        DropdownMenuItem(text={Text(uiText("Open with"))},onClick={menu=false;openExternal(entry)})
                                        DropdownMenuItem(text={Text(uiText("Share"))},onClick={
                                            menu=false
                                            try {repository.share(entry)} catch(e:Exception) {info="Could not share this file."}
                                        })
                                        DropdownMenuItem(text={Text(uiText("Save a copy"))},onClick={
                                            menu=false;pendingCopy=entry.id
                                            try {copyLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                                addCategory(Intent.CATEGORY_OPENABLE);type=entry.kind.mime;putExtra(Intent.EXTRA_TITLE,entry.name)
                                            })} catch(e:Exception) {pendingCopy=null;info="Could not save this file."}
                                        })
                                    }
                                }
                            }
                            Text(entry.name,fontSize=13.sp,color=HomeForest,maxLines=1,modifier=Modifier.padding(top=10.dp,start=4.dp))
                            Text(SimpleDateFormat("MM/dd/yy",Locale.getDefault()).format(Date(entry.created)),
                                fontSize=12.sp,color=HomeForest,modifier=Modifier.padding(start=4.dp,bottom=8.dp))
                        }
                    }
                }
            }
        }
    }
    if(deleteIds.isNotEmpty()) AlertDialog(
        onDismissRequest={if(!deleting) deleteIds=emptyList()},
        containerColor=Color(0xFFFFFEF8),shape=RoundedCornerShape(28.dp),
        title={Text("Delete ${deleteIds.size} ${if(deleteIsExport) {if(deleteIds.size==1) "export" else "exports"} else {if(deleteIds.size==1) "draft" else "drafts"}}?",color=HomeForest)},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(deleteIsExport) "Delete these files from the app and device? Shared copies remain. This cannot be undone."
                else "Delete these drafts permanently? Exported files will remain.",color=HomeForest)
            if(deleting) CircularProgressIndicator(color=HomeTurquoise)
        }},
        confirmButton={Button(enabled=!deleting && !copying && pendingCopy==null,onClick={
            val ids=deleteIds.toList()
            val isExport=deleteIsExport
            val entries=exports.associateBy {it.id}
            playingExport=null
            deleting=true
            scope.launch {
                try {
                    val failed=withContext(Dispatchers.IO) {
                        val failures=mutableListOf<String>()
                        ids.forEach {id ->
                            try {
                                if(isExport) entries[id]?.let {repository.delete(it)} else store.deleteWork(id)
                            } catch(e:kotlinx.coroutines.CancellationException) {throw e}
                            catch(e:Exception) {failures+=id}
                        }
                        failures.toList()
                    }
                    val refreshed=withContext(Dispatchers.IO) {repository.list() to store.workItems()}
                    exports=refreshed.first;drafts=refreshed.second
                    onChanged();deleteIds=emptyList()
                    selectedIds=failed;selectionMode=failed.isNotEmpty()
                    info=if(failed.isEmpty()) "${ids.size} item(s) deleted"
                        else "${ids.size-failed.size} deleted; ${failed.size} could not be deleted. Failed items remain selected so you can retry."
                } catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(e:Exception) {deleteIds=emptyList();info="Could not delete some items. Reopen Library and try again."}
                finally {deleting=false}
            }
        },colors=ButtonDefaults.buttonColors(containerColor=HomeTurquoise),shape=RoundedCornerShape(24.dp)) {Text("Delete")}},
        dismissButton={TextButton(enabled=!deleting,onClick={deleteIds=emptyList()}) {Text("Cancel",color=HomeForest)}})
    playingExport?.let {entry ->
        ExportPlaybackDialog(entry,repository.file(entry),onDismiss={playingExport=null})
    }
    if(copying) AlertDialog(onDismissRequest={},text={Text(uiText("Saving copy…"))},confirmButton={})
    if(info!=null) AlertDialog(onDismissRequest={info=null},text={Text(info!!)},
        confirmButton={TextButton(onClick={info=null}) {Text(uiText("OK"))}})
}

@Composable
private fun SavedDrawingPreview(project:MelodyProject,modifier:Modifier) {
    val context=LocalContext.current
    var bitmap by remember(project.backgroundPhoto) {mutableStateOf<ImageBitmap?>(null)}
    LaunchedEffect(project.backgroundPhoto) {
        project.backgroundPhoto?.let {id ->
            bitmap=withContext(Dispatchers.IO) {runCatching {loadBuiltInBackground(context,id,true)}.getOrNull()}
        }
    }
    val cells=remember(project.strokes) {visibleCells(project.strokes)}
    val drawing=remember(project) {DrawingState(cells,project.visuals.ifEmpty {legacySmoothPaths(cells)})}
    DrawingBoard(drawing,progress=-1f,grid=false,smooth=project.smooth,modifier=modifier,
        enabled=false,selected=project.instrument,eraser=false,brush=1,onPreview={},onCommit={},
        backgroundColor=canvasBackgrounds[project.backgroundIndex.coerceIn(canvasBackgrounds.indices)].color,
        backgroundImage=bitmap,backgroundOpacity=project.backgroundOpacity)
}

@Composable
fun AppSettings() {
    val ads=LocalCasAds.current
    val context=LocalContext.current
    var info by remember {mutableStateOf<String?>(null)}
    val requestLanguage=LocalRequestLanguage.current
    val selectedLanguage=LocalAppLanguage.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text(uiText("Settings"),fontSize=30.sp,fontWeight=FontWeight.Bold,color=HomeForest)
        ReminderSettingsCard()
        SettingsRow("◎",uiText("Language")+" · "+selectedLanguage.nativeName) {requestLanguage()}
        SettingsRow("➜","Share app") {
            val intent=Intent(Intent.ACTION_SEND).apply {
                type="text/plain";putExtra(Intent.EXTRA_TEXT,"Draw pictures and turn them into music with Draw Musical!\nhttps://play.google.com/store/apps/details?id=${context.packageName}")
            }
            runCatching {context.startActivity(Intent.createChooser(intent,"Share app"))}
                .onFailure {info="Sharing is unavailable on this device."}
        }
        if(CasRuntime.ready) SettingsRow("◈","Ad privacy options") {ads?.showPrivacyOptions()}
        SettingsRow("?","Privacy Policy") {
            runCatching {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://zexnoglobal.github.io/Draw-Music-Legal/DrawMusical_Privacy_Policy.html")
                    )
                )
            }.onFailure {
                info="Unable to open the privacy policy. Please try again."
            }
        }
    }
    if(info!=null) AlertDialog(onDismissRequest={info=null},text={Text(info!!)},
        confirmButton={TextButton(onClick={info=null}) {Text(uiText("OK"))}})
}

@Composable
private fun SettingsRow(symbol:String,title:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick=onClick).padding(vertical=18.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
        Text(symbol,fontSize=28.sp,color=HomeForest)
        Text(uiText(title),fontSize=23.sp,color=HomeForest)
    }
}
