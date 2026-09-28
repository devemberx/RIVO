package com.monsters.mobimon.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsters.mobimon.core.domain.ConversationKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class ConversationStoreDeviceTest {
    @Test fun atomicCurrentThreadReopensAndResetRejectsItsOldLease() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = Files.createTempDirectory(context.cacheDir.toPath(), "chat-test-").toFile()
            try {
                val key = ConversationKey("isolated-test", "friend:mobi")
                val store = AtomicConversationStore(directory)
                val saved = requireNotNull(store.append(key, store.load(key, 1), "hello", "reply"))
                val reopened = AtomicConversationStore(directory)
                assertEquals(listOf("hello", "reply"), reopened.load(key, 1).turns.map { it.text })
                reopened.reset(key, 1)
                assertNull(reopened.append(key, saved, "late", "reply"))
                assertTrue(reopened.load(key, 1).turns.isEmpty())
                assertEquals(1, directory.listFiles()!!.size)
            } finally {
                directory.deleteRecursively()
            }
        }
}
