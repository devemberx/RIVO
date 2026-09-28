package com.monsters.mobimon.core.database

import android.util.AtomicFile
import com.monsters.mobimon.core.domain.ConversationKey
import com.monsters.mobimon.core.domain.ConversationStore
import com.monsters.mobimon.core.domain.ConversationTurn
import com.monsters.mobimon.core.domain.StoredConversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** One singleton per app process, rooted in that Android user's noBackupFilesDir. No thread archive. */
class AtomicConversationStore(
    private val directory: File,
) : ConversationStore {
    private val mutex = Mutex()

    override suspend fun load(
        key: ConversationKey,
        accountId: Long,
    ): StoredConversation =
        locked {
            val file = file(key)
            val current = read(file)
            if (current?.accountId == accountId) current else fresh(file, accountId)
        }

    override suspend fun reset(
        key: ConversationKey,
        accountId: Long,
    ): StoredConversation =
        locked {
            fresh(file(key), accountId)
        }

    override suspend fun append(
        key: ConversationKey,
        expected: StoredConversation,
        user: String,
        reply: String,
    ): StoredConversation? =
        locked {
            val file = file(key)
            val current = read(file)
            if (current == null ||
                current.id != expected.id ||
                current.accountId != expected.accountId ||
                current.revision != expected.revision
            ) {
                return@locked null
            }
            val next =
                StoredConversation(
                    current.id,
                    current.accountId,
                    current.revision + 1,
                    current.turns + ConversationTurn(user, true) + ConversationTurn(reply, false),
                )
            write(file, next)
            next
        }

    private suspend fun <T> locked(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                block()
            }
        }

    private fun file(key: ConversationKey): AtomicFile {
        require(key.profileId.isNotBlank() && key.friendId in setOf("friend:mobi", "friend:luna"))
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Conversation storage unavailable")
        val identity = JSONArray().put(key.profileId).put(key.friendId).toString()
        val name =
            MessageDigest
                .getInstance(
                    "SHA-256",
                ).digest(identity.toByteArray())
                .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, "$name.json"))
    }

    private suspend fun fresh(
        file: AtomicFile,
        account: Long,
    ): StoredConversation {
        val next = StoredConversation(UUID.randomUUID().toString(), account, 0, emptyList())
        write(file, next)
        return next
    }

    private fun read(file: AtomicFile): StoredConversation? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        try {
            val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
            require(json.getInt("format") == 1)
            val turns = json.getJSONArray("turns")
            require(turns.length() % 2 == 0)
            val messages =
                (0 until turns.length()).map {
                    val turn = turns.getJSONObject(it)
                    val user = turn.getBoolean("user")
                    require(user == (it % 2 == 0))
                    ConversationTurn(turn.getString("text"), user)
                }
            return StoredConversation(json.getString("id"), json.getLong("account"), json.getLong("revision"), messages)
        } catch (_: Exception) {
            throw IOException("Conversation storage unreadable")
        }
    }

    private suspend fun write(
        file: AtomicFile,
        conversation: StoredConversation,
    ) {
        val turns = JSONArray()
        conversation.turns.forEach { turns.put(JSONObject().put("text", it.text).put("user", it.fromUser)) }
        val bytes =
            JSONObject()
                .put("format", 1)
                .put("id", conversation.id)
                .put("account", conversation.accountId)
                .put("revision", conversation.revision)
                .put("turns", turns)
                .toString()
                .toByteArray()
        currentCoroutineContext().ensureActive()
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw IOException("Conversation save failed")
        }
    }
}
