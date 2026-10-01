package com.monsters.mobimon.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroundedReplyCodecTest {
    @Test fun parsesTypedVehicleReferences() {
        val reply = GroundedReplyCodec.parse(valid)
        assertEquals("capture", reply.vehicleRefs.single().evidenceId)
        assertEquals("VEHICLE", reply.status)
    }

    @Test fun rejectsDuplicateUnknownMissingAndWrongTypedFields() {
        listOf(
            valid.replace("\"version\":1", "\"version\":1,\"version\":1"),
            valid.replace("\"version\":1", "\"version\":1,\"extra\":false"),
            valid.replace("\"version\":1,", ""),
            valid.replace("\"version\":1", "\"version\":\"1\""),
            valid.replace("\"evidenceId\":\"capture\"", "\"evidenceId\":\"capture\",\"evidenceId\":\"other\""),
            valid + "{}",
        ).forEach { json -> assertThrows(ConversationException::class.java) { GroundedReplyCodec.parse(json) } }
    }

    private val valid = """{"version":1,"status":"VEHICLE","text":"{{vehicle:0}}","sourceIds":[],"vehicleRefs":[{"evidenceId":"capture","fieldId":"interpreted.batteryPercent"}]}"""
}
