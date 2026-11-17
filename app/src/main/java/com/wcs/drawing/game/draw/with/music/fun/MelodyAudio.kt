package com.wcs.drawing.game.draw.with.music.`fun`

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class MelodyAudio(context: android.content.Context) {
    private val samples=ReferenceSampleBank(context)
    companion object { const val RATE = 44100 }
    private var track: AudioTrack? = null
    var generation = 0
        private set

    suspend fun render(cells: List<Cell>, bpm: Int, root: Int = 0, scaleIndex: Int = 0, looping: Boolean = false, backingSounds: Set<String> = emptySet(),includeReleaseTail:Boolean=false): ShortArray {
        require(bpm in 40..240)
        val step = MusicTiming.phraseSeconds(bpm) / MUSIC_COLUMNS
        val phraseFrames=(MUSIC_COLUMNS*step*RATE).roundToInt()
        var totalFrames=phraseFrames
        if(includeReleaseTail && !looping) {
            if(backingSounds.isNotEmpty()) totalFrames+=(RATE*.4).roundToInt()
            cells.forEach {cell ->
                val midi=MusicPitch.rowMidi(cell.y,root,scaleIndex)
                totalFrames=max(totalFrames,(cell.x*step*RATE).roundToInt()+samples.naturalFrames(cell.instrument,midi))
            }
        }
        val mix=FloatArray(totalFrames)
        val triggered=mutableSetOf<Triple<Int,Int,Int>>()
        val board = cells.associateBy { it.x to it.y }
        // Recorded instruments retrigger per column; duplicate unison cells trigger once.
        for (cell in board.values) {
            currentCoroutineContext().ensureActive()
            if (cell.instrument !in 0..5 && board[(cell.x-1) to cell.y]?.instrument == cell.instrument) continue
            var end=cell.x+1
            while(end<MUSIC_COLUMNS && board[end to cell.y]?.instrument==cell.instrument) end++
            val midi=MusicPitch.rowMidi(cell.y,root,scaleIndex)
            if(!triggered.add(Triple(cell.x,midi,cell.instrument))) continue
            val hz=440.0*2.0.pow((midi-69)/12.0)
            val start=(cell.x*step*RATE).roundToInt()
            if(samples.mix(cell.instrument,midi,start,mix,wrapTails=looping)) continue
            val duration=(end-cell.x)*step
            val count=(duration*RATE).roundToInt()
            for(i in 0 until count) {
                if(i%8192==0) currentCoroutineContext().ensureActive()
                val at=start+i
                if(at>=mix.size) break
                val t=i.toDouble()/RATE
                val phase=2*PI*hz*t
                fun harmonic(n: Double): Double = if(hz*n<RATE*.46) sin(phase*n) else 0.0
                val wave=when(cell.instrument) {
                    0 -> (harmonic(1.0)+.35*harmonic(2.0)+.16*harmonic(3.0))*exp(-3*t)
                    1 -> (harmonic(1.0)+.35*harmonic(3.0)+.12*harmonic(7.0))*exp(-12*t)
                    2 -> (harmonic(1.0)+.4*harmonic(2.76)+.16*harmonic(5.4))*exp(-5*t)
                    3 -> harmonic(1.0)
                    4 -> (harmonic(1.0)+.45*harmonic(2.0)+.25*harmonic(3.0)+.1*harmonic(4.0))*exp(-6*t)
                    5 -> .9*sin(phase+.025*sin(2*PI*5*t))+.1*harmonic(2.0)
                    6 -> (harmonic(1.0)+.48*harmonic(2.0)+.30*harmonic(3.0)+.16*harmonic(4.0))*min(t/.12,1.0)
                    7 -> (harmonic(1.0)+.5*harmonic(2.0)+.3*harmonic(4.0))*exp(-8*t)
                    8 -> (.8*sin(phase*.5)+.2*harmonic(1.0))*exp(-2*t)
                    9 -> harmonic(1.0)+harmonic(3.0)/3+harmonic(5.0)/5+harmonic(7.0)/7
                    10 -> .7*harmonic(1.0)+.3*harmonic(2.0)+.2*harmonic(3.0)
                    else -> harmonic(1.0)+.5*harmonic(2.0)+.18*harmonic(4.0)+.1*harmonic(6.0)
                }
                val attack=(t/.008).coerceAtMost(1.0)
                val release=((duration-t)/.025).coerceIn(0.0,1.0)
                mix[at]+=(wave*attack*release).toFloat()
            }
        }
        BackingMixer.mix(mix,bpm,root,scaleIndex,backingSounds,looping)
        // A continuous loop wraps recorded decay into its beginning; avoid a fade dip.
        if(!looping) {
            val fade=min((RATE*.015).toInt(),mix.size)
            for(i in 0 until fade) mix[mix.lastIndex-i]*=i.toFloat()/fade
        }
        val peak=mix.maxOfOrNull { abs(it) }?.coerceAtLeast(1f) ?: 1f
        return ShortArray(mix.size) { (mix[it]/peak*24000).roundToInt().coerceIn(-32768,32767).toShort() }
    }

    /** Streams complete phrases. The provider is sampled only between phrases, on Main. */
    suspend fun stream(initial:ShortArray,nextPhrase:()->ShortArray,repeat:()->Boolean,
        onProgress:(Float)->Unit,loopLimit:Int?=null):Boolean {
        require(loopLimit==null || loopLimit>0)
        require(initial.isNotEmpty())
        stop()
        val minimum=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
        check(minimum>0) {"Audio output unavailable"}
        val created=AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(max(minimum,2048)).build()
        track=created
        val token=generation
        val boundaries=java.util.ArrayDeque<Pair<Long,Int>>()
        var queued=0L
        var phrase=initial
        var started=false
        var phrasesWritten=0
        var reachedLimit=false
        fun alive()=track===created && generation==token
        fun played()=created.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        fun report() {
            val head=played()
            while(boundaries.size>1) {
                val first=boundaries.first()
                if(head<first.first+first.second) break
                boundaries.removeFirst()
            }
            boundaries.peekFirst()?.let {(start,length) ->
                onProgress(((head-start).toFloat()/length).coerceIn(0f,1f))
            }
        }
        try {
            check(created.state==AudioTrack.STATE_INITIALIZED) {"Audio initialization failed"}
            while(alive()) {
                currentCoroutineContext().ensureActive()
                boundaries.addLast(queued to phrase.size)
                var offset=0
                while(offset<phrase.size && alive()) {
                    currentCoroutineContext().ensureActive()
                    val written=created.write(phrase,offset,min(512,phrase.size-offset),AudioTrack.WRITE_NON_BLOCKING)
                    check(written>=0) {"Audio write failed: $written"}
                    offset+=written;queued+=written
                    // Prime the stream before starting; subsequent writes never block the UI.
                    if(!started && (queued>=max(minimum,2048)/2 || offset==phrase.size || (written==0 && queued>0))) {
                        created.play();started=true
                    }
                    if(started) report()
                    delay(if(written==0) 4L else 1L)
                }
                if(!alive()) break
                phrasesWritten++
                if(loopLimit!=null && phrasesWritten>=loopLimit) {
                    // Count audible, fully completed phrases, never queued buffers.
                    while(alive() && played()<queued) {report();delay(4)}
                    reachedLimit=alive() && repeat()
                    break
                }
                // Keep only a short scheduling lead before taking the latest prepared phrase.
                while(alive() && played()<queued-512) {report();delay(4)}
                if(!alive()) break
                if(!repeat()) {
                    while(alive() && played()<queued) {report();delay(4)}
                    break
                }
                phrase=nextPhrase()
                require(phrase.isNotEmpty())
            }
        } finally {
            if(track===created) stop()
        }
        return reachedLimit
    }

    fun play(pcm: ShortArray, loop: Boolean) {
        stop()
        val created=AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size*2).build()
        track=created
        check(created.state!=AudioTrack.STATE_UNINITIALIZED) { "Audio output unavailable" }
        check(created.write(pcm,0,pcm.size)==pcm.size) { "Could not load audio" }
        check(created.state==AudioTrack.STATE_INITIALIZED) { "PCM initialization failed" }
        if(loop) check(created.setLoopPoints(0,pcm.size,-1)==AudioTrack.SUCCESS)
        created.play()
    }
    fun isPlaying() = track?.playState==AudioTrack.PLAYSTATE_PLAYING
    fun framesPlayed(): Long = (track?.playbackHeadPosition?.toLong() ?: 0L) and 0xFFFFFFFFL
    fun stop() {
        generation++
        val current=track ?: return
        track=null
        try { if(current.state==AudioTrack.STATE_INITIALIZED) current.pause() }
        catch(e: IllegalStateException) { android.util.Log.w("DrawMusicAudio","Already stopped",e) }
        finally {
            try { current.release() }
            catch(e: IllegalStateException) { android.util.Log.w("DrawMusicAudio","Already released",e) }
        }
    }
}

/** Mono 16-bit PCM WAV. The supplied PCM may include a release tail. */
suspend fun writeMelodyWav(output: OutputStream, pcm: ShortArray) {
    val bytes=pcm.size*2
    val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36+bytes)
    header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
    header.putShort(1.toShort()).putShort(1.toShort()).putInt(MelodyAudio.RATE)
    header.putInt(MelodyAudio.RATE*2).putShort(2.toShort()).putShort(16.toShort())
    header.put("data".toByteArray(Charsets.US_ASCII)).putInt(bytes)
    output.write(header.array())
    val buffer=ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
    var index=0
    while(index<pcm.size) {
        currentCoroutineContext().ensureActive()
        buffer.clear()
        while(buffer.remaining()>=2 && index<pcm.size) buffer.putShort(pcm[index++])
        output.write(buffer.array(),0,buffer.position())
    }
    output.flush()
}
