package com.monsters.mobimon.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.monsters.mobimon.BuildConfig
import com.monsters.mobimon.R
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.navigation.CompanionRoute
import com.monsters.mobimon.core.navigation.QuestRoute
import com.monsters.mobimon.core.navigation.VehicleRoute
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonColors
import com.monsters.mobimon.core.ui.MobiMonNotificationBadge
import com.monsters.mobimon.core.ui.MobiMonReferenceText
import com.monsters.mobimon.core.ui.mobiMonReferenceTextStyle

private data class DrawerDestination(
    val label: Int,
    val icon: Int,
    val route: AppRoute?,
)

// Keep the SVG at AAOS compatibility density while preventing overlapping 76dp targets.
private const val MIN_REFERENCE_MENU_SCALE = 76f / 112f

private val destinations =
    listOf(
        DrawerDestination(R.string.drawer_menu_home, R.drawable.drawer_home, CompanionRoute.HOME),
        DrawerDestination(R.string.drawer_menu_notifications, R.drawable.drawer_notification_bell, null),
        DrawerDestination(R.string.drawer_menu_quests, R.drawable.drawer_quest, QuestRoute.QUESTS),
        DrawerDestination(R.string.drawer_menu_vehicle, R.drawable.drawer_vehicle, VehicleRoute.VEHICLE_INFO),
        DrawerDestination(R.string.drawer_menu_appearance, R.drawable.drawer_appearance, CompanionRoute.APPEARANCE),
        DrawerDestination(R.string.drawer_settings, R.drawable.drawer_settings, CompanionRoute.SETTINGS),
    )

private fun drawerProfileName(friendId: String?): Int =
    when (friendId) {
        "friend:mobi" -> R.string.drawer_mobi_name
        "friend:luna" -> R.string.drawer_luna_name
        else -> R.string.drawer_no_friend
    }

