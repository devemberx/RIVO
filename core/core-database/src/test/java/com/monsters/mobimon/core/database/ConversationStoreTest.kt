package com.monsters.mobimon.core.database

import com.monsters.mobimon.core.domain.ConversationKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationStoreTest {
    @get:Rule val files = TemporaryFolder()
    private val mobi = ConversationKey("profile", "friend:mobi")
    private val luna = ConversationKey("profile", "friend:luna")

    @Test fun reopeningRestoresOnlyTheCurrentCompanionAndResetRejectsLateSaves() =
        runTest {
            val store = AtomicConversationStore(files.root)
            val first = store.load(mobi, 1)
            val saved = requireNotNull(store.append(mobi, first, "hello", "hi"))
            assertEquals(listOf("hello", "hi"), AtomicConversationStore(files.root).load(mobi, 1).turns.map { it.text })
            assertTrue(store.load(luna, 1).turns.isEmpty())
            val reset = store.reset(mobi, 1)
            assertNotEquals(saved.id, reset.id)
            assertNull(store.append(mobi, saved, "late", "reply"))
            assertTrue(AtomicConversationStore(files.root).load(mobi, 1).turns.isEmpty())
            assertEquals(2, files.root.listFiles()!!.size)
        }

    @Test fun accountAndProfileChangesNeverLoadAnotherOwnersDialogue() =
        runTest {
            val store = AtomicConversationStore(files.root)
            val first = store.load(mobi, 1)
            store.append(mobi, first, "private", "reply")
            assertTrue(store.load(mobi.copy(profileId = "other"), 1).turns.isEmpty())
            assertTrue(store.load(mobi, 2).turns.isEmpty())
            assertNull(store.append(mobi, first, "late", "reply"))
            assertTrue(store.load(mobi, 1).turns.isEmpty())
        }

    @Test fun corruptStorageFailsWithoutErasingCommittedBytes() =
        runTest {
            val store = AtomicConversationStore(files.root)
            store.load(mobi, 1)
            val file = files.root.listFiles()!!.single()
            file.writeText("corrupt")
            try {
                store.load(mobi, 1)
                fail("Expected read failure")
            } catch (_: java.io.IOException) {
            }
            assertEquals("corrupt", file.readText())
            assertTrue(store.reset(mobi, 1).turns.isEmpty())
        }
}
