package com.monsters.mobimon.feature.pet

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsters.mobimon.core.domain.PetProfile
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.presentation.VehicleCondition
import com.monsters.mobimon.core.presentation.vehicleCondition
import com.monsters.mobimon.core.ui.FallingParticlesEffect
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.LocalMobiMonNotificationCount
import com.monsters.mobimon.core.ui.MobiMonButton
import com.monsters.mobimon.core.ui.MobiMonColors
import com.monsters.mobimon.core.ui.MobiMonMessage
import com.monsters.mobimon.core.ui.MobiMonNavigationButton
import com.monsters.mobimon.core.ui.MobiMonParkingStatusBadge
import com.monsters.mobimon.core.ui.MobiMonPointSummary
import com.monsters.mobimon.core.ui.ParticleType
import com.monsters.mobimon.core.ui.PetAvatar
import com.monsters.mobimon.core.ui.StarHanger
import com.monsters.mobimon.core.ui.StarlightYarnBasket
import com.monsters.mobimon.core.ui.companionBackgroundRes
import kotlin.math.roundToInt
import com.monsters.mobimon.core.ui.R as CoreUiR

/** Displays the in-app Home from committed state; navigation belongs to the shell. */
@Composable
fun PetHomeScreen(
    profile: PetProfile,
    snapshot: VehicleSnapshot,
    onOpenMenu: () -> Unit,
    onPetClick: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    pointBalance: Long? = null,
    pointLoadFailed: Boolean = false,
    friendId: String? = "friend:mobi",
    accessoryId: String? = null,
    outfitId: String? = null,
    backgroundId: String? = null,
    interactionAllowed: Boolean = false,
    profileObservationFailed: Boolean = false,
    onRetryProfile: () -> Unit = {},
    inventoryLoaded: Boolean = true,
    inventoryLoadFailed: Boolean = false,
    connectionAvailable: Boolean = false,
    backgroundTimeOfDay: String? = snapshot.timeOfDay,
) {
    var bubbleTrigger by remember { mutableIntStateOf(0) }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val windowHeight = maxHeight
            val fontScale = LocalDensity.current.fontScale
            val scale = maxWidth.value / 2560f
            val textShadow =
                if (
                    companionBackgroundRes(backgroundTimeOfDay) == CoreUiR.drawable.pet_home_background_night ||
                    companionBackgroundRes(backgroundTimeOfDay) == CoreUiR.drawable.pet_home_background_midnight
                ) {
                    null
                } else {
                    with(LocalDensity.current) {
                        Shadow(MobiMonColors.background, Offset(0f, 2.dp.toPx()), 4.dp.toPx())
                    }
                }
            val bubbleLeft = (maxWidth - (2560 * scale).dp) / 2 + (1576 * scale).dp
            val referenceLayout = maxWidth >= 1200.dp && maxHeight >= 700.dp && fontScale <= 1f
            HomeBackground(backgroundTimeOfDay, backgroundId)
            val companion: @Composable (Modifier) -> Unit = { companionModifier ->
                HomeCompanion(
                    profile,
                    friendId,
                    accessoryId,
                    outfitId,
                    backgroundId,
                    inventoryLoaded,
                    inventoryLoadFailed,
                    companionModifier,
                    vehicleWarning = snapshot.vehicleCondition() == VehicleCondition.WARNING,
                    vehicleHungry = snapshot.vehicleCondition() == VehicleCondition.LOW_BATTERY,
                    onClick = { bubbleTrigger++ },
                )
            }
            val header: @Composable (Modifier, Float) -> Unit = { headerModifier, headerScale ->
                HomeHeader(snapshot, pointBalance, pointLoadFailed, onOpenMenu, headerModifier, headerScale, textShadow)
            }
            val notices: @Composable () -> Unit = {
                if (profileObservationFailed) {
                    HomeFailure(
                        stringResource(R.string.pet_profile_observation_failed),
                        onRetryProfile,
                    )
                }
                if (inventoryLoadFailed) HomeFailure(stringResource(R.string.pet_inventory_failed), onRetryProfile)
            }
            val action: @Composable (Modifier, Float) -> Unit = { actionModifier, actionScale ->
                HomeConversationAction(
                    connectionAvailable,
                    interactionAllowed,
                    onPetClick,
                    actionModifier,
                    actionScale,
                    referenceLayout,
                )
            }
            if (referenceLayout) {
                // Coordinates are relative to the current SVG's 96-unit top system bar.
                // Artwork scales uniformly; native text and controls retain their minimum sizes.
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = windowHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height((1048 * scale).dp)) {
                        header(Modifier.padding(horizontal = (72 * scale).dp).offset(y = (36 * scale).dp), scale)
                        HomeGreeting(
                            backgroundTimeOfDay,
                            Modifier.align(Alignment.TopCenter).offset(y = (174 * scale).dp),
                            scale,
                            textShadow,
                        )
                        companion(
                            Modifier.align(Alignment.TopCenter).offset(y = (372 * scale).dp).size((600 * scale).dp),
                        )
                        if (friendId != null) {
                            HomeSpeechBubble(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .offset(
                                        x = bubbleLeft,
                                        y = (500 * scale).dp,
                                    ).anchorHomeSpeechBubbleTail((130f * homeSpeechBubbleScale(scale, fontScale)).dp),
                                scale = scale,
                                triggerKey = bubbleTrigger,
                                friendId = friendId,
                                isSick = snapshot.vehicleCondition() == VehicleCondition.WARNING,
                                isHungry = snapshot.vehicleCondition() == VehicleCondition.LOW_BATTERY,
                                backgroundTimeOfDay = backgroundTimeOfDay,
                            )
                        }
                    }
                    action(Modifier, scale)
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        notices()
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(
                            rememberScrollState(),
                        ).heightIn(min = maxHeight)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    header(Modifier, 0.75f)
                    notices()
                    HomeGreeting(backgroundTimeOfDay, Modifier, 0.65f, textShadow)
                    companion(Modifier.size(240.dp))
                    if (friendId != null) {
                        Box(
                            Modifier
                                .widthIn(max = (560 * homeSpeechBubbleScale(0.75f, fontScale)).dp)
                                .fillMaxWidth(),
                        ) {
                            HomeSpeechBubble(
                                scale = 0.75f,
                                triggerKey = bubbleTrigger,
                                friendId = friendId,
                                isSick = snapshot.vehicleCondition() == VehicleCondition.WARNING,
                                isHungry = snapshot.vehicleCondition() == VehicleCondition.LOW_BATTERY,
                                backgroundTimeOfDay = backgroundTimeOfDay,
                            )
                        }
                    }
                    action(Modifier, 0.75f)
                }
            }
        }
    }
}

