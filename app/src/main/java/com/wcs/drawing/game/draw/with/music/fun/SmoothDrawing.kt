package com.wcs.drawing.game.draw.with.music.`fun`

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as PathStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.*

data class DrawPoint(val x: Float,val y: Float)
data class VisualStroke(val instrument: Int,val points: List<DrawPoint>,val widthCells: Float=.14f)
data class DrawingState(val cells: List<Cell>,val paths: List<VisualStroke>)

/** Recover approximate center lines for drawings saved before vector points existed. */
fun legacySmoothPaths(cells: List<Cell>): List<VisualStroke> {
    val output=mutableListOf<VisualStroke>()
    cells.groupBy {it.instrument}.forEach { (instrument,group) ->
        val positions=group.map {it.x to it.y}.toSet()
        fun neighbors(p: Pair<Int,Int>): List<Pair<Int,Int>> {
            val (x,y)=p
            return (-1..1).flatMap {dx -> (-1..1).map {dy -> dx to dy}}
                .filter { (dx,dy) ->
                    (dx!=0 || dy!=0) && (x+dx to y+dy) in positions &&
                        !(dx!=0 && dy!=0 && ((x+dx to y) in positions || (x to y+dy) in positions))
                }.map { (dx,dy) -> x+dx to y+dy }
        }
        val links=positions.associateWith {neighbors(it)}
        val visited=mutableSetOf<Set<Pair<Int,Int>>>()
        fun point(p: Pair<Int,Int>)=DrawPoint(p.first+.5f,p.second+.5f)
        fun trace(start: Pair<Int,Int>,next: Pair<Int,Int>) {
            val path=mutableListOf(point(start));var a=start;var b=next
            while(visited.add(setOf(a,b))) {
                path+=point(b)
                val candidates=links.getValue(b).filter {it!=a && setOf(b,it) !in visited}
                if(links.getValue(b).size!=2 || candidates.isEmpty()) break
                a=b;b=candidates.first()
            }
            if(path.size>1) output+=VisualStroke(instrument,path)
        }
        positions.forEach {p ->
            val near=links.getValue(p)
            if(near.isEmpty()) output+=VisualStroke(instrument,listOf(point(p)))
            if(near.size!=2) near.forEach {q -> if(setOf(p,q) !in visited) trace(p,q)}
        }
        positions.forEach {p -> links.getValue(p).forEach {q -> if(setOf(p,q) !in visited) trace(p,q)} }
    }
    return output
}

/** Split at erased cells so a curve never bridges the erased part. */
fun eraseVisualCells(paths: List<VisualStroke>,removed: Set<Pair<Int,Int>>): List<VisualStroke> {
    if(removed.isEmpty()) return paths
    return buildList {
        paths.forEach {path ->
            var run=mutableListOf<DrawPoint>()
            fun flush() {if(run.isNotEmpty()) add(path.copy(points=run.toList()));run=mutableListOf()}
            val dense=buildList<DrawPoint> {
                path.points.firstOrNull()?.let {add(it)}
                path.points.zipWithNext().forEach { (a,b) ->
                    val steps=ceil(max(abs(b.x-a.x),abs(b.y-a.y))/.1f).toInt().coerceAtLeast(1)
                    for(i in 1..steps) {
                        val t=i.toFloat()/steps
                        add(DrawPoint(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t))
                    }
                }
            }
            dense.forEach {point ->
                if((floor(point.x).toInt() to floor(point.y).toInt()) in removed) flush() else run+=point
            }
            flush()
        }
    }
}

