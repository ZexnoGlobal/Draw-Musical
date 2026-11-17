package com.wcs.drawing.game.draw.with.music.`fun`

import androidx.compose.ui.graphics.Color

const val MUSIC_COLUMNS = 24
const val MUSIC_ROWS = 32
data class Cell(val x: Int, val y: Int, val instrument: Int)
data class Stroke(val cells: List<Cell>)
data class MelodyProject(
    val strokes: List<Stroke> = emptyList(), val tempo: Int = 120,
    val root: Int = 0, val scale: Int = 0, val loop: Boolean = true,
    val instrument: Int = 0,
    val visuals: List<VisualStroke> = emptyList(), val smooth: Boolean = false,
    val backgroundIndex: Int = 0,
    val backgroundPhoto: String? = null, val backgroundOpacity: Float = 1f,
    val backingSounds: Set<String> = emptySet()
)
data class Instrument(val name: String, val color: Color)
val instruments = listOf(
    Instrument("Piano", Color(0xFF8878D7)),
    Instrument("Marimba", Color(0xFFE9A500)),
    Instrument("Bells", Color(0xFF409E7C)),
    Instrument("Synth", Color(0xFFCD582A)),
    Instrument("Guitar", Color(0xFFA3C66F)),
    Instrument("Flute", Color(0xFF8495CC)),
    Instrument("Strings", Color(0xFF967C90)),
    Instrument("Chimes", Color(0xFF81939F)),
    Instrument("Bass", Color(0xFF68675F)),
    Instrument("Square", Color(0xFFA99171)),
    Instrument("Pulse", Color(0xFF557F7A)),
    Instrument("Reed", Color(0xFFAA846B))
)
val rootNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
val scaleNames = listOf("Pentatonic", "Major", "Minor")
val noteScales = listOf(intArrayOf(0,2,4,7,9), intArrayOf(0,2,4,5,7,9,11), intArrayOf(0,2,3,5,7,8,10))

fun visibleCells(strokes: List<Stroke>): List<Cell> {
    val map = linkedMapOf<Pair<Int, Int>, Cell>()
    strokes.forEach { stroke -> stroke.cells.forEach { map[it.x to it.y] = it } }
    return map.values.toList()
}

fun starterPattern(index: Int): List<Cell> {
    if (index == 0) return (0 until MUSIC_COLUMNS).map { Cell(it, 28-it, 0) }
    if (index == 1) return (0 until MUSIC_COLUMNS).map {
        Cell(it, 25 - (if (it < 12) it else 23-it), 1)
    }
    if (index == 2) return (0 until MUSIC_COLUMNS step 2).map { Cell(it, 8+(it/2%4)*3, 2) } +
        (0 until MUSIC_COLUMNS step 4).map { Cell(it, 28, 8) }
    // An original pixel-art flower with separate melody, stem, and bell accents.
    val cells = mutableListOf<Cell>()
    for (x in 4..17) for (y in 4..17) {
        val dx=x-10.5; val dy=y-10.5; val distance=kotlin.math.sqrt(dx*dx+dy*dy)
        if (distance in 5.0..6.5) cells += Cell(x,y,1)
        if (distance < 2.2) cells += Cell(x,y,3)
    }
    for (y in 17..28) cells += Cell(11,y,2)
    for (x in 6..10) cells += Cell(x,22+(x%2),2)
    for (x in 12..17) cells += Cell(x,25-(x%3),2)
    for (y in 3..11) cells += Cell(21,y,0)
    cells += Cell(20,11,0); cells += Cell(20,12,0)
    return cells
}
