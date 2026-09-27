package com.monsters.mobimon.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.monsters.mobimon.R
import com.monsters.mobimon.core.domain.SettingsRepository
import com.monsters.mobimon.core.domain.VehicleRepository
import com.monsters.mobimon.core.presentation.CompanionAppearancePresentation
import com.monsters.mobimon.core.presentation.VehicleCondition
import com.monsters.mobimon.core.presentation.vehicleCondition
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MOBI_RUN_FRAME_DURATION_MS
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.core.ui.PetAvatar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

private const val NOTIFICATION_CHANNEL_ID = "mobimon_floating_companion"
private const val NOTIFICATION_ID = 1001
private const val DEFAULT_OVERLAY_X = 1900
private const val DEFAULT_OVERLAY_Y = 700

/**
 * Floating companion overlay service rendering the companion avatar on top of all screens.
 * Runs as a specialUse foreground service to persist across the launcher and other apps.
 * Fails closed if overlay permission is lost or explicit opt-in preference is disabled.
 */
@AndroidEntryPoint
class FloatingCompanionService : Service() {
    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var companionAppearance: CompanionAppearancePresentation

    @Inject lateinit var vehicleRepository: VehicleRepository

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var isViewAttached = false

    private var isMoving by mutableStateOf(false)
    private var movingLeft by mutableStateOf(true)
    private var vehicleWarning by mutableStateOf(false)
    private var vehicleHungry by mutableStateOf(false)
    private var currentFriendId by mutableStateOf("friend:mobi")

