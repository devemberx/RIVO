package com.monsters.mobimon.feature.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.ui.MobiMonButton
import com.monsters.mobimon.core.ui.MobiMonButtonStyle
import com.monsters.mobimon.core.ui.MobiMonColors

@Composable
internal fun StorePurchaseDialog(
    item: CosmeticItem,
    balance: Long?,
    busy: Boolean,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val parentDensity = LocalDensity.current
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalDensity provides parentDensity) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val scale = (maxWidth.value / 2560f).coerceAtLeast(0.5f)
                Column(
                    Modifier
                        .fillMaxWidth(if (maxWidth >= 1600.dp) 0.5f else 0.9f)
                        .widthIn(max = 1280.dp * scale)
                        .heightIn(max = 900.dp * scale)
                        .background(MobiMonColors.panel, RoundedCornerShape(40.dp * scale))
                        .testTag("store-purchase-dialog")
                        .verticalScroll(rememberScrollState())
                        .padding(72.dp * scale),
                    verticalArrangement = Arrangement.spacedBy(32.dp * scale),
                ) {
                    Text(
                        "${cosmeticName(item.id)} 구매할까요?",
                        color = MobiMonColors.text,
                        fontSize = (44f * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("구매 후 적용할 수 있어요.", color = MobiMonColors.muted, fontSize = (32f * scale).sp)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MobiMonColors.border))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("아이템 가격", color = MobiMonColors.muted, fontSize = (32f * scale).sp)
                        Text(
                            "${item.price} P",
                            color = MobiMonColors.text,
                            fontSize = (36f * scale).sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("구매 후 포인트", color = MobiMonColors.muted, fontSize = (32f * scale).sp)
                        Text(
                            balance?.let {
                                "${it - item.price} P"
                            } ?: "확인 중",
                            color = MobiMonColors.accent,
                            fontSize = (36f * scale).sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 32.dp * scale),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                32.dp * scale,
                            ),
                    ) {
                        MobiMonButton(
                            onDismiss,
                            Modifier.weight(1f).heightIn(min = 104.dp * scale),
                            enabled = !busy,
                            style = MobiMonButtonStyle.SECONDARY,
                        ) {
                            Text("취소", fontSize = (40f * scale).sp)
                        }
                        MobiMonButton(
                            onConfirm,
                            Modifier.weight(1f).heightIn(min = 104.dp * scale),
                            enabled = enabled && !busy,
                        ) {
                            Text("구매하기", fontSize = (40f * scale).sp)
                        }
                    }
                }
            }
        }
    }
}
