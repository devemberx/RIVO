package com.monsters.mobimon.ui

import android.app.Application
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
import androidx.compose.ui.test.assertIsNotDisplayed
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
@Config(sdk = [34], application = Application::class, qualifiers = "ko-rKR-w2560dp-h1184dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompanionMenuReviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun menuReferenceRender() {
        show()
        val panel = compose.onNodeWithTag("companion-menu").fetchSemanticsNode().boundsInRoot
        assertEquals(690f, panel.width, 1f)
        assertEquals(1184f, panel.height, 1f)
        val home = compose.onNodeWithText("홈").fetchSemanticsNode().boundsInRoot
        assertEquals(44f, home.left, 1f)
        assertEquals(332f, home.top, 1f)
        assertEquals(596f, home.width, 1f)
        assertEquals(94f, home.height, 1f)
        val name = compose.onNodeWithText("모비").fetchSemanticsNode().boundsInRoot
        assertEquals(244f, name.left, 1f)
        compose.onNodeWithText("v0.1.0").assertIsDisplayed()
        capture("menu")
    }

    @Test fun emptyNotificationPopupMatchesReferenceBounds() {
        show()
        compose.onNodeWithContentDescription("알림 열기").performClick()
        val popup = compose.onNodeWithTag("notification-popup").fetchSemanticsNode().boundsInRoot
        assertEquals(825f, popup.left, 1f)
        assertEquals(224f, popup.top, 1f)
        assertEquals(1480f, popup.width, 1f)
        assertEquals(720f, popup.height, 1f)
        compose.onNodeWithText("새 알림이 없어요").assertIsDisplayed()
        capture("notifications-empty")
        compose.onNodeWithContentDescription("알림 닫기").performClick()
        compose.onNodeWithTag("notification-popup").assertDoesNotExist()
    }

    @Test fun manyNotificationsKeepThreeVisibleAndScrollToMore() {
        var selected: AppRoute? = null
        val alerts =
            referenceAlerts() +
                listOf(
                    NotificationItem("washer", "워셔액 확인이 필요해요", NotificationKind.VEHICLE),
                    NotificationItem("quest-2", "안전 운전 완료", NotificationKind.QUEST),
                )
        show(onNavigate = { selected = it }, notifications = alerts)
        compose.onNodeWithContentDescription("알림 5건 열기").performClick()
        compose.onNodeWithText("알림(5)").assertIsDisplayed()
        val list = compose.onNodeWithTag("notification-list").fetchSemanticsNode().boundsInRoot
        assertEquals(612f, list.height, 1f)
        compose.onNodeWithTag("notification-quest-quest-2").assertIsNotDisplayed()
        capture("notifications-many")
        compose.onNodeWithTag("notification-quest-quest-2").performScrollTo().performClick()
        assertEquals(QuestRoute.QUESTS, selected)
    }

    @Test fun threeNotificationPopupShowsVehicleAndQuestCards() {
        show(notifications = referenceAlerts())
        compose.onNodeWithContentDescription("알림 3건 열기").performClick()
        compose.onNodeWithText("알림(3)").assertIsDisplayed()
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
        val labels = listOf("홈", "대화하기", "퀘스트", "차량 상태", "꾸미기", "설정")
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
                val top = if (index == 0) 332f else 336f + 112f * index
                assertEquals((top + 47) * scale, row.center.y, 1f)
                row
            }
        bounds.zipWithNext().forEach { (upper, lower) -> assertTrue(upper.bottom <= lower.top) }
        capture("menu-aaos-density")
    }

    @Test
    @Config(qualifiers = "ko-rKR-w1792dp-h829dp-mdpi")
    fun notificationPopupKeepsTargetsReachableAtEnlargedText() {
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
        val labels = listOf("홈", "대화하기", "퀘스트", "차량 상태", "꾸미기", "설정")
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
    ) {
        val visible = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(
                LocalMobiMonMotionEnabled provides false,
                LocalDensity provides Density(1f, fontScale),
            ) {
                MobiMonTheme {
                    Box(Modifier.fillMaxSize()) {
                        Image(painterResource(CoreUiR.drawable.pet_home_background_night), null, Modifier.fillMaxSize())
                        CompanionMenu(
                            visible.value,
                            CompanionRoute.HOME,
                            { visible.value = false },
                            onNavigate,
                            notifications = notifications,
                            activeFriendId = "friend:mobi",
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun referenceAlerts() =
        listOf(
            NotificationItem("battery", "배터리 잔량을 확인해 주세요", NotificationKind.VEHICLE),
            NotificationItem("tire", "타이어 상태를 확인해 주세요", NotificationKind.VEHICLE),
            NotificationItem("pre-drive", "출발 전 차 살피기 완료", NotificationKind.QUEST),
        )

    private fun capture(name: String) {
        compose.runOnIdle {
            val roots = WindowInspector.getGlobalWindowViews()
            val popup = roots.last { it.javaClass.simpleName == "PopupLayout" }
            val bitmap = Bitmap.createBitmap(popup.width, popup.height, Bitmap.Config.ARGB_8888)
            popup.draw(Canvas(bitmap))
            val directory = File("build/reports/menu-ui").apply { mkdirs() }
            File(
                directory,
                "$name.png",
            ).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
