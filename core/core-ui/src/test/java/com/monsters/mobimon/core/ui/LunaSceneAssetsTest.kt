package com.monsters.mobimon.core.ui

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LunaSceneAssetsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun sharedImagesPreserveEveryOriginalTransitionFrameInOrder() {
        // SHA-256 of the ordered 24 PNG digests, captured before removing duplicate files.
        val originalSequences =
            mapOf(
                "normal/appear" to "5eaafccdfea3675d0c810dfade6490a6c81322d9270fe3b7e1c802cd320ab53d",
                "normal/disappear" to "eb0040e556e16b4230cf6035513c7475b579dce7222d54989533fb5faac7f71a",
                "hat/appear" to "217aeaaa090b17f46f3ec0cad7c77cc1e276ac6c3d64cdad0d4cc570c56a483a",
                "hat/disappear" to "0abfc8fd9673e988b65705e10cfc0c8d65f39dd02cd55f63c308a9e41014c19d",
                "sunglasses/appear" to "c3f1d8e1a1434cda85677a948102acc2c6049276cd2d3f6ea6df4139cf138f75",
                "sunglasses/disappear" to "05f4d219d16b49d0087ab3c4777c89133334c7efb99a284d5dbefd9f3154e7c0",
            )
        for ((clip, expected) in originalSequences) {
            val (appearance, action) = clip.split('/')
            val digest = MessageDigest.getInstance("SHA-256")
            for (frame in 1..24) {
                val path =
                    String.format(Locale.US, "characters/luna/$clip/luna_${action}_${appearance}_%02d.png", frame)
                val bytes = context.assets.open(LunaScene.sourcePath(path)).use { it.readBytes() }
                digest.update(MessageDigest.getInstance("SHA-256").digest(bytes))
            }
            assertEquals(clip, expected, digest.digest().joinToString("") { "%02x".format(it) })
        }
    }

    @Test
    fun repeatedHoldsKeepTheirSlotsAndReuseDecodedBitmaps() {
        for (appearance in LunaAppearance.entries) {
            val frames = requireNotNull(LunaDisappearTimeline.load(context, appearance))
            assertEquals(24, frames.size)
            for ((first, repeat) in listOf(0 to 1, 7 to 8, 17 to 18, 22 to 23)) {
                assertSame(frames[first], frames[repeat])
            }
        }
    }
}
