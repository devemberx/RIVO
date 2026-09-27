package com.monsters.mobimon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsters.mobimon.R
import com.monsters.mobimon.core.navigation.AppRoute

private val popupBackground = Color(0xFF142A42)
private val cardBackground = Color(0xFF203C58)
private val popupText = Color(0xFFF4F7FC)
private val popupMuted = Color(0xFFB9CADD)
private val popupAction = Color(0xFF87DAF5)

@Composable
internal fun NotificationPopup(
    notifications: List<NotificationItem>,
    windowWidth: Dp,
    windowHeight: Dp,
    onClose: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val scale = minOf(windowWidth.value / 2560f, windowHeight.value / 1184f)
    val closeFocus = androidx.compose.runtime.remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        closeFocus.requestFocus()
    }
    val panelHeight = if (notifications.isEmpty()) 720f else 950f
    val closeVisualSize = (76 * scale).dp
    val closeTargetSize = closeVisualSize.coerceAtLeast(76.dp)
    val closeInset = (closeTargetSize - closeVisualSize) / 2
    Box(Modifier.fillMaxSize().testTag("notification-overlay")) {
        Box(
            Modifier
                .offset(x = (690 * scale).dp)
                .size(windowWidth - (690 * scale).dp, windowHeight)
                .background(Color(0xB8050C16))
                .clickable(onClick = onClose),
        )
        Box(
            Modifier
                .offset(x = (825 * scale).dp, y = ((if (notifications.isEmpty()) 224f else 114f) * scale).dp)
                .size((1480 * scale).dp, (panelHeight * scale).dp)
                .background(popupBackground, RoundedCornerShape((44 * scale).dp))
                .border((1 * scale).dp, Color(0xE664839F), RoundedCornerShape((44 * scale).dp))
                .testTag("notification-popup"),
        ) {
            Text(
                if (notifications.isEmpty()) "알림" else "알림(${notifications.size})",
                Modifier.offset((64 * scale).dp, (45 * scale).dp).semantics { heading() },
                color = popupText,
                fontSize = (54 * scale).sp,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClose,
                Modifier
                    .offset((1344 * scale).dp - closeInset, (38 * scale).dp - closeInset)
                    .size(closeTargetSize)
                    .focusRequester(closeFocus)
                    .testTag("notification-close"),
            ) {
                Box(
                    Modifier
                        .size(closeVisualSize)
                        .background(cardBackground, CircleShape)
                        .border((2 * scale).dp, Color(0xFF64839F), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.drawer_close),
                        "알림 닫기",
                        Modifier.size((32 * scale).dp),
                        tint = popupText,
                    )
                }
            }
            Box(
                Modifier
                    .offset((64 * scale).dp, (158 * scale).dp)
                    .size((1352 * scale).dp, (2 * scale).dp)
                    .background(Color(0xFF36516F)),
            )
            if (notifications.isEmpty()) {
                Box(
                    Modifier
                        .offset((676 * scale).dp, (286 * scale).dp)
                        .size((128 * scale).dp)
                        .background(cardBackground, CircleShape)
                        .border((2 * scale).dp, Color(0xFF64839F), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.drawer_notification_bell),
                        null,
                        Modifier.size((45 * scale).dp),
                        tint = popupMuted,
                    )
                }
                Text(
                    "새 알림이 없어요",
                    Modifier.offset(y = (450 * scale).dp).width((1480 * scale).dp),
                    color = popupText,
                    fontSize = (46 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "차량 상태와 완료한 퀘스트 소식이 여기에 표시돼요.",
                    Modifier.offset(y = (535 * scale).dp).width((1480 * scale).dp),
                    color = popupMuted,
                    fontSize = (30 * scale).sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .offset((64 * scale).dp, (196 * scale).dp)
                        .size((1352 * scale).dp, (612 * scale).dp)
                        .verticalScroll(scroll)
                        .testTag("notification-list"),
                    verticalArrangement = Arrangement.spacedBy((24 * scale).dp),
                ) {
                    notifications.forEach { item ->
                        NotificationCard(item, scale) { onNavigate(item.route) }
                    }
                }
                if (notifications.size > 3) {
                    Box(
                        Modifier
                            .offset((1431 * scale).dp, (196 * scale).dp)
                            .size((7 * scale).dp, (612 * scale).dp)
                            .background(Color(0xFF36516F), CircleShape),
                    ) {
                        val fraction = scroll.value.toFloat() / scroll.maxValue.coerceAtLeast(1)
                        Box(
                            Modifier
                                .offset(y = (247 * fraction * scale).dp)
                                .size((7 * scale).dp, (365 * scale).dp)
                                .background(popupAction, CircleShape),
                        )
                    }
                }
                Text(
                    "알림은 현재 차량 정보와 받지 않은 퀘스트 보상을 기준으로 표시돼요.",
                    Modifier.offset((64 * scale).dp, (836 * scale).dp),
                    color = popupMuted,
                    fontSize = (29 * scale).sp,
                )
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: NotificationItem,
    scale: Float,
    onClick: () -> Unit,
) {
    val vehicle = item.kind == NotificationKind.VEHICLE
    val accent = if (vehicle) Color(0xFFF5C976) else Color(0xFF71E5C5)
    val routeLabel = if (vehicle) "차량 상태 보기  ›" else "퀘스트 보기  ›"
    Box(
        Modifier
            .width((1352 * scale).dp)
            .height((188 * scale).dp)
            .background(cardBackground, RoundedCornerShape((26 * scale).dp))
            .border((1 * scale).dp, Color(0xA664839F), RoundedCornerShape((26 * scale).dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("notification-${item.kind.name.lowercase()}-${item.id}"),
    ) {
        Box(
            Modifier
                .offset(y = (21 * scale).dp)
                .size((8 * scale).dp, (146 * scale).dp)
                .background(accent),
        )
        Row(
            Modifier.fillMaxSize().padding(start = (48 * scale).dp, end = (40 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.width((900 * scale).dp)) {
                Text(
                    if (vehicle) "차량 상태 · 주의" else "퀘스트 · 완료",
                    color = accent,
                    fontSize = (28 * scale).sp,
                )
                Text(
                    item.title,
                    color = popupText,
                    fontSize = (44 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Text(routeLabel, color = popupAction, fontSize = (30 * scale).sp)
        }
    }
}
