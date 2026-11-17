package com.wcs.drawing.game.draw.with.music.`fun`

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A scalable image, independent of fonts and emoji appearance. */
@Composable
fun OrangeDiamondIcon(modifier:Modifier=Modifier,description:String="Watch an ad to unlock") {
    Canvas(modifier.size(20.dp).semantics {contentDescription=description}) {
        fun facet(color:Color,vararg points:Float) {
            val path=Path().apply {
                moveTo(points[0]*size.width,points[1]*size.height)
                for(i in 2 until points.size step 2) lineTo(points[i]*size.width,points[i+1]*size.height)
                close()
            }
            drawPath(path,color)
        }
        facet(Color(0xFFE97812),.06f,.35f,.26f,.10f,.74f,.10f,.94f,.35f,.50f,.94f)
        facet(Color(0xFFFFB548),.06f,.35f,.26f,.10f,.37f,.35f)
        facet(Color(0xFFFFD68A),.26f,.10f,.50f,.10f,.37f,.35f)
        facet(Color(0xFFFFBF58),.50f,.10f,.74f,.10f,.63f,.35f,.37f,.35f)
        facet(Color(0xFFFFA02C),.74f,.10f,.94f,.35f,.63f,.35f)
        facet(Color(0xFFFFAB35),.06f,.35f,.37f,.35f,.50f,.94f)
        facet(Color(0xFFFFCF76),.37f,.35f,.63f,.35f,.50f,.94f)
        facet(Color(0xFFEA7911),.63f,.35f,.94f,.35f,.50f,.94f)
    }
}
