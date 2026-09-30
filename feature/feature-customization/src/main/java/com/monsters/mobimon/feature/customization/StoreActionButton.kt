package com.monsters.mobimon.feature.customization

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.ui.MobiMonColors

@Composable
internal fun StoreActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scale: Float = 1f,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick,
        modifier.sizeIn(minHeight = 76.dp),
        enabled = enabled,
        shape = RoundedCornerShape(24.dp * scale),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MobiMonColors.button,
                contentColor = MobiMonColors.onButton,
                disabledContainerColor = MobiMonColors.raised,
                disabledContentColor = MobiMonColors.muted,
            ),
        content = content,
    )
}
