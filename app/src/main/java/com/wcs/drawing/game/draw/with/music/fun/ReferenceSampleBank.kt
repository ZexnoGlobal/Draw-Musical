package com.wcs.drawing.game.draw.with.music.`fun`

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.*

/** Six single-note recordings. Loading and mixing run inside render(), off the UI thread. */
class ReferenceSampleBank(context: Context) {
    private val assets=context.applicationContext.assets
    private val filenames=arrayOf("piano_d4", "marimba_e4", "bells_e4", "wave_synth_g4", "guitar_g4", "flute_e4")
    private val rootMidi=intArrayOf(62,64,64,67,67,64)
    private val cache=mutableMapOf<Int,FloatArray>()

    @Synchronized
    private fun load(index: Int): FloatArray = cache.getOrPut(index) {
        val path="reference_sounds/${filenames[index]}.pcm"
        val bytes=try { assets.open(path).use { it.readBytes() } }
        catch(e: java.io.IOException) { throw IllegalStateException("Missing sound asset: $path. Copy assets/reference_sounds into app/src/main/assets/.",e) }
        check(bytes.isNotEmpty() && bytes.size%2==0) { "Invalid PCM asset: $path" }
        val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        FloatArray(bytes.size/2) { buffer.short/32768f }
    }

    fun naturalFrames(index:Int,midi:Int):Int {
        if(index !in filenames.indices) return 0
        val speed=2.0.pow((midi-rootMidi[index])/12.0)
        return ((load(index).size-1)/speed).toInt()
    }

    /** One-shot playback per note event. Decay may wrap around the repeating phrase. */
    suspend fun mix(index: Int,midi: Int,start: Int,destination: FloatArray,wrapTails: Boolean=false): Boolean {
        if(index !in filenames.indices) return false
        val raw=load(index)
        val speed=2.0.pow((midi-rootMidi[index])/12.0)
        // Gentle four-pole prefilter reduces high partials before upward transposition.
        val source=if(speed>1.01) {
            val cutoff=min(MelodyAudio.RATE*.42/speed,MelodyAudio.RATE*.42)
            val alpha=(1-exp(-2*PI*cutoff/MelodyAudio.RATE)).toFloat()
            var a=0f;var b=0f;var c=0f;var d=0f
            FloatArray(raw.size) { i ->
                a+=alpha*(raw[i]-a); b+=alpha*(a-b); c+=alpha*(b-c); d+=alpha*(c-d); d
            }
        } else raw
        val naturalFrames=((source.size-1)/speed).toInt()
        val frames=if(wrapTails) naturalFrames else min(naturalFrames,destination.size-start)
        for(i in 0 until frames) {
            if(i%8192==0) currentCoroutineContext().ensureActive()
            val position=i*speed;val left=position.toInt();val fraction=(position-left).toFloat()
            val value=source[left]+(source[left+1]-source[left])*fraction
            val target=if(wrapTails) (start+i)%destination.size else start+i
            destination[target]+=value
        }
        return true
    }
}