    // Unknown until the first preference read, so stay in place like the in-app shell.
    private var reducedMotion by mutableStateOf(true)
    private var wanderJob: Job? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        startInForeground()
        initOverlayView()
        observeSettings()
        observeVehicleState()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.floating_service_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startInForeground() {
        createNotificationChannel()
        val notification =
            NotificationCompat
                .Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.floating_service_notification_title))
                .setContentText(getString(R.string.floating_service_notification_text))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initOverlayView() {
        val wm =
            getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
                stopSelf()
                return
            }
        val layoutFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        // Preserve the display already assigned to this user/service, but obtain the
        // overlay area's own configuration, density and inset token.
        @Suppress("DEPRECATION")
        val windowContext = createWindowContext(wm.defaultDisplay, layoutFlag, null)
        val overlayWindowManager = windowContext.getSystemService(WindowManager::class.java)
        windowManager = overlayWindowManager

        val params =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.LEFT
                    // Use one full-window coordinate space for insets, dragging and wandering.
                    setFitInsetsTypes(0)
                    x = DEFAULT_OVERLAY_X
                    y = DEFAULT_OVERLAY_Y
                }

        val owner = OverlayLifecycleOwner()
        lifecycleOwner = owner

        val view =
            ComposeView(windowContext).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides owner,
                        LocalMobiMonMotionEnabled provides !reducedMotion,
                    ) {
                        MobiMonTheme {
                            val appearanceState = companionAppearance.state()
                            currentFriendId = appearanceState.friendId
                            Box(
                                modifier =
                                    Modifier
                                        .size(140.dp)
                                        .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                PetAvatar(
                                    modifier = Modifier.fillMaxSize(),
                                    appearanceKey = "GOLDEN",
                                    friendId = appearanceState.friendId,
                                    accessoryId = appearanceState.accessoryId,
                                    outfitId = appearanceState.outfitId,
                                    backgroundId = null,
                                    isAnimated = true,
                                    isMoving = isMoving,
                                    movingLeft = movingLeft,
                                    vehicleWarning = vehicleWarning,
                                    vehicleHungry = vehicleHungry,
                                )
                            }
                        }
                    }
                }
            }

        view.setOnApplyWindowInsetsListener { _, insets ->
            if (isViewAttached) constrainPosition(view, params, overlayWindowManager)
            insets
        }
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (isViewAttached) constrainPosition(view, params, overlayWindowManager)
        }
        setupDragAndTap(view, params, overlayWindowManager)

        try {
            overlayWindowManager.addView(view, params)
            isViewAttached = true
            composeView = view
            overlayParams = params
            view.requestApplyInsets()
            constrainPosition(view, params, overlayWindowManager)
            startWandering(view, params, overlayWindowManager)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndTap(
        view: ComposeView,
        params: WindowManager.LayoutParams,
        wm: WindowManager,
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    wanderJob?.cancel()
                    isMoving = false
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                    }
                    val (x, y) = movementBounds(view, wm).clamp(initialX + dx, initialY + dy)
                    params.x = x
                    params.y = y
                    try {
                        wm.updateViewLayout(view, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val launchIntent =
                            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        }
                    }
                    startWandering(view, params, wm, initialDelayMs = 4000L)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    startWandering(view, params, wm, initialDelayMs = 4000L)
                    false
                }
                else -> false
            }
        }
    }

    private fun startWandering(
        view: ComposeView,
        params: WindowManager.LayoutParams,
        wm: WindowManager,
        initialDelayMs: Long = 4000L,
    ) {
        wanderJob?.cancel()
        wanderJob = null
        isMoving = false
        // Dragging stays available; autonomous wandering stops when reducedMotion is on, or when vehicle is abnormal or hungry.
        if (!FloatingCompanionWanderMath.isWanderingAllowed(reducedMotion, vehicleWarning, vehicleHungry)) return
        wanderJob =
            serviceScope.launch {
                delay(initialDelayMs)
                while (isActive) {
                    isMoving = false
                    val idleDuration = Random.nextLong(3500L, 7000L)
                    delay(idleDuration)
                    if (!isActive) break

                    constrainPosition(view, params, wm)
                    val bounds = movementBounds(view, wm)
                    val minX = bounds.minX
                    val maxX = bounds.maxX
                    val minY = bounds.minY
                    val maxY = bounds.maxY

                    val currentX = params.x
                    val currentY = params.y

                    val preferRight = currentX <= minX + 250
                    val preferLeft = currentX >= maxX - 250
                    val isMobi = currentFriendId == "friend:mobi"
                    val minStep = if (isMobi) 300 else 150
                    val maxStep = if (isMobi) 600 else 450
                    val distance = Random.nextInt(minStep, maxStep)
                    val dy = Random.nextInt(-100, 101)

                    val (targetX, targetY) =
                        FloatingCompanionWanderMath.calculateTargetPosition(
                            currentX = currentX,
                            currentY = currentY,
                            minX = minX,
                            maxX = maxX,
                            minY = minY,
                            maxY = maxY,
                            distance = distance,
                            preferRight = preferRight,
                            preferLeft = preferLeft,
                            goLeft = Random.nextBoolean(),
                            deltaY = dy,
                        )

                    val actualDx = targetX - currentX
                    if (abs(actualDx) < 30) {
                        continue
                    }

                    movingLeft = actualDx < 0
                    isMoving = true

                    val moveDistance = hypot(actualDx.toDouble(), (targetY - currentY).toDouble()).toFloat()
                    val hopCycleMs = (24 * MOBI_RUN_FRAME_DURATION_MS).toLong()
                    val numHops = (moveDistance / 380f).roundToInt().coerceAtLeast(1)
                    val moveDurationMs =
                        if (isMobi) {
                            (numHops * hopCycleMs)
                        } else {
                            ((moveDistance / 250f) * 1000)
                                .toLong()
                                .coerceIn(
                                    800L,
                                    3000L,
                                )
                        }
                    val startTime = SystemClock.uptimeMillis()
                    val startX = currentX
                    val startY = currentY

                    while (isActive) {
                        val elapsed = SystemClock.uptimeMillis() - startTime
                        val fraction = (elapsed.toFloat() / moveDurationMs).coerceIn(0f, 1f)
                        // Insets can change while a wander animation is in progress.
                        val (x, y) =
                            movementBounds(view, wm).clamp(
                                (startX + (targetX - startX) * fraction).toInt(),
                                (startY + (targetY - startY) * fraction).toInt(),
                            )
                        params.x = x
                        params.y = y
                        try {
                            wm.updateViewLayout(view, params)
                        } catch (_: Exception) {
                            break
                        }
                        if (fraction >= 1f) break
                        delay(16L)
                    }

                    isMoving = false
                }
            }
    }

    private fun movementBounds(
        view: ComposeView,
        wm: WindowManager,
    ): OverlayMovementBounds {
        val metrics = wm.currentWindowMetrics
        val insets =
            metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
        val fallbackSize = (140 * view.resources.displayMetrics.density).toInt()
        return overlayMovementBounds(
            metrics.bounds.width(),
            metrics.bounds.height(),
            insets.left,
            insets.top,
            insets.right,
            insets.bottom,
            view.width.takeIf { it > 0 } ?: fallbackSize,
            view.height.takeIf { it > 0 } ?: fallbackSize,
        )
    }

    private fun constrainPosition(
        view: ComposeView,
        params: WindowManager.LayoutParams,
        wm: WindowManager,
    ) {
        val (x, y) = movementBounds(view, wm).clamp(params.x, params.y)
        if (x == params.x && y == params.y) return
        params.x = x
        params.y = y
        try {
            wm.updateViewLayout(view, params)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsRepository.settings
                .catch {
                    reducedMotion = true
                    restartWandering()
                }.collect { settings ->
                    if (!settings.launcherCharacterEnabled ||
                        !Settings.canDrawOverlays(this@FloatingCompanionService)
                    ) {
                        stopSelf()
                        return@collect
                    }
                    if (reducedMotion != settings.reducedMotion) {
                        reducedMotion = settings.reducedMotion
                        restartWandering()
                    }
                }
        }
    }

    private fun observeVehicleState() {
        serviceScope.launch {
            vehicleRepository.snapshots
                .collect { snapshot ->
                    val condition = snapshot.vehicleCondition()
                    val newWarning = (condition == VehicleCondition.WARNING)
                    val newHungry = (condition == VehicleCondition.LOW_BATTERY)
                    if (vehicleWarning != newWarning || vehicleHungry != newHungry) {
                        vehicleWarning = newWarning
                        vehicleHungry = newHungry
                        restartWandering()
                    }
                }
        }
    }

    private fun restartWandering() {
        val view = composeView ?: return
        val params = overlayParams ?: return
        val wm = windowManager ?: return
        startWandering(view, params, wm)
    }

    override fun onDestroy() {
        super.onDestroy()
        wanderJob?.cancel()
        wanderJob = null
        serviceScope.cancel()
        if (isViewAttached) {
            try {
                composeView?.let { windowManager?.removeView(it) }
            } catch (_: Exception) {
            }
            isViewAttached = false
        }
        composeView = null
        overlayParams = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        windowManager = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
    }
}

internal object FloatingCompanionWanderMath {
    fun isWanderingAllowed(
        reducedMotion: Boolean,
        vehicleWarning: Boolean,
        vehicleHungry: Boolean,
    ): Boolean = !reducedMotion && !vehicleWarning && !vehicleHungry

    fun calculateTargetPosition(
        currentX: Int,
        currentY: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
        distance: Int,
        preferRight: Boolean,
        preferLeft: Boolean,
        goLeft: Boolean,
        deltaY: Int,
    ): Pair<Int, Int> {
        val chooseLeft =
            when {
                preferRight -> false
                preferLeft -> true
                else -> goLeft
            }
        val targetX =
            if (chooseLeft) {
                (currentX - distance).coerceIn(minX, maxX)
            } else {
                (currentX + distance).coerceIn(minX, maxX)
            }
        val targetY = (currentY + deltaY).coerceIn(minY, maxY)
        return Pair(targetX, targetY)
    }
}
