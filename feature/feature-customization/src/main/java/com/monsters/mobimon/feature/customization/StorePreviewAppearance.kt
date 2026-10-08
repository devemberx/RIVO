package com.monsters.mobimon.feature.customization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.monsters.mobimon.core.ui.preparePetPreviewArtwork

@Composable
internal fun rememberStorePreviewAppearance(preview: CosmeticPreview): String? {
    val context = LocalContext.current
    val requested = preview.accessoryId ?: preview.outfitId
    var visible by remember(context, preview.friendId) { mutableStateOf(requested) }
    LaunchedEffect(context, preview.friendId, requested) {
        if (requested != visible && preparePetPreviewArtwork(context, preview.friendId, requested)) {
            // A superseded effect is cancelled before it can replace the visible appearance.
            visible = requested
        }
    }
    return visible
}
