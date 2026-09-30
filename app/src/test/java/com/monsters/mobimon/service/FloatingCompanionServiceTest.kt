package com.monsters.mobimon.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import com.monsters.mobimon.core.domain.CompanionSettings
import com.monsters.mobimon.core.domain.SettingsRepository
import com.monsters.mobimon.core.domain.WriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FloatingCompanionServiceTest {
    @Test
    fun overlayHidesOnlyWhileMobiMonIsInForeground() {
        assertFalse(FloatingCompanionVisibility.shouldShow(appInForeground = true))
        assertTrue(FloatingCompanionVisibility.shouldShow(appInForeground = false))
    }

    @Test
    fun wanderingDisallowedWhenReducedMotionOrWarningOrHungry() {
        assertTrue(
            FloatingCompanionWanderMath.isWanderingAllowed(
                reducedMotion = false,
                vehicleWarning = false,
                vehicleHungry = false,
            ),
        )
        assertFalse(
            FloatingCompanionWanderMath.isWanderingAllowed(
                reducedMotion = true,
                vehicleWarning = false,
                vehicleHungry = false,
            ),
        )
        assertFalse(
            FloatingCompanionWanderMath.isWanderingAllowed(
                reducedMotion = false,
                vehicleWarning = true,
                vehicleHungry = false,
            ),
        )
        assertFalse(
            FloatingCompanionWanderMath.isWanderingAllowed(
                reducedMotion = false,
                vehicleWarning = false,
                vehicleHungry = true,
            ),
        )
    }

    @Test
    fun overlayLifecycleOwnerInitializesInResumedStateAndDestroysCleanly() {
        val owner = OverlayLifecycleOwner()
        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)
        assertNotNull(owner.savedStateRegistry)
        assertNotNull(owner.viewModelStore)

        // Store a test ViewModel to verify cleanup
        class TestViewModel : ViewModel() {
            var cleared = false

            override fun onCleared() {
                cleared = true
            }
        }
        val testVm = TestViewModel()
        owner.viewModelStore.put("key", testVm)

        owner.destroy()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        assertEquals(true, testVm.cleared)
    }

    @Test
    fun serviceFailsClosedWithoutOverlayPermission() {
        val service = FloatingCompanionService()
        // In Robolectric test without SYSTEM_ALERT_WINDOW granted by default:
        val result = service.onStartCommand(null, 0, 1)
        // Service should return START_NOT_STICKY and stop itself when permission is missing
        assertEquals(android.app.Service.START_NOT_STICKY, result)
    }

    @Test
    fun wanderMathPrefersRightWhenNearLeftBoundary() {
        val (targetX, _) =
            FloatingCompanionWanderMath.calculateTargetPosition(
                currentX = 150,
                currentY = 500,
                minX = 100,
                maxX = 2200,
                minY = 150,
                maxY = 1100,
                distance = 200,
                preferRight = true,
                preferLeft = false,
                goLeft = true, // overridden by preferRight
                deltaY = 0,
            )
        assertEquals(350, targetX)
    }

    @Test
    fun wanderMathPrefersLeftWhenNearRightBoundary() {
        val (targetX, _) =
            FloatingCompanionWanderMath.calculateTargetPosition(
                currentX = 2100,
                currentY = 500,
                minX = 100,
                maxX = 2200,
                minY = 150,
                maxY = 1100,
                distance = 200,
                preferRight = false,
                preferLeft = true,
                goLeft = false, // overridden by preferLeft
                deltaY = 0,
            )
        assertEquals(1900, targetX)
    }

    @Test
    fun wanderMathClampsPositionWithinBounds() {
        val (targetX, targetY) =
            FloatingCompanionWanderMath.calculateTargetPosition(
                currentX = 200,
                currentY = 200,
                minX = 100,
                maxX = 2200,
                minY = 150,
                maxY = 1100,
                distance = 500,
                preferRight = false,
                preferLeft = false,
                goLeft = true,
                deltaY = -300,
            )
        assertEquals(100, targetX)
        assertEquals(150, targetY)
    }

    private class FakeSettingsRepository : SettingsRepository {
        val settingsFlow = MutableStateFlow(CompanionSettings(launcherCharacterEnabled = false))
        override val settings: Flow<CompanionSettings> = settingsFlow

        override suspend fun setReducedMotion(enabled: Boolean): WriteResult = WriteResult.Success

        override suspend fun setLauncherCharacterEnabled(enabled: Boolean): WriteResult = WriteResult.Success

        override suspend fun setDebugModeEnabled(enabled: Boolean): WriteResult = WriteResult.Success
    }
}
