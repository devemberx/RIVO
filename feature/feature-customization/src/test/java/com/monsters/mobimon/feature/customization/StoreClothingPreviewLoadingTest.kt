package com.monsters.mobimon.feature.customization

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.core.ui.preparePetPreviewArtwork
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w1280dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreClothingPreviewLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mobiClothingKeepsVisiblePreviewDuringColdLoad() = checkHandoff("mobi", "goggles")

    @Test fun lunaClothingKeepsVisiblePreviewDuringColdLoad() = checkHandoff("luna", "cap", "hat")

    private fun checkHandoff(
        friend: String,
        appearance: String,
        frameName: String = appearance,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val blocked = AtomicBoolean(false)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val delayedContext =
            object : ContextWrapper(context) {
                override fun getApplicationContext(): Context = this

                override fun getAssets(): AssetManager {
                    if (blocked.get() && Thread.currentThread() != Looper.getMainLooper().thread) {
                        started.countDown()
                        check(release.await(20, TimeUnit.SECONDS)) { "Decode was not released" }
                    }
                    return context.assets
                }
            }
        val friendId = "friend:$friend"
        val itemId = "accessory:${friend}_$appearance"
        val inventory = CosmeticInventory(setOf(friendId), mapOf(CosmeticSlot.FRIEND to friendId))
        var selected by mutableStateOf<String?>(null)
        var writes = 0
        compose.setContent {
            CompositionLocalProvider(LocalContext provides delayedContext) {
                MobiMonTheme {
                    CustomizationScreen(
                        inventory = inventory,
                        catalog =
                            listOf(
                                CosmeticItem(friendId, CosmeticSlot.FRIEND, 0),
                                CosmeticItem(itemId, CosmeticSlot.ACCESSORY, 300, friendId),
                            ),
                        selectedItemId = selected,
                        purchasing = false,
                        purchaseFailed = false,
                        onSelectItem = { selected = it },
                        onPurchaseItem = { _, _ -> writes++ },
                        onEquipItem = { writes++ },
                        onEquipFriend = { writes++ },
                        pointBalance = 300,
                        pointLoadFailed = false,
                    )
                }
            }
        }
        compose.onNodeWithTag("store-tab-ACCESSORY").performClick()
        awaitFrame(friend, "normal")
        try {
            blocked.set(true)
            compose.onNodeWithTag("store-item-$itemId").performClick()
            compose.waitUntil(5_000) { started.count == 0L }
            compose.onNodeWithTag("$friend-animation-frame-normal").assertExists()
            compose.onNodeWithTag("$friend-animation-loading-$frameName").assertDoesNotExist()
            assertEquals(itemId, selected)
            assertEquals(0, writes)

            // Supersede the pending request with the already visible appearance.
            compose.runOnIdle { selected = null }
            compose.onNodeWithTag("$friend-animation-frame-normal").assertExists()
            blocked.set(false)
            release.countDown()
            // Await the actual decode before checking that its cancelled request stayed invisible.
            runBlocking { preparePetPreviewArtwork(context, friendId, itemId) }
            compose.waitForIdle()
            compose.onNodeWithTag("$friend-animation-frame-normal").assertExists()
            compose.onNodeWithTag("$friend-animation-frame-$frameName").assertDoesNotExist()

            compose.onNodeWithTag("store-item-$itemId").performClick()
            awaitFrame(friend, frameName)
            compose.onNodeWithTag("$friend-animation-frame-normal").assertDoesNotExist()
            assertEquals(0, writes)
        } finally {
            blocked.set(false)
            release.countDown()
        }
    }

    private fun awaitFrame(
        friend: String,
        appearance: String,
    ) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("$friend-animation-frame-$appearance").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
