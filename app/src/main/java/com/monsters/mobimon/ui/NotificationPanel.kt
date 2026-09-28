package com.monsters.mobimon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsters.mobimon.R
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.ui.MobiMonReferenceText

private val panelColor = Color(0xFF183257)
private val cardColor = Color(0xFF203B5A)
private val titleColor = Color(0xFFF4F7FC)
private val detailColor = Color(0xFFB9CADD)
private val vehicleColor = Color(0xFFFFD38A)
private val questColor = Color(0xFF9FE8DA)

@Composable
internal fun NotificationPanel(
    notifications: List<NotificationItem>,
    windowWidth: Dp,
    windowHeight: Dp,
    panelWidth: Dp,
    panelOpacity: Float,
    expanded: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val scale = minOf(windowWidth.value / 2560f, windowHeight.value / 1184f)
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(expanded) {
        if (expanded) backFocus.requestFocus()
    }
    val panelShape = RoundedCornerShape(topEnd = (64 * scale).dp, bottomEnd = (64 * scale).dp)
    Box(Modifier.fillMaxSize().testTag("notification-overlay")) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color(0xFF050D19).copy(alpha = 0.5f * panelOpacity)) }
                .clickable(onClick = onClose),
        )
        Box(
            Modifier
                .size(panelWidth, windowHeight)
                .graphicsLayer { alpha = panelOpacity }
                .clip(panelShape)
                .background(panelColor)
                .testTag("notification-panel"),
        ) {
            NotificationPanelContent(
                notifications = notifications,
                scale = scale,
                panelWidth = panelWidth,
                windowHeight = windowHeight,
                backFocus = backFocus,
                onBack = onBack,
                onClose = onClose,
                onNavigate = onNavigate,
            )
        }
    }
}

@Composable
private fun NotificationPanelContent(
    notifications: List<NotificationItem>,
    scale: Float,
    panelWidth: Dp,
    windowHeight: Dp,
    backFocus: FocusRequester,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val closeSize = (76 * scale).dp.coerceAtLeast(76.dp)
    val closeX = (panelWidth - (138 * scale).dp).coerceAtMost(panelWidth - closeSize).coerceAtLeast(0.dp)
    IconButton(
        onClick = onBack,
        modifier =
            Modifier
                .offset((48 * scale).dp, (44 * scale).dp)
                .size((88 * scale).dp.coerceAtLeast(76.dp))
                .focusRequester(backFocus)
                .testTag("notification-back"),
    ) {
        Box(
            Modifier.size((88 * scale).dp).background(Color(0xFF264362), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.notification_back),
                "메뉴로 돌아가기",
                Modifier.size((36 * scale).dp),
                tint = Color(0xFFE2EDF7),
            )
        }
    }
    MobiMonReferenceText("알림", 178f, 108f, 48f, Modifier.semantics { heading() }, scale, true, titleColor)
    IconButton(
        onClick = onClose,
        modifier =
            Modifier
                .offset(closeX, (50 * scale).dp)
                .size(closeSize)
                .testTag("notification-close"),
    ) {
        Icon(
            painterResource(R.drawable.drawer_close),
            "알림 닫기",
            Modifier.size((40 * scale).dp),
            tint = Color(0xFFE2EDF7),
        )
    }
    if (notifications.isEmpty()) {
        NotificationEmptyState(scale, panelWidth)
    } else {
        NotificationList(notifications, scale, panelWidth, windowHeight, onNavigate)
    }
}

@Composable
private fun NotificationEmptyState(
    scale: Float,
    panelWidth: Dp,
) {
    Box(
        Modifier
            .offset(panelWidth / 2 - (64 * scale).dp, (494 * scale).dp)
            .size((128 * scale).dp)
            .background(Color(0xFF203C58), CircleShape)
            .border((1 * scale).dp, Color(0xFF64839F), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.drawer_notification_bell),
            null,
            Modifier.size((45 * scale).dp),
            tint = Color(0xFFBDEDF7),
        )
    }
    Text(
        "새 알림이 없어요",
        Modifier.offset(y = (648 * scale).dp).width(panelWidth),
        color = titleColor,
        fontSize = (46 * scale).sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Text(
        "차량과 퀘스트의 새 소식이 생기면\n여기서 알려드릴게요.",
        Modifier.offset(y = (730 * scale).dp).width(panelWidth),
        color = detailColor,
        fontSize = (30 * scale).sp,
        textAlign = TextAlign.Center,
        lineHeight = (40 * scale).sp,
    )
}

@Composable
private fun NotificationList(
    notifications: List<NotificationItem>,
    scale: Float,
    panelWidth: Dp,
    windowHeight: Dp,
    onNavigate: (AppRoute) -> Unit,
) {
    val vehicle = notifications.filter { it.kind == NotificationKind.VEHICLE }
    val quests = notifications.filter { it.kind == NotificationKind.QUEST }
    val contentWidth = (panelWidth - (128 * scale).dp).coerceAtMost((816 * scale).dp).coerceAtLeast(0.dp)
    Column(
        Modifier
            .offset((64 * scale).dp, (174 * scale).dp)
            .size(contentWidth, (windowHeight - (250 * scale).dp).coerceAtLeast(0.dp))
            .verticalScroll(rememberScrollState())
            .testTag("notification-list"),
    ) {
        if (vehicle.isNotEmpty()) {
            NotificationSection("차량 확인", vehicleColor, scale)
            Spacer(Modifier.height((16 * scale).dp))
            vehicle.forEachIndexed { index, item ->
                NotificationCard(item, scale, contentWidth) { onNavigate(item.route) }
                if (index != vehicle.lastIndex) Spacer(Modifier.height((16 * scale).dp))
            }
        }
        if (quests.isNotEmpty()) {
            if (vehicle.isNotEmpty()) Spacer(Modifier.height((32 * scale).dp))
            NotificationSection("퀘스트 보상", questColor, scale)
            Spacer(Modifier.height((16 * scale).dp))
            quests.forEachIndexed { index, item ->
                NotificationCard(item, scale, contentWidth) { onNavigate(item.route) }
                if (index != quests.lastIndex) Spacer(Modifier.height((16 * scale).dp))
            }
        }
    }
}

@Composable
private fun NotificationSection(
    label: String,
    color: Color,
    scale: Float,
) {
    Box(Modifier.height((38 * scale).dp), contentAlignment = Alignment.CenterStart) {
        Text(label, color = color, fontSize = (28 * scale).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NotificationCard(
    item: NotificationItem,
    scale: Float,
    width: Dp,
    onClick: () -> Unit,
) {
    val accent = if (item.kind == NotificationKind.VEHICLE) vehicleColor else questColor
    Row(
        Modifier
            .size(width, (160 * scale).dp)
            .background(cardColor, RoundedCornerShape((24 * scale).dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = (34 * scale).dp, end = (28 * scale).dp)
            .testTag("notification-${item.kind.name.lowercase()}-${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((30 * scale).dp),
    ) {
        Icon(painterResource(item.iconResource), null, Modifier.size((48 * scale).dp), tint = accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
            Text(
                item.title,
                color = titleColor,
                fontSize = (34 * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.supportingText,
                color = detailColor,
                fontSize = (27 * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painterResource(R.drawable.notification_chevron),
            null,
            Modifier.size((32 * scale).dp),
            tint = Color(0xFFC9DAEB),
        )
    }
}