@Composable
fun CompanionMenu(
    visible: Boolean,
    currentRoute: AppRoute,
    onClose: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    notifications: List<NotificationItem> = emptyList(),
    onVersionClick: () -> Unit = {},
    activeFriendId: String? = null,
    accessoryId: String? = null,
    outfitId: String? = null,
    backgroundId: String? = null,
) {
    val duration = if (LocalMobiMonMotionEnabled.current) NAVIGATION_MOTION_DURATION_MILLIS else 0
    val first = remember { FocusRequester() }
    val notificationFocus = remember { FocusRequester() }
    val drawer = remember { MutableTransitionState(false) }
    var notificationsOpen by remember { mutableStateOf(false) }
    var openedNotificationsOnce by remember { mutableStateOf(false) }
    val notificationTransition = updateTransition(notificationsOpen, label = "menuNotificationExpansion")
    LaunchedEffect(visible) {
        drawer.targetState = visible
        if (!visible) {
            notificationsOpen = false
            openedNotificationsOnce = false
        }
    }
    LaunchedEffect(notificationsOpen, notificationTransition.currentState, visible) {
        if (visible && !notificationsOpen && !notificationTransition.currentState && openedNotificationsOnce) {
            notificationFocus.requestFocus()
        }
    }
    if (!drawer.currentState && !drawer.targetState) return
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().testTag("menu-host")) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight
        val referenceScale = windowWidth.value / 2560f
        val referenceMenu =
            windowWidth.value >= 1400 &&
                windowHeight.value >= 1184 * referenceScale &&
                LocalDensity.current.fontScale <= 1f &&
                referenceScale >= MIN_REFERENCE_MENU_SCALE
        val collapsedWidth = if (referenceMenu) (690 * referenceScale).dp else minOf(520.dp, windowWidth)
        val notificationScale = minOf(windowWidth.value / 2560f, windowHeight.value / 1184f)
        val expandedWidth = minOf(windowWidth, maxOf(collapsedWidth, (944 * notificationScale).dp))
        val notificationWidth by
            notificationTransition.animateDp(
                transitionSpec = {
                    if (duration == 0) {
                        snap()
                    } else {
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        )
                    }
                },
                label = "notificationPanelWidth",
            ) { if (it) expandedWidth else collapsedWidth }
        val notificationOpacity by
            notificationTransition.animateFloat(
                transitionSpec = {
                    if (duration == 0) {
                        snap()
                    } else {
                        tween(150, delayMillis = if (targetState) 0 else 100, easing = FastOutSlowInEasing)
                    }
                },
                label = "notificationPanelOpacity",
            ) { if (it) 1f else 0f }
        Popup(onDismissRequest = {
            if (notificationsOpen) {
                notificationsOpen = false
            } else if (visible) {
                onClose()
            }
        }, properties = PopupProperties(focusable = true)) {
            // AAOS Popup constraints may exceed the app compatibility-density window.
            Box(Modifier.size(windowWidth, windowHeight)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable(enabled = visible, onClick = {
                            if (notificationsOpen) notificationsOpen = false else onClose()
                        })
                        .testTag("menu-backdrop"),
                )
                AnimatedVisibility(
                    visibleState = drawer,
                    enter =
                        slideInHorizontally(
                            animationSpec = tween(duration, easing = FastOutSlowInEasing),
                            initialOffsetX = { -it },
                        ) + fadeIn(tween(duration)),
                    exit =
                        slideOutHorizontally(
                            animationSpec = tween(duration, easing = FastOutSlowInEasing),
                            targetOffsetX = { -it },
                        ) + fadeOut(tween(duration)),
                ) {
                    MenuPanel(
                        windowWidth.value,
                        windowHeight.value,
                        visible && !notificationsOpen,
                        currentRoute,
                        onClose,
                        onNavigate,
                        onVersionClick,
                        activeFriendId,
                        accessoryId,
                        outfitId,
                        backgroundId,
                        first,
                        notifications.size,
                        {
                            openedNotificationsOnce = true
                            notificationsOpen = true
                        },
                        notificationFocus,
                    )
                }
                if (visible && (notificationTransition.currentState || notificationTransition.targetState)) {
                    NotificationPanel(
                        notifications = notifications,
                        windowWidth = windowWidth,
                        windowHeight = windowHeight,
                        panelWidth = notificationWidth,
                        panelOpacity = notificationOpacity,
                        expanded = notificationsOpen,
                        onBack = { notificationsOpen = false },
                        onClose = onClose,
                        onNavigate = onNavigate,
                    )
                }
            }
            LaunchedEffect(drawer.currentState) {
                if (drawer.currentState) first.requestFocus()
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun MenuPanel(
    windowWidth: Float,
    windowHeight: Float,
    visible: Boolean,
    currentRoute: AppRoute,
    onClose: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onVersionClick: () -> Unit,
    friendId: String?,
    accessoryId: String?,
    outfitId: String?,
    backgroundId: String?,
    first: FocusRequester,
    notificationCount: Int,
    onOpenNotifications: () -> Unit,
    notificationFocus: FocusRequester,
) {
    val referenceScale = windowWidth / 2560f
    val reference =
        windowWidth >= 1400 &&
            windowHeight >= 1184 * referenceScale &&
            LocalDensity.current.fontScale <= 1f &&
            referenceScale >= MIN_REFERENCE_MENU_SCALE
    val scale = if (reference) referenceScale else 1f
    val width = if (reference) 690 * scale else minOf(520f, windowWidth)
    val shape =
        remember(scale) {
            object : Shape {
                override fun createOutline(
                    size: Size,
                    layoutDirection: LayoutDirection,
                    density: Density,
                ): Outline =
                    with(density) {
                        val corner = CornerRadius((70 * scale).dp.toPx(), (61.639f * scale).dp.toPx())
                        // A native rounded outline keeps the elliptical corners and reliable pointer hit testing.
                        Outline.Rounded(
                            RoundRect(
                                0f,
                                0f,
                                size.width,
                                size.height,
                                topRightCornerRadius = corner,
                                bottomRightCornerRadius = corner,
                            ),
                        )
                    }
            }
        }
    Box(
        Modifier
            .width(width.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(Color(0xFF183257))
            .drawWithContent {
                drawContent()
                drawLine(
                    Color(0x6B6F9AD0),
                    Offset(size.width - scale.dp.toPx(), (61.639f * scale).dp.toPx()),
                    Offset(size.width - scale.dp.toPx(), size.height - (61.639f * scale).dp.toPx()),
                    (2 * scale).dp.toPx(),
                )
            }.testTag("companion-menu"),
    ) {
        val portrait: @Composable (Modifier) -> Unit = { modifier ->
            Box(
                modifier
                    .size((154 * scale).dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFF18375D), Color(0xFF0D1E3B))),
                        CircleShape,
                    ).drawWithContent {
                        drawContent()
                        drawCircle(
                            Color(0xAD78A6DB),
                            radius = size.minDimension / 2,
                            style = Stroke((3 * scale).dp.toPx()),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                val isRunaOrLuna = friendId == "friend:luna" || friendId == "friend:runa"
                val faceRes = if (isRunaOrLuna) R.drawable.menu_runa_face else R.drawable.menu_mobi_face
                val imageSize = if (isRunaOrLuna) (115 * scale).dp else (135 * scale).dp
                Image(
                    painterResource(faceRes),
                    contentDescription = null,
                    modifier = Modifier.size(imageSize).offset(y = if (isRunaOrLuna) 0.dp else (-5 * scale).dp),
                )
            }
        }
        val close: @Composable (Modifier) -> Unit = { modifier ->
            IconButton(onClose, modifier.size(76.dp), enabled = visible) {
                Icon(
                    painterResource(R.drawable.drawer_close),
                    stringResource(R.string.close),
                    Modifier.size((40 * scale).dp),
                    tint = Color(0xFFF3F7FF),
                )
            }
        }
        if (reference) {
            Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = windowHeight.dp)) {
                MobiMonReferenceText("MobiMon", 62f, 70f, 48f, scale = scale, bold = true, color = Color(0xFFF7FAFF))
                MobiMonReferenceText(
                    stringResource(R.string.drawer_tagline),
                    62f,
                    119f,
                    26f,
                    scale = scale,
                    color = Color(0xFFB9C9E1),
                )
                close(Modifier.offset((590 * scale).dp - 38.dp, (88 * scale).dp - 38.dp))
                portrait(Modifier.offset((63 * scale).dp, (163 * scale).dp))
                MobiMonReferenceText(
                    stringResource(drawerProfileName(friendId)),
                    244f,
                    222f,
                    34f,
                    Modifier.semantics { heading() },
                    scale,
                    bold = true,
                    color = Color(0xFFF6F9FF),
                )
                MobiMonReferenceText(
                    stringResource(R.string.drawer_profile_subtitle),
                    244f,
                    263f,
                    22f,
                    scale = scale,
                    color = Color(0xFF9FB2CE),
                )
                destinations.forEachIndexed { index, item ->
                    val top = 341f + 112f * index
                    MenuDestination(
                        item,
                        item.route != null && item.route == currentRoute,
                        visible,
                        { if (item.route == null) onOpenNotifications() else onNavigate(item.route) },
                        Modifier
                            .offset((44 * scale).dp, (top * scale).dp)
                            .width((596 * scale).dp)
                            .then(
                                if (item.route ==
                                    null
                                ) {
                                    Modifier.focusRequester(notificationFocus).testTag("menu-notifications")
                                } else {
                                    Modifier
                                },
                            ).then(
                                if (index ==
                                    0
                                ) {
                                    Modifier.focusRequester(first)
                                } else {
                                    Modifier
                                },
                            ),
                        scale,
                        reference = true,
                        notificationCount = if (item.route == null) notificationCount else 0,
                    )
                }
                MenuFooter(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height((173 * scale).dp),
                    scale,
                    true,
                    onVersionClick,
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MobiMon", style = MaterialTheme.typography.headlineLarge, color = MobiMonColors.text)
                        Text(stringResource(R.string.drawer_tagline), color = Color(0xFFB9C9E1))
                    }
                    close(Modifier)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    portrait(Modifier)
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(drawerProfileName(friendId)),
                            Modifier.semantics {
                                heading()
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            color = MobiMonColors.text,
                        )
                        Text(stringResource(R.string.drawer_profile_subtitle), color = Color(0xFF9FB2CE))
                    }
                }
                destinations.forEachIndexed { index, item ->
                    MenuDestination(
                        item,
                        item.route != null && item.route == currentRoute,
                        visible,
                        { if (item.route == null) onOpenNotifications() else onNavigate(item.route) },
                        Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .then(
                                if (item.route ==
                                    null
                                ) {
                                    Modifier.focusRequester(notificationFocus).testTag("menu-notifications")
                                } else {
                                    Modifier
                                },
                            ),
                        scale,
                        reference = false,
                        notificationCount = if (item.route == null) notificationCount else 0,
                    )
                }
                MenuFooter(Modifier.fillMaxWidth(), scale, false, onVersionClick)
            }
        }
    }
}

