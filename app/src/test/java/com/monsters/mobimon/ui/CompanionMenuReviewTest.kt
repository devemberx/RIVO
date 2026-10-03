package com.monsters.mobimon.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.navigation.CompanionRoute
import com.monsters.mobimon.core.navigation.QuestRoute
import com.monsters.mobimon.core.navigation.VehicleRoute
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.testing.ComposeTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import com.monsters.mobimon.core.ui.R as CoreUiR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ComposeTestApplication::class, qualifiers = "ko-rKR-w2560dp-h1184dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompanionMenuReviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun menuReferenceRender() {
        show(notifications = referenceAlerts())
        val panel = compose.onNodeWithTag("companion-menu").fetchSemanticsNode().boundsInRoot
        assertEquals(690f, panel.width, 1f)
        assertEquals(1184f, panel.height, 1f)
        val home = compose.onNodeWithText("홈").fetchSemanticsNode().boundsInRoot
        assertEquals(44f, home.left, 1f)
        assertEquals(341f, home.top, 1f)
        assertEquals(596f, home.width, 1f)
        assertEquals(94f, home.height, 1f)
        val name = compose.onNodeWithText("모비").fetchSemanticsNode().boundsInRoot
        assertEquals(244f, name.left, 1f)
        val close = compose.onNodeWithContentDescription("닫기").fetchSemanticsNode().boundsInRoot
        assertEquals(552f, close.left, 1f)
        assertEquals(50f, close.top, 1f)
        val alerts = compose.onNodeWithTag("menu-notifications").fetchSemanticsNode().boundsInRoot
        assertEquals(453f, alerts.top, 1f)
        assertMenuIconAndBadgeAligned()
        compose.onNodeWithText("v0.1.0").assertIsDisplayed()
        capture("menu")
    }

    @Test fun lasMenuRendersLasName() {
        show(activeFriendId = "friend:las")
        compose.onNodeWithText("라스").assertIsDisplayed()
        capture("menu-las")
    }

    @Test fun emptyNotificationPanelMatchesReferenceBounds() {
        show()
        compose.onNodeWithContentDescription("알림 열기").performClick()
        val popup = compose.onNodeWithTag("notification-panel").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, popup.left, 1f)
        assertEquals(0f, popup.top, 1f)
        assertEquals(944f, popup.width, 1f)
        assertEquals(1184f, popup.height, 1f)
        compose.onNodeWithText("새 알림이 없어요").assertIsDisplayed()
        capture("notifications-empty")
        compose.onNodeWithContentDescription("메뉴로 돌아가기").performClick()
        compose.onNodeWithTag("notification-panel").assertDoesNotExist()
        compose.onNodeWithTag("menu-notifications").assertIsDisplayed()
    }

    @Test fun notificationPanelExpandsFromMenuWidth() {
        show(motionEnabled = true, notifications = referenceAlerts())
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("알림 3건 열기").performClick()
        compose.mainClock.advanceTimeBy(16)
        val start =
            compose
                .onNodeWithTag("notification-panel")
                .fetchSemanticsNode()
                .boundsInRoot.width
        assertTrue(start in 690f..944f)
        compose.onNodeWithText("차량 확인").assertIsDisplayed()
        compose.onNodeWithTag("notification-vehicle-battery").assertIsDisplayed()
        capture("notifications-expanding-start", assertContentVisible = true)
        compose.mainClock.advanceTimeBy(120)
        val expanding =
            compose
                .onNodeWithTag("notification-panel")
                .fetchSemanticsNode()
                .boundsInRoot.width
        assertTrue("panel should widen in place: $start -> $expanding", expanding > start && expanding < 944f)
        val close = compose.onNodeWithTag("notification-close").fetchSemanticsNode().boundsInRoot
        assertTrue(close.right <= expanding + 1f)
        compose.onNodeWithText("차량 확인").assertIsDisplayed()
        capture("notifications-expanding", assertContentVisible = true)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val expanded =
            compose
                .onNodeWithTag("notification-panel")
                .fetchSemanticsNode()
                .boundsInRoot.width
        assertEquals(944f, expanded, 1f)
        compose.onNodeWithText("차량 확인").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("메뉴로 돌아가기").performClick()
        compose.mainClock.advanceTimeBy(16)
        val collapseStart =
            compose
                .onNodeWithTag("notification-panel")
                .fetchSemanticsNode()
                .boundsInRoot.width
        compose.onNodeWithText("차량 확인").assertIsDisplayed()
        capture("notifications-collapsing-start", assertContentVisible = true)
        compose.mainClock.advanceTimeBy(120)
        val collapsing =
            compose
                .onNodeWithTag("notification-panel")
                .fetchSemanticsNode()
                .boundsInRoot.width
        assertTrue(
            "panel should narrow in place: $collapseStart -> $collapsing",
            collapsing < collapseStart && collapsing > 690f,
        )
        compose.onNodeWithText("차량 확인").assertIsDisplayed()
        capture("notifications-collapsing", assertContentVisible = true)
        compose.mainClock.advanceTimeBy(120)
        capture("notifications-collapsing-end", assertContentVisible = true)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("notification-panel").assertDoesNotExist()
        compose.onNodeWithTag("menu-notifications").assertIsDisplayed()
    }

    @Test fun manyNotificationsScrollToMore() {
        var selected: AppRoute? = null
        val alerts =
            referenceAlerts() +
                listOf(
                    NotificationItem("quest-1", "배터리 지킴이 완료", NotificationKind.QUEST),
                    NotificationItem("quest-2", "네 바퀴의 균형 완료", NotificationKind.QUEST),
                )
        show(onNavigate = { selected = it }, notifications = alerts)
        compose.onNodeWithContentDescription("알림 5건 열기").performClick()
        compose.onNodeWithText("차량 확인").assertIsDisplayed()
        compose.onNodeWithText("퀘스트 보상").assertIsDisplayed()
        val list = compose.onNodeWithTag("notification-list").fetchSemanticsNode().boundsInRoot
        assertEquals(816f, list.width, 1f)
        assertEquals(934f, list.height, 1f)
        capture("notifications-many")
        compose.onNodeWithTag("notification-quest-quest-2").performScrollTo().performClick()
        assertEquals(QuestRoute.QUESTS, selected)
    }

    @Test fun threeNotificationPanelShowsVehicleAndQuestCards() {
        show(notifications = referenceAlerts())
        capture("menu-three")
        compose.onNodeWithContentDescription("알림 3건 열기").performClick()
        compose.onNodeWithText("차량 확인").assertIsDisplayed()
        compose.onNodeWithText("퀘스트 보상").assertIsDisplayed()
        compose.onNodeWithTag("notification-quest-pre-drive").assertIsDisplayed()
        capture("notifications-three")
    }

    @Test fun vehicleNotificationRoutesToVehicle() {
        var selected: AppRoute? = null
        show(
            onNavigate = { selected = it },
            notifications = listOf(NotificationItem("battery", "배터리 잔량을 확인해 주세요", NotificationKind.VEHICLE)),
        )
        compose.onNodeWithContentDescription("알림 1건 열기").performClick()
        compose.onNodeWithTag("notification-vehicle-battery").performClick()
        assertEquals(VehicleRoute.VEHICLE_INFO, selected)
    }

    @Test
    @Config(qualifiers = "ko-rKR-w1792dp-h829dp-mdpi")
    fun aaosCompatibilityDensityPreservesReferenceGeometryAndSeparateTargets() {
        show()
        val scale = 0.7f
        val panel = compose.onNodeWithTag("companion-menu").fetchSemanticsNode().boundsInRoot
        assertEquals(690f * scale, panel.width, 1f)
        val name =
            compose
                .onNodeWithText("모비")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals(244f * scale, name.left, 1f)
        compose.onNodeWithContentDescription("닫기").assertIsDisplayed()
        compose.onNodeWithText("v0.1.0").assertIsDisplayed()
        val labels = listOf("홈", "알림", "퀘스트", "차량 상태", "꾸미기", "설정")
        val bounds =
            labels.mapIndexed { index, label ->
                val row =
                    compose
                        .onNodeWithText(
                            label,
                        ).assertIsDisplayed()
                        .assertHeightIsAtLeast(76.dp)
                        .fetchSemanticsNode()
                        .boundsInRoot
                val top = 341f + 112f * index
                assertEquals((top + 47) * scale, row.center.y, 1f)
                row
            }
        bounds.zipWithNext().forEach { (upper, lower) -> assertTrue(upper.bottom <= lower.top) }
        capture("menu-aaos-density")
    }

    @Test
    @Config(qualifiers = "ko-rKR-w1792dp-h829dp-mdpi")
    fun notificationCountRemainsVisibleInReflowedMenu() {
        show(fontScale = 1.5f, notifications = referenceAlerts())
        assertMenuIconAndBadgeAligned()
        val row = compose.onNodeWithTag("menu-notifications").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val bitmap = popupBitmap()
            try {
                val badgeColor = android.graphics.Color.rgb(0xBD, 0xED, 0xF7)
                val visible =
                    (row.top.toInt() until row.bottom.toInt()).any { y ->
                        ((row.right - 100f).toInt() until row.right.toInt()).any { x ->
                            bitmap.getPixel(x, y) == badgeColor
                        }
                    }
                assertTrue("notification count badge is clipped in the reflowed menu", visible)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    @Config(qualifiers = "ko-rKR-w393dp-h852dp-mdpi")
    fun compactMenuAlignsBellAndCountWithOtherControls() {
        show(notifications = referenceAlerts())
        assertMenuIconAndBadgeAligned()
    }

    @Test
    @Config(qualifiers = "ko-rKR-w1792dp-h829dp-mdpi")
    fun notificationPanelKeepsTargetsReachableAtEnlargedText() {
        show(fontScale = 1.5f, notifications = referenceAlerts())
        compose.onNodeWithTag("menu-notifications").assertWidthIsAtLeast(76.dp).assertHeightIsAtLeast(76.dp)
        compose.onNodeWithContentDescription("알림 3건 열기").performClick()
        compose.onNodeWithTag("notification-close").assertWidthIsAtLeast(76.dp).assertHeightIsAtLeast(76.dp)
        compose.onNodeWithTag("notification-quest-pre-drive").assertIsDisplayed()
        capture("notifications-aaos-enlarged")
    }

    @Test
    @Config(qualifiers = "ko-rKR-w1792dp-h829dp-mdpi")
    fun aaosMenuKeepsFooterAndSettingsReachableAtEnlargedText() {
        show(fontScale = 1.5f)
        capture("menu-aaos-enlarged-text")
        compose.onNodeWithText("설정").performScrollTo().assertIsDisplayed()
        capture("menu-aaos-enlarged-text-settings")
        compose.onNodeWithText("v0.1.0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("닫기").performScrollTo().performClick()
        compose.onNodeWithTag("companion-menu").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "ko-rKR-w1792dp-h829dp-mdpi")
    fun aaosMenuKeepsDestinationTouchTargetsSeparate() {
        var selected: AppRoute? = null
        show(onNavigate = { selected = it })
        val labels = listOf("홈", "알림", "퀘스트", "차량 상태", "꾸미기", "설정")
        capture("menu-aaos-touch-targets")
        labels.zipWithNext().forEach { (upperLabel, lowerLabel) ->
            val lower =
                compose
                    .onNodeWithText(lowerLabel)
                    .performScrollTo()
                    .fetchSemanticsNode()
                    .boundsInRoot
            val upper = compose.onNodeWithText(upperLabel).fetchSemanticsNode().boundsInRoot
            assertTrue("$upperLabel $upper overlaps $lowerLabel $lower", upper.bottom <= lower.top)
        }

        compose.onNodeWithText("홈").performScrollTo().performTouchInput { click(Offset(width / 2f, height - 5f)) }
        assertEquals(CompanionRoute.HOME, selected)
    }

    private fun show(
        fontScale: Float = 1f,
        onNavigate: (AppRoute) -> Unit = {},
        notifications: List<NotificationItem> = emptyList(),
        motionEnabled: Boolean = false,
        activeFriendId: String = "friend:mobi",
    ) {
        val visible = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(
                LocalMobiMonMotionEnabled provides motionEnabled,
                LocalDensity provides Density(1f, fontScale),
            ) {
                MobiMonTheme {
                    Box(Modifier.fillMaxSize()) {
                        Image(
                            painterResource(CoreUiR.drawable.pet_background_lake_park_night),
                            null,
                            Modifier.fillMaxSize(),
                        )
                        CompanionMenu(
                            visible.value,
                            CompanionRoute.HOME,
                            { visible.value = false },
                            onNavigate,
                            notifications = notifications,
                            activeFriendId = activeFriendId,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun referenceAlerts() =
        listOf(
            NotificationItem("battery", "배터리 잔량이 낮아요", NotificationKind.VEHICLE),
            NotificationItem("tire", "타이어 공기압 확인이 필요해요", NotificationKind.VEHICLE),
            NotificationItem("pre-drive", "차량 건강검진 완료", NotificationKind.QUEST),
        )

    private fun assertMenuIconAndBadgeAligned() {
        val home = compose.onNodeWithTag("menu-home-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bell =
            compose
                .onNodeWithTag(
                    "menu-notification-icon",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        val close = compose.onNodeWithContentDescription("닫기").fetchSemanticsNode().boundsInRoot
        val badge =
            compose
                .onNodeWithTag(
                    "menu-notification-count",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        assertEquals(home.center.x, bell.center.x, 1f)
        assertEquals(close.center.x, badge.center.x, 1f)
    }

    private fun capture(
        name: String,
        assertContentVisible: Boolean = false,
    ) {
        compose.runOnIdle {
            val bitmap = popupBitmap()
            if (assertContentVisible) {
                var brightPixels = 0
                for (y in 20 until 145) {
                    for (x in 30 until 500) {
                        val pixel = bitmap.getPixel(x, y)
                        if (
                            android.graphics.Color.red(pixel) > 150 &&
                            android.graphics.Color.green(pixel) > 150 &&
                            android.graphics.Color.blue(pixel) > 150
                        ) {
                            brightPixels++
                        }
                    }
                }
                assertTrue("$name has no visible menu or notification header", brightPixels > 100)
            }
            val directory = File("build/reports/menu-ui").apply { mkdirs() }
            File(
                directory,
                "$name.png",
            ).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }

    private fun popupBitmap(): Bitmap {
        val roots = WindowInspector.getGlobalWindowViews()
        val popup = roots.last { it.javaClass.simpleName == "PopupLayout" }
        return Bitmap.createBitmap(popup.width, popup.height, Bitmap.Config.ARGB_8888).also {
            popup.draw(Canvas(it))
        }
    }
}
