package com.wcs.drawing.game.draw.with.music.`fun`

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileInputStream

private fun exportLifecycleOwner(context:Context):LifecycleOwner? {
    var current=context
    while(true) {
        if(current is LifecycleOwner) return current
        val wrapped=current as? ContextWrapper ?: return null
        if(wrapped.baseContext===current) return null
        current=wrapped.baseContext
    }
}

/** Opens the actual exported file using Android's built-in media decoders, without another app. */
@Composable
fun ExportPlaybackDialog(entry:ExportedFile,file:File,onDismiss:()->Unit) {
    val context=LocalContext.current
    val owner=remember(context) {exportLifecycleOwner(context)}
    val player=remember(entry.id) {MediaPlayer()}
    var prepared by remember(entry.id) {mutableStateOf(false)}
    var playing by remember(entry.id) {mutableStateOf(false)}
    var position by remember(entry.id) {mutableIntStateOf(0)}
    var duration by remember(entry.id) {mutableIntStateOf(0)}
    var error by remember(entry.id) {mutableStateOf<String?>(null)}
    var surface by remember(entry.id) {mutableStateOf<SurfaceHolder?>(null)}
    val video=entry.kind==ExportKind.MP4
    fun pause() {
        if(prepared) runCatching {if(player.isPlaying) player.pause()}
        playing=false
    }
    fun start() {
        if(!prepared || error!=null) return
        try {
            if(duration>0 && player.currentPosition>=duration-50) player.seekTo(0)
            player.start();playing=true
        } catch(e:Exception) {error="Could not play this file.";playing=false}
    }
    DisposableEffect(entry.id) {
        var disposed=false
        val observer=LifecycleEventObserver {_,event ->
            if(event==Lifecycle.Event.ON_STOP) pause()
        }
        owner?.lifecycle?.addObserver(observer)
        try {
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            player.setOnPreparedListener {media ->
                if(!disposed) {
                    prepared=true;duration=media.duration.coerceAtLeast(0)
                    val foreground=owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED)!=false
                    if(foreground && (!video || surface!=null)) start()
                }
            }
            player.setOnCompletionListener {if(!disposed) {playing=false;position=duration}}
            player.setOnErrorListener {_,what,extra ->
                if(!disposed) {
                    playing=false;prepared=false
                    error="This file could not be played. Try exporting it again."
                }
                true
            }
            // A descriptor avoids asking Android's media service to open an app-private pathname.
            if(file.isFile && file.length()>0) FileInputStream(file).use {player.setDataSource(it.fd)}
            else if(entry.publicUri!=null) player.setDataSource(context,Uri.parse(entry.publicUri))
            else throw IllegalStateException("The exported file is missing or empty. Export the drawing again.")
            player.prepareAsync()
        } catch(e:Exception) {error="Could not read this file."}
        onDispose {
            disposed=true
            owner?.lifecycle?.removeObserver(observer)
            player.setOnPreparedListener(null);player.setOnCompletionListener(null);player.setOnErrorListener(null)
            runCatching {player.release()}
        }
    }
    LaunchedEffect(entry.id) {
        delay(15_000)
        if(!prepared && error==null) error="Playback timed out. Please try again."
    }
    // Stop at background transition; don't auto-resume without the user's Play action.
    LaunchedEffect(prepared,playing) {
        while(prepared && playing) {
            runCatching {position=player.currentPosition.coerceAtLeast(0)}
            delay(150)
        }
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Play ${entry.kind.extension.uppercase()}")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(entry.name,maxLines=2)
            if(video) {
                AndroidView(factory={viewContext ->
                    SurfaceView(viewContext).apply {
                        holder.addCallback(object:SurfaceHolder.Callback {
                            override fun surfaceCreated(holder:SurfaceHolder) {
                                surface=holder
                                runCatching {player.setDisplay(holder)}
                                if(prepared && owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED)!=false) start()
                            }
                            override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int) {}
                            override fun surfaceDestroyed(holder:SurfaceHolder) {
                                pause();surface=null;runCatching {player.setDisplay(null)}
                            }
                        })
                    }
                },modifier=Modifier.fillMaxWidth().heightIn(max=280.dp).aspectRatio(.75f).background(Color.Black))
            } else Box(Modifier.fillMaxWidth().height(80.dp),contentAlignment=Alignment.Center) {
                Text(if(entry.kind==ExportKind.MIDI) "♪" else "♫",fontSize=48.sp)
            }
            if(error!=null) Text(error!!,color=MaterialTheme.colorScheme.error)
            else if(!prepared) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp));Text(uiText("Preparing playback…"))
            } else {
                Text("${position/1000}s / ${duration/1000}s")
                Slider(value=position.toFloat().coerceIn(0f,duration.coerceAtLeast(1).toFloat()),
                    onValueChange={value ->
                        runCatching {player.seekTo(value.toInt());position=value.toInt()}
                    },valueRange=0f..duration.coerceAtLeast(1).toFloat(),enabled=duration>0)
                Button(onClick={if(playing) pause() else start()},modifier=Modifier.fillMaxWidth()) {
                    Text(uiText(if(playing) "Pause" else "Play"))
                }
            }
        }},confirmButton={TextButton(onClick=onDismiss){Text(uiText("Close"))}})
}