@Composable
private fun MenuDestination(
    item: DrawerDestination,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    reference: Boolean = false,
    notificationCount: Int = 0,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((24 * scale).dp)
    val visualHeight = (94 * scale).dp
    val targetHeight = visualHeight.coerceAtLeast(76.dp)
    val notificationDescription =
        if (notificationCount == 0) "알림 열기" else "알림 ${notificationCount}건 열기"
    Box(
        modifier
            .offset(y = if (reference) (visualHeight - targetHeight) / 2 else 0.dp)
            .heightIn(min = targetHeight)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .semantics { this.selected = selected }
            .then(
                if (item.route ==
                    null
                ) {
                    Modifier.semantics { contentDescription = notificationDescription }
                } else {
                    Modifier
                },
            ).clickable(enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = visualHeight)
                .clip(shape)
                .then(
                    if (selected) {
                        Modifier.background(
                            Brush.horizontalGradient(listOf(Color(0x2E7ED6FF), Color(0x1CB6D7FF))),
                        )
                    } else {
                        Modifier
                    },
                ).then(if (focused && !selected) Modifier.border(3.dp, MobiMonColors.accent, shape) else Modifier)
                .padding(horizontal = (34 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconSize =
                when (item.route) {
                    CompanionRoute.HOME -> 72
                    CompanionRoute.SETTINGS -> 68
                    null -> 41
                    else -> 65
                }
            Box(Modifier.size((72 * scale).dp), contentAlignment = Alignment.Center) {
                if (item.route == null) {
                    Icon(
                        painterResource(item.icon),
                        null,
                        Modifier.offset(x = if (reference) (-10 * scale).dp else 0.dp).size((iconSize * scale).dp),
                        tint = Color(0xFFD7EEF5),
                    )
                } else {
                    Image(
                        painterResource(item.icon),
                        null,
                        Modifier.offset(y = (1.5f * scale).dp).size((iconSize * scale).dp),
                    )
                }
            }
            Spacer(Modifier.width((20 * scale).dp))
            Text(
                stringResource(item.label),
                style = mobiMonReferenceTextStyle(32f, scale, bold = true),
                color = if (selected) Color(0xFF82D4FF) else Color(0xFFF1F5FC),
                modifier = if (reference) Modifier.offset(y = (-1 * scale).dp) else Modifier,
            )
        }
        if (item.route == null) {
            MobiMonNotificationBadge(
                notificationCount,
                (48 * scale).dp,
                (25 * scale).sp,
                Modifier.align(Alignment.CenterEnd).offset(x = (-42 * scale).dp),
            )
        }
    }
}

@Composable
private fun MenuFooter(
    modifier: Modifier,
    scale: Float,
    reference: Boolean,
    onVersionClick: () -> Unit,
) {
    if (reference) {
        Box(modifier.clickable(role = Role.Button, onClick = onVersionClick).testTag("menu-version")) {
            Box(
                Modifier
                    .offset(
                        (62 * scale).dp,
                        (-1 * scale).dp,
                    ).size((566 * scale).dp, (2 * scale).dp)
                    .background(Color(0x4787A6CB)),
            )
            MobiMonReferenceText("MobiMon", 62f, 57f, 34f, scale = scale, bold = true, color = Color(0xFF7287A8))
            MobiMonReferenceText(
                stringResource(R.string.drawer_tagline),
                62f,
                93f,
                20f,
                scale = scale,
                color = Color(0xFF607696),
            )
            MobiMonReferenceText(
                stringResource(R.string.drawer_version, BuildConfig.VERSION_NAME),
                62f,
                133f,
                20f,
                scale = scale,
                color = Color(0xFF607696),
            )
            Icon(
                painterResource(R.drawable.drawer_footer_car),
                null,
                Modifier.offset((517 * scale).dp, (39 * scale).dp).size((100 * scale).dp, (75 * scale).dp),
                tint = Color(0xFF7189AA),
            )
        }
    } else {
        Column(
            modifier.clickable(role = Role.Button, onClick = onVersionClick).testTag("menu-version"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0x4787A6CB)))
            Text("MobiMon", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF7287A8))
            Text(stringResource(R.string.drawer_tagline), color = Color(0xFF9FB2CE))
            Text(stringResource(R.string.drawer_version, BuildConfig.VERSION_NAME), color = Color(0xFF9FB2CE))
        }
    }
}
