package com.wcs.drawing.game.draw.with.music.`fun`

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun DrawMusicalBrandIcon(modifier:Modifier=Modifier) {
    Image(painter=painterResource(R.drawable.draw_musical_icon),contentDescription="Draw Musical",
        modifier=modifier.size(150.dp).clip(RoundedCornerShape(28.dp)),contentScale=ContentScale.Fit)
}
