package com.monsters.mobimon.chat

import android.content.pm.PackageManager
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidUserNameDeviceTest {
    @Test fun currentUserNameCanBeReadWithQueryUsersAndMissingPermissionIsOptional() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val reader = AndroidUserName(context)
        if (context.checkSelfPermission("android.permission.QUERY_USERS") != PackageManager.PERMISSION_GRANTED) {
            assertNull(reader.read())
        }
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.QUERY_USERS")
        try {
            val expected =
                context
                    .getSystemService(UserManager::class.java)
                    .userName
                    .trim()
                    .takeIf { it.isNotEmpty() }
            assertEquals(expected, reader.read())
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
