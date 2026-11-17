package com.wcs.drawing.game.draw.with.music.`fun`

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream

/** Standard MIDI File type 0, 480 ticks/quarter; MIDI stores notes, not recorded timbres. */
object MidiExporter {
    private data class Event(val tick:Int,val priority:Int,val bytes:ByteArray)
    fun write(project:MelodyProject,output:OutputStream) {
        val events=mutableListOf<Event>()
        fun event(tick:Int,priority:Int,vararg bytes:Int) {
            events+=Event(tick,priority,ByteArray(bytes.size) {bytes[it].toByte()})
        }
        val tempo=60_000_000/project.tempo
        event(0,-2,0xff,0x51,3,tempo shr 16,tempo shr 8,tempo)
        event(0,-2,0xff,0x58,4,4,2,24,8)
        val programs=intArrayOf(0,12,14,80,24,73,48,10,32,80,81,68)
        val channels=intArrayOf(0,1,2,3,4,5,6,7,8,10,11,12)
        programs.forEachIndexed {i,program ->event(0,-1,0xc0 or channels[i],program)}
        event(0,-1,0xcd,32);event(0,-1,0xce,80)
        fun note(tick:Int,length:Int,channel:Int,pitch:Int,velocity:Int=88) {
            event(tick,1,0x90 or channel,pitch.coerceIn(0,127),velocity)
            event((tick+length).coerceAtMost(3840),0,0x80 or channel,pitch.coerceIn(0,127),0)
        }
        val board=visibleCells(project.strokes).associateBy {it.x to it.y}
        val triggered=mutableSetOf<Triple<Int,Int,Int>>()
        board.values.forEach {cell ->
            if(cell.instrument !in 0..5 && board[(cell.x-1) to cell.y]?.instrument==cell.instrument) return@forEach
            val pitch=MusicPitch.rowMidi(cell.y,project.root,project.scale)
            if(!triggered.add(Triple(cell.x,pitch,cell.instrument))) return@forEach
            var end=cell.x+1
            while(end<MUSIC_COLUMNS && board[end to cell.y]?.instrument==cell.instrument) end++
            val length=if(cell.instrument in 0..5) 140 else ((end-cell.x)*160-8).coerceAtLeast(1)
            note(cell.x*160,length,channels[cell.instrument],pitch)
        }
        val enabled=project.backingSounds
        val kit="drum_kit" in enabled
        val scale=noteScales[project.scale]
        for(step in 0 until 16) {
            val tick=step*240
            if((kit || "kick" in enabled) && step%4==0) note(tick,60,9,36)
            if((kit || "snare" in enabled) && step%4==2) note(tick,60,9,38)
            if((kit || "hi_hat" in enabled) && step%2==0) note(tick,45,9,42,65)
            if("clap" in enabled && step%4==2) note(tick,60,9,39)
            if("open_hat" in enabled && step%4==3) note(tick,180,9,46,65)
            if("tom" in enabled && step in listOf(6,7,14,15)) note(tick,120,9,45)
            if("bass" in enabled && step%2==0) note(tick,400,13,36+project.root)
            if("arpeggio" in enabled) {
                val degree=intArrayOf(0,2,4,2)[step%4]
                note(tick,200,14,60+project.root+scale[degree%scale.size]+12*(degree/scale.size),70)
            }
        }
        val track=ByteArrayOutputStream()
        fun variable(value:Int) {
            require(value>=0)
            var v=value;val bytes=mutableListOf(v and 127)
            while(v>127) {v=v ushr 7;bytes.add((v and 127) or 128)}
            bytes.asReversed().forEach {track.write(it)}
        }
        var last=0
        events.sortedWith(compareBy<Event> {it.tick}.thenBy {it.priority}).forEach {
            variable(it.tick-last);track.write(it.bytes);last=it.tick
        }
        variable(3840-last);track.write(byteArrayOf(0xff.toByte(),0x2f,0))
        val data=DataOutputStream(output)
        data.writeBytes("MThd");data.writeInt(6);data.writeShort(0);data.writeShort(1);data.writeShort(480)
        data.writeBytes("MTrk");data.writeInt(track.size());track.writeTo(output);output.flush()
    }
}
