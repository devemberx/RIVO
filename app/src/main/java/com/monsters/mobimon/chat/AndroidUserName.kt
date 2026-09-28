package com.monsters.mobimon.chat

import android.annotation.SuppressLint
import android.content.Context
import android.os.UserManager

/** Reads this application's Android user, never a display name from the GitHub account. */
class AndroidUserName(
    private val context: Context,
) {
    @SuppressLint("MissingPermission")
    fun read(): String? =
        try {
            context
                .getSystemService(UserManager::class.java)
                ?.userName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
}