@Composable
private fun HomeBackground(
    timeOfDay: String?,
    backgroundId: String?,
) {
    Crossfade(
        targetState = companionBackgroundRes(timeOfDay),
        animationSpec = tween(durationMillis = if (LocalMobiMonMotionEnabled.current) 1000 else 0),
        modifier = Modifier.fillMaxSize(),
        label = "pet_home_background_crossfade",
    ) { targetRes ->
        val tintOpacity =
            when (targetRes) {
                CoreUiR.drawable.pet_home_background_sunrise -> 0.08f
                CoreUiR.drawable.pet_home_background_morning -> 0.08f
                CoreUiR.drawable.pet_home_background_day -> 0.12f
                CoreUiR.drawable.pet_home_background_afternoon -> 0.10f
                CoreUiR.drawable.pet_home_background_sunset -> 0.06f
                else -> 0.04f
            }
        Image(
            painterResource(targetRes),
            null,
            Modifier.fillMaxSize().drawWithCache {
                // Keep the horizon clear and crossfade the glass tint with its artwork.
                val glass =
                    Brush.verticalGradient(
                        0f to MobiMonColors.background.copy(alpha = tintOpacity),
                        0.5f to MobiMonColors.background.copy(alpha = tintOpacity * 0.25f),
                        1f to MobiMonColors.background.copy(alpha = tintOpacity * 0.6f),
                    )
                onDrawWithContent {
                    drawContent()
                    drawRect(glass)
                }
            },
            contentScale = ContentScale.Crop,
            alignment = HomeBackgroundAlignment,
        )
    }
    if (backgroundId == "background:starlight_yarn_basket") {
        StarlightYarnBasket(Modifier.fillMaxSize().testTag("home-yarn-basket"))
    } else if (backgroundId == "background:star_hanger") {
        StarHanger(Modifier.fillMaxSize().testTag("home-star-hanger"))
    } else if (backgroundId != null) {
        val particleType =
            when {
                backgroundId.contains("snow") -> ParticleType.SNOW
                backgroundId.contains("petal") || backgroundId.contains("flower") -> ParticleType.PETAL
                else -> ParticleType.STAR
            }
        FallingParticlesEffect(
            particleType = particleType,
            modifier = Modifier.fillMaxSize().testTag("home-background-particles"),
        )
    }
}

