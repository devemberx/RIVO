package com.monsters.mobimon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.monsters.mobimon.R
import com.monsters.mobimon.core.ui.BackgroundLayer
import com.monsters.mobimon.core.ui.MobiMonTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** Terminal local failures release startup to the existing Home retry UI, never wait for OAuth or VSS. */
internal data class StartupContent(
    val appearanceResolved: Boolean,
    val homeResolved: Boolean,
    val friendId: String,
    val background: BackgroundLayer,
)

private const val INTRO = 0
private const val SCENE = 1
private const val FACE = 2
private const val EXIT = 3
private const val COMPLETE = 4

/** One launch per saved shell. Activity recreation resumes the current stage, rather than replaying the intro. */
@Composable
internal fun StartupLoadingHost(
    state: StartupContent,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    launchReady: Boolean = true,
    content: @Composable () -> Unit,
) {
    var stage by rememberSaveable { mutableIntStateOf(INTRO) }
    var slow by rememberSaveable { mutableStateOf(false) }
    val currentState by rememberUpdatedState(state)
    val nativeReleased by rememberUpdatedState(launchReady)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val timeline =
        remember {
            Animatable(
                if (stage >= FACE) {
                    2450f
                } else if (stage == SCENE) {
                    1750f
                } else {
                    0f
                },
            )
        }
    val exit = remember { Animatable(if (stage == COMPLETE) 1f else 0f) }
    var still by remember { mutableStateOf(reducedMotion) }
    LaunchedEffect(lifecycle, reducedMotion, stage == COMPLETE) {
        if (stage == COMPLETE) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            still = reducedMotion || coroutineContext[MotionDurationScale]?.scaleFactor == 0f
            launch {
                delay(8_000)
                slow = true
            }
            if (stage == INTRO) {
                // Some warm/platform launches omit native splash callbacks; never block those launches.
                withTimeoutOrNull(500) { snapshotFlow { nativeReleased }.first { it } }
                if (still) {
                    timeline.snapTo(
                        1750f,
                    )
                } else {
                    timeline.animateTo(
                        1750f,
                        tween((1750 - timeline.value).toInt().coerceAtLeast(0), easing = LinearEasing),
                    )
                }
                snapshotFlow { currentState.appearanceResolved }.first { it }
                stage = SCENE
            }
            if (stage == SCENE) {
                timeline.animateTo(2450f, tween(if (still) 140 else 700, easing = LinearEasing))
                stage = FACE
            }
            if (stage == FACE) {
                timeline.animateTo(2810f, tween(if (still) 140 else 360, easing = LinearEasing))
                snapshotFlow { currentState.homeResolved }.first { it }
                stage = EXIT
            }
            if (stage == EXIT) {
                exit.animateTo(1f, tween(if (still) 140 else 320))
                stage = COMPLETE
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        if (stage >= EXIT) {
            Box(Modifier.fillMaxSize().then(if (stage == COMPLETE) Modifier else Modifier.clearAndSetSemantics {})) {
                content()
            }
        }
        if (stage != COMPLETE) {
            MobiMonTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - exit.value }
                        .background(StartupBlue)
                        .testTag("startup-loading")
                        .pointerInput(stage == EXIT) {
                            if (stage != EXIT) return@pointerInput
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                ) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Image(
                            painterResource(R.drawable.startup_common_background),
                            null,
                            Modifier.fillMaxSize().graphicsLayer {
                                val expansion = startupEase((timeline.value - 100) / 600)
                                alpha = startupEase(timeline.value / 300f)
                                scaleX = if (still) 1f else 1.055f - .055f * expansion
                                scaleY = scaleX
                            },
                            contentScale = ContentScale.Crop,
                        )
                        StartupStarlight(time = { timeline.value }, still = still, modifier = Modifier.fillMaxSize())
                        if (stage >= SCENE) {
                            StartupScene(state.background, time = { timeline.value }, still = still)
                        }
                        if (stage >= FACE) {
                            StartupFace(state.friendId, time = { timeline.value }, still = still)
                        }
                        if (slow && stage < EXIT) {
                            TextButton(
                                onClick = { stage = COMPLETE },
                                modifier = Modifier.align(Alignment.BottomCenter).heightIn(min = 76.dp),
                            ) { Text(stringResource(R.string.startup_continue)) }
                        }
                    }
                }
            }
        }
    }
}

internal val StartupBlue = Color(0xFF365681)

internal fun startupEase(value: Float): Float {
    val inverse = 1f - value.coerceIn(0f, 1f)
    return 1f - inverse * inverse * inverse
}

internal fun startupSmooth(value: Float): Float {
    val p = value.coerceIn(0f, 1f)
    return p * p * (3 - 2 * p)
}
