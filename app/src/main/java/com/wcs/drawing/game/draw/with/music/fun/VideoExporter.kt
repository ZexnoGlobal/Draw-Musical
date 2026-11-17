package com.wcs.drawing.game.draw.with.music.`fun`

import android.content.Context
import android.graphics.Bitmap
import android.media.*
import android.os.SystemClock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.*

/** H.264 + AAC MP4 generated locally with native codecs; no screen/camera permissions. */
object VideoExporter {
    private const val FPS=24
    suspend fun write(context:Context,project:MelodyProject,pcm:ShortArray,target:File,onProgress:(Int)->Unit) {
        val video=File.createTempFile("draw_video_",".mp4",context.cacheDir)
        val audio=File.createTempFile("draw_audio_",".mp4",context.cacheDir)
        val painter=ExportFrameRenderer(context,project)
        try {
            val frames=ceil(pcm.size.toDouble()/MelodyAudio.RATE*FPS).toInt()
            val pixels=IntArray(painter.width*painter.height)
            val format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,painter.width,painter.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE,1_800_000);setInteger(MediaFormat.KEY_FRAME_RATE,FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
                setInteger(MediaFormat.KEY_COLOR_STANDARD,MediaFormat.COLOR_STANDARD_BT601_NTSC)
                setInteger(MediaFormat.KEY_COLOR_RANGE,MediaFormat.COLOR_RANGE_LIMITED)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER,MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            }
            val phrase=MusicTiming.phraseSeconds(project.tempo)
            encode(format,video,frames,{i ->i*1_000_000L/FPS}) {codec,index,frame ->
                val capacity=requireNotNull(codec.getInputBuffer(index)).capacity()
                val image=codec.getInputImage(index) ?: error("This device's video encoder cannot accept drawing frames")
                try {
                    val fraction=(frame.toDouble()/FPS/phrase).toFloat()
                    fillYuv(painter.frame(fraction),pixels,image)
                } finally {image.close()}
                codec.queueInputBuffer(index,0,capacity,frame*1_000_000L/FPS,0)
                if(frame%3==0) onProgress(frame*85/frames)
            }
            val chunks=(pcm.size+1023)/1024
            val audioFormat=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,MelodyAudio.RATE,1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE,128000);setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,2048)
            }
            encode(audioFormat,audio,chunks,{i ->min(i*1024,pcm.size)*1_000_000L/MelodyAudio.RATE}) {codec,index,chunk ->
                val buffer=requireNotNull(codec.getInputBuffer(index)).apply {clear();order(ByteOrder.LITTLE_ENDIAN)}
                val start=chunk*1024;val count=min(1024,pcm.size-start)
                check(buffer.remaining()>=count*2)
                for(i in start until start+count) buffer.putShort(pcm[i])
                codec.queueInputBuffer(index,0,count*2,start*1_000_000L/MelodyAudio.RATE,0)
            }
            onProgress(95);combine(video,audio,target);onProgress(100)
        } finally {painter.close();video.delete();audio.delete()}
    }
    private suspend fun encode(format:MediaFormat,target:File,count:Int,timestamp:(Int)->Long,
        feed:(MediaCodec,Int,Int)->Unit) {
        val name=MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
            ?: error("No compatible ${format.getString(MediaFormat.KEY_MIME)} encoder on this device")
        val codec=MediaCodec.createByCodecName(name)
        var muxer:MediaMuxer?=null;var muxing=false;var codecStarted=false
        try {
            codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();codecStarted=true
            val sink=MediaMuxer(target.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);muxer=sink
            var input=0;var inputEnded=false;var outputEnded=false;var track=-1
            var lastActivity=SystemClock.elapsedRealtime()
            val info=MediaCodec.BufferInfo()
            while(!outputEnded) {
                currentCoroutineContext().ensureActive()
                check(SystemClock.elapsedRealtime()-lastActivity<30_000) {"Media encoder timed out"}
                if(!inputEnded) {
                    val index=codec.dequeueInputBuffer(10_000)
                    if(index>=0) {
                        if(input<count) {feed(codec,index,input);input++}
                        else {codec.queueInputBuffer(index,0,0,timestamp(count),MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true}
                        lastActivity=SystemClock.elapsedRealtime()
                    }
                }
                var drain=true
                while(drain) {
                    currentCoroutineContext().ensureActive()
                    val index=codec.dequeueOutputBuffer(info,10_000)
                    when {
                        index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxing);track=sink.addTrack(codec.outputFormat);sink.start();muxing=true
                        }
                        index>=0 -> {
                            val buffer=requireNotNull(codec.getOutputBuffer(index))
                            try {
                                if(info.size>0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0) {
                                    check(muxing);buffer.position(info.offset);buffer.limit(info.offset+info.size)
                                    sink.writeSampleData(track,buffer,info)
                                }
                                outputEnded=(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0
                            } finally {codec.releaseOutputBuffer(index,false)}
                            lastActivity=SystemClock.elapsedRealtime()
                            if(outputEnded) drain=false
                        }
                        else ->drain=false
                    }
                }
            }
            check(muxing);sink.stop();muxing=false
        } finally {
            if(codecStarted) runCatching {codec.stop()};codec.release()
            if(muxing) runCatching {muxer?.stop()};muxer?.release()
        }
    }
    private fun fillYuv(bitmap:Bitmap,pixels:IntArray,image:Image) {
        val w=bitmap.width;val h=bitmap.height
        require(image.width>=w && image.height>=h && image.planes.size==3)
        bitmap.getPixels(pixels,0,w,0,0,w,h)
        val yp=image.planes[0];val up=image.planes[1];val vp=image.planes[2]
        val ybase=yp.buffer.position();val ubase=up.buffer.position();val vbase=vp.buffer.position()
        fun r(c:Int)=(c shr 16) and 255
        fun g(c:Int)=(c shr 8) and 255
        fun b(c:Int)=c and 255
        for(y in 0 until h) for(x in 0 until w) {
            val c=pixels[y*w+x]
            val l=((66*r(c)+129*g(c)+25*b(c)+128) shr 8)+16
            yp.buffer.put(ybase+y*yp.rowStride+x*yp.pixelStride,l.coerceIn(0,255).toByte())
        }
        for(y in 0 until h step 2) for(x in 0 until w step 2) {
            var red=0;var green=0;var blue=0
            for(dy in 0..1) for(dx in 0..1) {val c=pixels[(y+dy)*w+x+dx];red+=r(c);green+=g(c);blue+=b(c)}
            red/=4;green/=4;blue/=4
            val u=(( -38*red-74*green+112*blue+128) shr 8)+128
            val v=((112*red-94*green-18*blue+128) shr 8)+128
            up.buffer.put(ubase+y/2*up.rowStride+x/2*up.pixelStride,u.coerceIn(0,255).toByte())
            vp.buffer.put(vbase+y/2*vp.rowStride+x/2*vp.pixelStride,v.coerceIn(0,255).toByte())
        }
    }
    private suspend fun combine(video:File,audio:File,target:File) {
        val sources=listOf(video,audio).map {file ->MediaExtractor().apply {setDataSource(file.absolutePath)}}
        val muxer=MediaMuxer(target.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started=false
        try {
            val tracks=sources.map {muxer.addTrack(it.getTrackFormat(0))}
            sources.forEach {it.selectTrack(0)};muxer.start();started=true
            val buffer=ByteBuffer.allocateDirect(4*1024*1024);val info=MediaCodec.BufferInfo()
            while(true) {
                currentCoroutineContext().ensureActive()
                val next=sources.indices.filter {sources[it].sampleTime>=0}.minByOrNull {sources[it].sampleTime} ?: break
                val source=sources[next];buffer.clear()
                val size=source.readSampleData(buffer,0)
                if(size<0) break
                val flags=if((source.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC)!=0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0,size,source.sampleTime,flags);muxer.writeSampleData(tracks[next],buffer,info);source.advance()
            }
            muxer.stop();started=false
        } finally {if(started) runCatching {muxer.stop()};muxer.release();sources.forEach {it.release()}}
    }
}
