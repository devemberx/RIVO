package com.monsters.mobimon.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

val LocalMobiMonNotificationCount = compositionLocalOf { 0 }

@Composable
fun MobiMonNotificationBadge(
    count: Int,
    diameter: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    Box(
        modifier.size(diameter).background(Color(0xFFE6505B), CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}
