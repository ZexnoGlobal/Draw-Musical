package com.wcs.drawing.game.draw.with.music.`fun`

import android.content.Context
import android.graphics.*
import androidx.compose.ui.graphics.toArgb
import kotlin.math.*

/** Offline replay of saved stroke geometry. Never captures the phone screen. */
class ExportFrameRenderer(context:Context,private val project:MelodyProject,val width:Int=480,val height:Int=640) {
    val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
    private val canvas=Canvas(bitmap)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    private val cells=visibleCells(project.strokes).sortedWith(compareBy<Cell> {it.x}.thenBy {it.y})
    private val paths=project.visuals.ifEmpty {legacySmoothPaths(cells)}
    private val background=project.backgroundPhoto?.let {id ->
        if(builtInBackgrounds.none {it.id==id}) null else context.assets.open("backgrounds/$id.jpg").use {BitmapFactory.decodeStream(it)}
    }
    private val totalSegments=paths.sumOf {max(it.points.size-1,1)}.coerceAtLeast(1)
    fun frame(fraction:Float):Bitmap {
        val progress=fraction.coerceIn(0f,1f)
        canvas.drawColor(canvasBackgrounds[project.backgroundIndex.coerceIn(canvasBackgrounds.indices)].color.toArgb())
        background?.let {bg ->
            val scale=max(width.toFloat()/bg.width,height.toFloat()/bg.height)
            val w=bg.width*scale;val h=bg.height*scale
            paint.style=Paint.Style.FILL;paint.alpha=(project.backgroundOpacity*255).roundToInt().coerceIn(0,255)
            canvas.drawBitmap(bg,null,RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),paint)
            paint.alpha=255
        }
        if(project.smooth) {
            var remaining=progress*totalSegments
            for(stroke in paths) {
                if(remaining<=0) break
                val points=stroke.points
                paint.color=instruments[stroke.instrument].color.toArgb()
                paint.strokeWidth=max(1f,stroke.widthCells*width/MUSIC_COLUMNS)
                paint.style=Paint.Style.STROKE
                if(points.size==1) {
                    paint.style=Paint.Style.FILL
                    canvas.drawCircle(points[0].x/MUSIC_COLUMNS*width,points[0].y/MUSIC_ROWS*height,paint.strokeWidth/2,paint)
                    remaining-=1
                } else for(i in 1 until points.size) {
                    if(remaining<=0) break
                    val a=points[i-1];val b=points[i];val f=min(remaining,1f)
                    canvas.drawLine(a.x/MUSIC_COLUMNS*width,a.y/MUSIC_ROWS*height,
                        (a.x+(b.x-a.x)*f)/MUSIC_COLUMNS*width,(a.y+(b.y-a.y)*f)/MUSIC_ROWS*height,paint)
                    remaining-=1
                }
            }
        } else {
            paint.style=Paint.Style.FILL
            val unitX=width.toFloat()/MUSIC_COLUMNS;val unitY=height.toFloat()/MUSIC_ROWS
            cells.take(ceil(cells.size*progress).toInt()).forEach {cell ->
                paint.color=instruments[cell.instrument].color.toArgb()
                canvas.drawRoundRect(cell.x*unitX+1,cell.y*unitY+1,(cell.x+1)*unitX-1,(cell.y+1)*unitY-1,2f,2f,paint)
            }
        }
        return bitmap
    }
    fun close() {background?.recycle();bitmap.recycle()}
}