@Composable
fun DrawingBoard(drawing: DrawingState,progress: Float,grid: Boolean,smooth: Boolean,modifier: Modifier,
    enabled: Boolean,selected: Int,eraser: Boolean,brush: Int,
    onPreview:(DrawingState?)->Unit,onCommit:(DrawingState)->Unit,
    backgroundColor: Color=Color(0xFFF5F5ED),backgroundImage: ImageBitmap?=null,backgroundOpacity: Float=1f) {
    val latest by rememberUpdatedState(drawing)
    Canvas(modifier.clip(RoundedCornerShape(18.dp)).background(backgroundColor)
        .semantics {contentDescription="${if(smooth) "Smooth" else "Pixel"} drawing canvas. Horizontal is time; vertical is pitch."}
        .pointerInput(enabled,selected,eraser,brush,smooth) {
            if(enabled) awaitEachGesture {
                val down=awaitFirstDown()
                val unit=min(size.width.toFloat()/MUSIC_COLUMNS,size.height.toFloat()/MUSIC_ROWS)
                if(unit<=0f) return@awaitEachGesture
                val left=(size.width-unit*MUSIC_COLUMNS)/2;val top=(size.height-unit*MUSIC_ROWS)/2
                fun local(p: Offset)=DrawPoint((p.x-left)/unit,(p.y-top)/unit)
                val work=latest.cells.associateBy {it.x to it.y}.toMutableMap()
                val originalPaths=latest.paths
                val removed=mutableSetOf<Pair<Int,Int>>()
                val points=mutableListOf<DrawPoint>()
                fun dab(p: Offset) {
                    val pos=local(p)
                    if(pos.x !in 0f..MUSIC_COLUMNS.toFloat() || pos.y !in 0f..MUSIC_ROWS.toFloat()) return
                    val cx=floor(pos.x).toInt().coerceIn(0,MUSIC_COLUMNS-1)
                    val cy=floor(pos.y).toInt().coerceIn(0,MUSIC_ROWS-1)
                    for(dx in 0 until brush) for(dy in 0 until brush) {
                        val x=cx+dx-(brush-1)/2;val y=cy+dy-(brush-1)/2
                        if(x in 0 until MUSIC_COLUMNS && y in 0 until MUSIC_ROWS) {
                            if(eraser) {work.remove(x to y);removed+=x to y}
                            else work[x to y]=Cell(x,y,selected)
                        }
                    }
                    if(!eraser) {
                        val clamped=DrawPoint(pos.x.coerceIn(0f,MUSIC_COLUMNS-.001f),pos.y.coerceIn(0f,MUSIC_ROWS-.001f))
                        val last=points.lastOrNull()
                        if(last==null || abs(clamped.x-last.x)+abs(clamped.y-last.y)>.035f) points+=clamped
                    }
                }
                fun current(): DrawingState {
                    val paths=if(eraser) eraseVisualCells(originalPaths,removed)
                        else originalPaths+if(points.isEmpty()) emptyList() else listOf(VisualStroke(selected,points.toList(),brush*.14f))
                    return DrawingState(work.values.toList(),paths)
                }
                var previous=down.position
                dab(previous);onPreview(current());down.consume()
                try {
                    while(true) {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull {it.id==down.id} ?: break
                        if(!change.pressed) break
                        val next=change.position
                        // Dense sub-cell points retain curves in either display mode and make erasing precise.
                        val steps=ceil(max(abs(next.x-previous.x),abs(next.y-previous.y))/(unit*.1f)).toInt().coerceIn(1,2048)
                        for(i in 1..steps) dab(previous+(next-previous)*(i.toFloat()/steps))
                        previous=next;onPreview(current());change.consume()
                    }
                    onCommit(current())
                } finally {onPreview(null)}
            }
        }) {
        backgroundImage?.let {image ->
            if(size.width>0 && size.height>0) {
                val canvasRatio=size.width/size.height
                val imageRatio=image.width.toFloat()/image.height
                val sourceWidth=if(imageRatio>canvasRatio) (image.height*canvasRatio).roundToInt().coerceIn(1,image.width) else image.width
                val sourceHeight=if(imageRatio>canvasRatio) image.height else (image.width/canvasRatio).roundToInt().coerceIn(1,image.height)
                drawImage(image,srcOffset=IntOffset((image.width-sourceWidth)/2,(image.height-sourceHeight)/2),
                    srcSize=IntSize(sourceWidth,sourceHeight),dstSize=IntSize(size.width.roundToInt(),size.height.roundToInt()),
                    alpha=backgroundOpacity.coerceIn(0f,1f))
            }
        }
        val unit=min(size.width/MUSIC_COLUMNS,size.height/MUSIC_ROWS)
        val left=(size.width-unit*MUSIC_COLUMNS)/2;val top=(size.height-unit*MUSIC_ROWS)/2
        fun position(p: DrawPoint)=Offset(left+p.x*unit,top+p.y*unit)
        if(grid) {
            val ink=Color(0xFF263F37).copy(alpha=.07f)
            for(x in 0..MUSIC_COLUMNS) drawLine(ink,Offset(left+x*unit,top),Offset(left+x*unit,top+MUSIC_ROWS*unit))
            for(y in 0..MUSIC_ROWS) drawLine(ink,Offset(left,top+y*unit),Offset(left+MUSIC_COLUMNS*unit,top+y*unit))
        }
        if(smooth) drawing.paths.forEach {line ->
            val points=line.points
            if(points.isNotEmpty()) {
                val ink=instruments[line.instrument].color
                val width=max(1.dp.toPx(),line.widthCells*unit)
                if(points.size==1) drawCircle(ink,width/2,position(points.first()))
                else {
                    val path=Path();val first=position(points.first());path.moveTo(first.x,first.y)
                    for(i in 1 until points.size) {
                        val a=position(points[i-1]);val b=position(points[i]);val mid=(a+b)/2f
                        path.quadraticBezierTo(a.x,a.y,mid.x,mid.y)
                    }
                    val last=position(points.last());path.lineTo(last.x,last.y)
                    drawPath(path,ink,style=PathStroke(width,cap=StrokeCap.Round,join=StrokeJoin.Round))
                }
            }
        } else drawing.cells.forEach {cell ->
            val gap=unit*.06f
            drawRoundRect(instruments[cell.instrument].color,Offset(left+cell.x*unit+gap/2,top+cell.y*unit+gap/2),
                Size(unit-gap,unit-gap),CornerRadius(unit*.12f))
        }
        if(progress>=0) {
            val x=left+progress*unit*MUSIC_COLUMNS
            drawLine(Color(0xFF08BFB3),Offset(x,top),Offset(x,top+MUSIC_ROWS*unit),3.dp.toPx())
        }
    }
}
