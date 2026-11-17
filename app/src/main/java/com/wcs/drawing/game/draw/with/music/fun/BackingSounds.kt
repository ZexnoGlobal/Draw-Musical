package com.wcs.drawing.game.draw.with.music.`fun`

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.*

// Stable IDs are stored in projects; instrument IDs and existing note samples are untouched.
data class BackingOption(val id:String,val title:String,val symbol:String)
val backingOptions=listOf(
    BackingOption("bass","Bass","♭"),BackingOption("drum_kit","Drum kit","♫"),
    BackingOption("arpeggio","Arpeggio","♬"),BackingOption("kick","Kick","K"),
    BackingOption("snare","Snare","S"),BackingOption("clap","Clap","C"),
    BackingOption("hi_hat","Hi-hat","H"),BackingOption("open_hat","Open hat","O"),
    BackingOption("tom","Tom","T"))

/** Original synthesized accompaniment, sequenced on the same eight-beat clock as the drawing. */
object BackingMixer {
    suspend fun mix(mix:FloatArray,bpm:Int,root:Int,scaleIndex:Int,enabled:Set<String>,loop:Boolean) {
        if(enabled.isEmpty()) return
        val rate=MelodyAudio.RATE
        val halfBeat=30.0/bpm
        val scale=noteScales[scaleIndex.coerceIn(noteScales.indices)]
        val kit="drum_kit" in enabled
        val events=mutableListOf<Triple<String,Int,Int>>()
        for(step in 0 until 16) {
            // Union kit and individual hits so toggling Kick does not double kit volume.
            if((kit || "kick" in enabled) && step%4==0) events+=Triple("kick",step,0)
            if((kit || "snare" in enabled) && step%4==2) events+=Triple("snare",step,0)
            if((kit || "hi_hat" in enabled) && step%2==0) events+=Triple("hi_hat",step,0)
            if("clap" in enabled && step%4==2) events+=Triple("clap",step,0)
            if("open_hat" in enabled && step%4==3) events+=Triple("open_hat",step,0)
            if("tom" in enabled && step in listOf(6,7,14,15)) events+=Triple("tom",step,0)
            if("bass" in enabled && step%2==0) events+=Triple("bass",step,36+root)
            if("arpeggio" in enabled) {
                val degrees=intArrayOf(0,2,4,2)
                val degree=degrees[step%4]
                events+=Triple("arpeggio",step,60+root+scale[degree%scale.size]+12*(degree/scale.size))
            }
        }
        for((kind,step,midi) in events) {
            currentCoroutineContext().ensureActive()
            val start=(step*halfBeat*rate).roundToInt()
            val duration=when(kind) {"open_hat"->.35;"bass"->halfBeat*1.7;"arpeggio"->halfBeat*.9;else->.22}
            var noiseState=0x1234567 xor (step*7919) xor kind.hashCode()
            var previousNoise=0.0
            val hz=440.0*2.0.pow((midi-69)/12.0)
            for(i in 0 until (duration*rate).roundToInt()) {
                if(i%8192==0) currentCoroutineContext().ensureActive()
                val t=i.toDouble()/rate
                noiseState=noiseState xor (noiseState shl 13)
                noiseState=noiseState xor (noiseState ushr 17)
                noiseState=noiseState xor (noiseState shl 5)
                val noise=noiseState.toDouble()/Int.MAX_VALUE
                val high=(noise-previousNoise)*.5;previousNoise=noise
                val wave=when(kind) {
                    "kick" -> .62*sin(2*PI*(48*t+105*.025*(1-exp(-t/.025))))*exp(-22*t)
                    "snare" -> (.27*high+.15*sin(2*PI*180*t))*exp(-26*t)
                    "clap" -> {
                        val age=t% .018
                        val burst=if(t<.054) exp(-age*160) else exp(-(t-.054)*45)*.45
                        .3*high*burst
                    }
                    "hi_hat" -> .20*high*exp(-85*t)
                    "open_hat" -> .16*high*exp(-12*t)
                    "tom" -> .4*sin(2*PI*(100*t+45*.04*(1-exp(-t/.04))))*exp(-18*t)
                    "bass" -> .25*(sin(2*PI*hz*t)+.18*sin(4*PI*hz*t))*exp(-4*t)
                    else -> .16*(sin(2*PI*hz*t)+.25*sin(4*PI*hz*t))*exp(-9*t)
                }
                val attack=min(t/.002,1.0)
                val release=((duration-t)/.012).coerceIn(0.0,1.0)
                val at=start+i
                if(at<mix.size) mix[at]+=(wave*attack*release).toFloat()
                else if(loop) mix[at%mix.size]+=(wave*attack*release).toFloat()
            }
        }
    }
}