// Figma preserves its 1268-high artwork at y=76 under the larger bars. Anchor
// that crop to y=96 content rather than recentering the horizon on each resize.
internal object HomeBackgroundAlignment : Alignment {
    override fun align(
        size: IntSize,
        space: IntSize,
        layoutDirection: LayoutDirection,
    ): IntOffset {
        val scale = space.width / 2560f
        val top = ((1268 * scale - size.height) / 2 - 20 * scale).roundToInt()
        return IntOffset((space.width - size.width) / 2, top.coerceIn(minOf(0, space.height - size.height), 0))
    }
}

@Composable
private fun HomeGreeting(
    backgroundTimeOfDay: String?,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    textShadow: Shadow? = null,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                (
                    3 *
                        scale
                ).dp,
            ),
    ) {
        Text(
            stringResource(R.string.pet_home_greeting),
            style =
                MaterialTheme.typography.displayLarge.copy(
                    fontSize = (64.8f * scale).sp,
                    shadow = textShadow,
                    lineHeight =
                        (
                            86.4f *
                                scale
                        ).sp,
                ),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        AmbientTextHeader(backgroundTimeOfDay, scale = scale)
    }
}

@Composable
private fun HomeCompanion(
    profile: PetProfile,
    friendId: String?,
    accessoryId: String?,
    outfitId: String?,
    backgroundId: String?,
    inventoryLoaded: Boolean,
    inventoryLoadFailed: Boolean,
    modifier: Modifier = Modifier,
    vehicleWarning: Boolean,
    vehicleHungry: Boolean = false,
    onClick: () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            friendId != null ->
                PetAvatar(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClick,
                            ),
                    appearanceKey = profile.appearance.name,
                    friendId = friendId,
                    accessoryId = accessoryId,
                    outfitId = outfitId,
                    backgroundId = backgroundId,
                    vehicleWarning = vehicleWarning,
                    vehicleHungry = vehicleHungry,
                )
            inventoryLoadFailed -> Unit
            !inventoryLoaded ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.padding(24.dp))
                    Text(stringResource(R.string.pet_inventory_loading))
                }
            else -> Text(stringResource(R.string.pet_no_friend), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HomeConversationAction(
    connectionAvailable: Boolean,
    interactionAllowed: Boolean,
    onPetClick: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    noticesAbove: Boolean = false,
) {
    var actionCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val button: @Composable () -> Unit = {
        MobiMonButton(
            onClick = {
                onPetClick(actionCoordinates?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero)
            },
            enabled = connectionAvailable && interactionAllowed,
            modifier =
                Modifier
                    .widthIn(
                        min = (532.8f * scale).dp,
                    ).heightIn(min = (100.8f * scale).dp)
                    .onGloballyPositioned { actionCoordinates = it }
                    .testTag("home-conversation-action"),
        ) {
            Icon(painterResource(R.drawable.pet_chat_icon), null, Modifier.size((33.3f * scale).dp))
            Spacer(Modifier.width((24 * scale).dp))
            Text(
                stringResource(if (connectionAvailable) R.string.pet_talk_action else R.string.pet_talk_unavailable),
                modifier = Modifier.offset(y = (-3 * scale).dp),
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = (34.2f * scale).coerceAtLeast(24f).sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
        }
    }
    val notice: @Composable () -> Unit = {
        if (!connectionAvailable) Text(stringResource(R.string.pet_ai_unavailable), textAlign = TextAlign.Center)
        if (!interactionAllowed) Text(stringResource(R.string.pet_interaction_restricted), textAlign = TextAlign.Center)
    }
    if (noticesAbove) {
        Box(modifier.widthIn(max = 1320.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            button()
            // The fixed gap above the action keeps restrictions visible without moving the scene.
            Row(Modifier.offset(y = (-56 * scale).dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) { notice() }
        }
    } else {
        Column(
            modifier.widthIn(max = 1320.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            button()
            Column(horizontalAlignment = Alignment.CenterHorizontally) { notice() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeHeader(
    snapshot: VehicleSnapshot,
    pointBalance: Long?,
    pointLoadFailed: Boolean,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    textShadow: Shadow? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val notificationCount = LocalMobiMonNotificationCount.current
        val wide = maxWidth / LocalDensity.current.fontScale >= 1100.dp
        val brand: @Composable () -> Unit = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((34 * scale).dp),
            ) {
                Box {
                    MobiMonNavigationButton(
                        painterResource(R.drawable.pet_menu_icon),
                        if (notificationCount > 0) {
                            stringResource(R.string.pet_open_menu_notifications, notificationCount)
                        } else {
                            stringResource(R.string.pet_open_menu)
                        },
                        onOpenMenu,
                        visualSize = (104 * scale).dp,
                        iconSize = (32 * scale).dp,
                        borderWidth = (2 * scale).dp,
                    )
                    if (notificationCount > 0) {
                        Box(
                            Modifier
                                .offset((69 * scale).dp, (19 * scale).dp)
                                .size((12 * scale).dp)
                                .background(Color(0xFF9FE8DA), CircleShape),
                        )
                    }
                }
                Column(Modifier.offset(x = (-2 * scale).dp, y = (-10 * scale).dp)) {
                    Text(
                        stringResource(R.string.pet_brand),
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontSize = (46 * scale).sp,
                                shadow = textShadow,
                                lineHeight =
                                    (
                                        66 *
                                            scale
                                    ).sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.pet_brand_tagline),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                shadow = textShadow,
                                fontSize =
                                    (
                                        28 *
                                            scale
                                    ).coerceAtLeast(24f).sp,
                            ),
                        color = MobiMonColors.muted,
                    )
                }
            }
        }
        val status: @Composable () -> Unit = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy((48 * scale).dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier.heightIn(min = (76 * scale).dp).offset(y = (7 * scale).dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MobiMonPointSummary(
                        pointBalance,
                        failed = pointLoadFailed,
                        scale = scale,
                        shadow = textShadow,
                    )
                }
                HomeParkingStatus(snapshot, scale = scale)
            }
        }
        if (wide) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.align(Alignment.TopStart)) { brand() }
                Box(Modifier.align(Alignment.TopEnd)) { status() }
            }
        } else {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                brand()
                status()
            }
        }
    }
}

@Composable
private fun HomeFailure(
    message: String,
    onRetry: () -> Unit,
) {
    Column(Modifier.widthIn(max = 1320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MobiMonMessage(message, isError = true)
        MobiMonButton(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 76.dp),
        ) { Text(stringResource(R.string.pet_retry)) }
    }
}

/** Initial profile loading and failure use the same Home theme. */
@Composable
fun PetHomeLoadingScreen(
    failed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    parkingBadgeConfirmed: Boolean = false,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints {
            if (failed) {
                val badgeScale = maxWidth.value / 2560f
                MobiMonParkingStatusBadge(
                    confirmed = parkingBadgeConfirmed,
                    modifier =
                        Modifier.align(Alignment.TopEnd).padding(
                            end = 72.dp * badgeScale,
                            top =
                                36.dp * badgeScale,
                        ),
                    scale = badgeScale,
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.pet_brand), style = MaterialTheme.typography.headlineMedium)
                if (failed) {
                    HomeFailure(stringResource(R.string.pet_home_load_failed), onRetry)
                } else {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.pet_home_loading))
                }
            }
        }
    }
}
