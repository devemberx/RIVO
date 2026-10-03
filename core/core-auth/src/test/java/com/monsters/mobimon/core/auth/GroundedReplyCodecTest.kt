package com.monsters.mobimon.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroundedReplyCodecTest {
    @Test fun instructionalOutputExamplesConformToTheActualEnvelopeParser() {
        val examples =
            GroundedReplyCodec.instruction
                .lineSequence()
                .mapNotNull { line ->
                    val start = line.indexOf("{\"version\":")
                    line.substring(start.takeIf { it >= 0 } ?: return@mapNotNull null)
                }.map(GroundedReplyCodec::parse)
                .toList()
        assertEquals(listOf("CONVERSATION", "ANSWERED", "VEHICLE", "VEHICLE"), examples.map { it.status })
        assertEquals(listOf("ne1-0123", "ne1-0456"), examples[1].sourceIds)
        assertTrue(examples[1].text.contains("\n\n"))
        assertEquals(emptyList<Any>(), examples.first().vehicleRefs)
        assertEquals(
            listOf("demo-1", "demo-1"),
            examples[2].vehicleRefs.map { it.evidenceId },
        )
        assertEquals(
            listOf("interpreted.petCondition", "interpreted.batteryPercent"),
            examples[2].vehicleRefs.map { it.fieldId },
        )
        assertEquals(
            listOf("demo-2", "demo-2"),
            examples[3].vehicleRefs.map { it.evidenceId },
        )
        assertEquals(
            listOf("interpreted.petCondition", "Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.IsPressureLow"),
            examples[3].vehicleRefs.map { it.fieldId },
        )
    }

    @Test fun normalizesOmittedUnusedReferencesForEvidenceFreeStatuses() {
        for (status in listOf("CONVERSATION", "NEEDS_CLARIFICATION", "NO_EVIDENCE", "OUT_OF_SCOPE")) {
            for (json in listOf(
                textOnly(status),
                textOnly(status).replace(",\"sourceIds\":[]", ""),
                textOnly(status).replace("\"sourceIds\":[]", "\"vehicleRefs\":[]"),
            )) {
                val reply = GroundedReplyCodec.parse(json)
                assertEquals(1, reply.version)
                assertEquals(status, reply.status)
                assertEquals("응, 오늘 어떤 일이 있었어?", reply.text)
                assertEquals(emptyList<String>(), reply.sourceIds)
                assertEquals(emptyList<Any>(), reply.vehicleRefs)
            }
        }
    }

    @Test fun normalizesUnusedArraysOnManualAndVehicleAnswersWithoutInventingEvidence() {
        val manual = GroundedReplyCodec.parse(manualOnly)
        assertEquals("ANSWERED", manual.status)
        assertEquals(listOf("ne1-0001"), manual.sourceIds)
        assertEquals(emptyList<Any>(), manual.vehicleRefs)

        val vehicle = GroundedReplyCodec.parse(valid.replace("\"sourceIds\":[],", ""))
        assertEquals("capture", vehicle.vehicleRefs.single().evidenceId)
        assertEquals(emptyList<String>(), vehicle.sourceIds)

        // Missing used evidence remains empty, so the owning acceptance policy must reject it.
        val missing = GroundedReplyCodec.parse(textOnly("VEHICLE").replace(",\"sourceIds\":[]", ""))
        assertEquals(emptyList<Any>(), missing.vehicleRefs)
    }

    @Test fun rejectsIncompleteMalformedAndWrongTypedEnvelopes() {
        listOf(
            textOnly("UNKNOWN"),
            textOnly("CONVERSATION").replace("\"version\":1,", ""),
            textOnly("CONVERSATION").replace("\"text\":\"응, 오늘 어떤 일이 있었어?\",", ""),
            textOnly("CONVERSATION").replace("\"version\":1", "\"version\":2"),
            textOnly("CONVERSATION").dropLast(1) + ",\"vehicleRefs\":null}",
            textOnly("CONVERSATION").dropLast(1) + ",\"vehicleRefs\":{}}",
            textOnly("CONVERSATION").dropLast(1) + ",\"extra\":false}",
        ).forEach { json -> assertThrows(ConversationException::class.java) { GroundedReplyCodec.parse(json) } }
    }

    @Test fun parsesTypedVehicleReferences() {
        val reply = GroundedReplyCodec.parse(valid)
        assertEquals("capture", reply.vehicleRefs.single().evidenceId)
        assertEquals("VEHICLE", reply.status)
        assertEquals("배터리는 25% 남아 있어.", reply.text)
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

    private val valid =
        """
        {"version":1,"status":"VEHICLE","text":"배터리는 25% 남아 있어.","sourceIds":[],
        "vehicleRefs":[{"evidenceId":"capture","fieldId":"interpreted.batteryPercent"}]}
        """.trimIndent()

    private fun textOnly(status: String) =
        """{"version":1,"status":"$status","text":"응, 오늘 어떤 일이 있었어?","sourceIds":[]}"""

    private val manualOnly =
        """{"version":1,"status":"ANSWERED","text":"와이퍼 교체 안내 [ne1-0001]","sourceIds":["ne1-0001"]}"""
}
