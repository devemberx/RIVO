package com.monsters.mobimon.quest

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.presentation.CompanionAppearanceState
import com.monsters.mobimon.core.presentation.PointBalanceState
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.feature.quest.QuestRewardSuccess
import com.monsters.mobimon.feature.quest.QuestScreen
import com.monsters.mobimon.feature.quest.QuestScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class QuestRewardLayoutDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Suppress("DEPRECATION")
    fun allEquippedRewardsShareLayoutAndLargeTextStaysInsideDialog() {
        var friend by mutableStateOf("friend:mobi")
        var accessory by mutableStateOf<String?>(null)
        var fontScale by mutableStateOf(1f)
        var bonus by mutableStateOf(0L)
        lateinit var view: View
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            CompositionLocalProvider(
                LocalMobiMonMotionEnabled provides false,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                MobiMonTheme {
                    val equipment =
                        buildMap {
                            put(CosmeticSlot.FRIEND, friend)
                            accessory?.let { put(CosmeticSlot.ACCESSORY, it) }
                        }
                    val state =
                        QuestScreenState(
                            rewardSuccess = QuestRewardSuccess("layout-review", 15 + bonus, basePoints = 15),
                            appearance = CompanionAppearanceState(CosmeticInventory(emptySet(), equipment)),
                            pointBalance = PointBalanceState.Ready(120),
                            parkedVerified = true,
                            canClaim = true,
                        )
                    QuestScreen(state, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }
        val footer = compose.onNodeWithTag("quest-modal-btn-confirm").fetchSemanticsNode().boundsInRoot
        val looks =
            listOf(
                "friend:mobi" to null,
                "friend:mobi" to "accessory:mobi_headphones",
                "friend:mobi" to "accessory:mobi_goggles",
                "friend:luna" to null,
                "friend:luna" to "accessory:luna_cap",
                "friend:luna" to "accessory:luna_sunglasses",
                "friend:las" to null,
            )
        looks.forEachIndexed { index, (id, item) ->
            compose.runOnIdle {
                friend = id
                accessory = item
            }
            assertEquals(footer, compose.onNodeWithTag("quest-modal-btn-confirm").fetchSemanticsNode().boundsInRoot)
            capture("look-$index")
        }
        val resources = view.resources
        val originalConfiguration = Configuration(resources.configuration)
        try {
            compose.runOnIdle {
                // A platform Dialog creates a new view whose density comes from Resources.
                resources.updateConfiguration(
                    Configuration(originalConfiguration).apply { this.fontScale = 2f },
                    resources.displayMetrics,
                )
                friend = "friend:luna"
                accessory = "accessory:luna_cap"
                fontScale = 2f
                bonus = 3
            }
            val titleLayouts = mutableListOf<TextLayoutResult>()
            compose
                .onNodeWithTag("quest-reward-title", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayouts) }
            assertEquals(
                "Dialog uses the requested font scale",
                2f,
                titleLayouts
                    .single()
                    .layoutInput.density.fontScale,
                0.01f,
            )
            val modal = compose.onNodeWithTag("quest-reward-success-modal").fetchSemanticsNode().boundsInRoot
            compose.onAllNodesWithText("날씨 보너스", substring = true, useUnmergedTree = true).assertCountEquals(1)
            for (tag in listOf(
                "quest-reward-badge",
                "quest-reward-title",
                "quest-reward-subtitle",
                "quest-reward-points",
                "quest-modal-btn-confirm",
            )) {
                val node = compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
                assertTrue(
                    "$tag stays inside dialog",
                    modal.contains(node.boundsInRoot.topLeft) && modal.contains(node.boundsInRoot.bottomRight),
                )
            }
            capture("cap-font-2-bonus")
        } finally {
            compose.runOnIdle { resources.updateConfiguration(originalConfiguration, resources.displayMetrics) }
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onNodeWithTag("quest-reward-success-modal").captureToImage().asAndroidBitmap()
        val directory =
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
                ?: context.getExternalFilesDir("quest-reward-layout")
        val output = File(directory, "quest-reward-$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
