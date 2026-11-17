package com.wcs.drawing.game.draw.with.music.`fun`

import kotlin.math.floor

object MusicTiming {
    const val PHRASE_BEATS=8
    fun phraseSeconds(bpm: Int): Double {
        require(bpm in 40..240)
        return PHRASE_BEATS*60.0/bpm
    }
}

object MusicPitch {
    /**
     * C-pentatonic mapping fitted to the bear recording's note activity.
     * Rows 5..21 are supported by that pattern; outer rows extrapolate the fit.
     * Interpolate before quantizing so Major/Minor can introduce their own notes.
     */
    fun rowMidi(row: Int,root: Int=0,scaleIndex: Int=0): Int {
        val degree=21.0-row.coerceIn(0,MUSIC_ROWS-1)*0.525
        val octave=floor(degree/5.0).toInt()
        val within=degree-octave*5
        val index=floor(within).toInt().coerceIn(0,4)
        val fraction=within-index
        val pentatonic=noteScales[0]
        val next=if(index==4) 12 else pentatonic[index+1]
        val raw=36+octave*12+pentatonic[index]+fraction*(next-pentatonic[index])
        val pitchOctave=floor(raw/12).toInt()
        val semitone=raw-pitchOctave*12
        val scale=noteScales[scaleIndex.coerceIn(0,noteScales.lastIndex)]
        val offset=scale.lastOrNull {it<=semitone+0.000001} ?: 0
        return (pitchOctave*12+offset+root.coerceIn(0,11)).coerceIn(0,127)
    }
}
