package com.monsters.mobimon.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monsters.mobimon.core.domain.ConversationEvidence
import com.monsters.mobimon.core.domain.ConversationEvidenceSet
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.GroundedReply
import com.monsters.mobimon.core.domain.ManualReplyRejection
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.VehicleChatCapture
import com.monsters.mobimon.core.domain.VehicleChatEvidenceSource
import com.monsters.mobimon.core.domain.VehicleChatField
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleFactReference
import com.monsters.mobimon.core.domain.VehicleFieldValidity
import com.monsters.mobimon.core.domain.VehicleObservation
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.core.domain.VehicleValue
import com.monsters.mobimon.manual.ManualReplyPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises Android's regex engine without launching an activity or accessing account/storage data. */
@RunWith(AndroidJUnit4::class)
class GroundedConversationReplyPolicyDeviceTest {
    @Test fun aiProseIsPreservedForBothSourcesOnAndroid() =
        runBlocking {
            val references = listOf(VehicleFactReference("capture", VehicleChatFieldCatalog.BATTERY))
            for (source in listOf(VehicleObservationSource.DEBUG_OVERRIDE, VehicleObservationSource.VSS_ADAPTER)) {
                val text = "응, 확인해봤어! 배터리는 25% 남아 있어."
                assertEquals(
                    ConversationResult.Success(text),
                    accept(GroundedReply(1, "VEHICLE", text, emptyList(), references), source),
                )
            }
        }

    @Test fun invalidReferencesAreRejectedWithoutReplacingProseOnAndroid() =
        runBlocking {
            val text = "배터리는 25% 남아 있어!"
            for (refs in listOf(
                emptyList(),
                listOf(VehicleFactReference("another-turn", VehicleChatFieldCatalog.BATTERY)),
            )) {
                assertTrue(accept(GroundedReply(1, "VEHICLE", text, emptyList(), refs)) is ConversationResult.Failure)
            }
        }

    @Test fun manualOnlyAnswersStillRequireThisTurnSourcesOnAndroid() =
        runBlocking {
            val capture =
                VehicleChatCapture("capture", 100, VehicleObservationSource.DEBUG_OVERRIDE, "session", emptyList())
            val policy = GroundedConversationReplyPolicy(VehicleChatEvidenceSource { capture }, ManualReplyPolicy)
            val evidence = ConversationEvidenceSet(capture)
            evidence.add(
                ConversationToolResult.Found(
                    "fixture excerpt",
                    listOf(ConversationEvidence("ne1-0001", "fixture page")),
                ),
            )

            fun reply(
                ids: List<String> = listOf("ne1-0001"),
                text: String = "와이퍼 교체 안내 [ne1-0001]",
            ) = GroundedReply(1, "ANSWERED", text, ids, emptyList())

            val accepted = policy.accept(reply(), evidence) as ConversationResult.Success
            assertTrue(accepted.value.contains("와이퍼 교체 안내 [1]") && accepted.value.contains("[1] fixture page"))
            assertTrue(policy.accept(reply(emptyList()), evidence) is ConversationResult.Failure)
            assertTrue(policy.accept(reply(listOf("ne1-9999")), evidence) is ConversationResult.Failure)
            assertTrue(policy.accept(reply(), ConversationEvidenceSet(capture)) is ConversationResult.Failure)
            assertTrue(policy.accept(reply(text = "{{vehicle:0}} [ne1-0001]"), evidence) is ConversationResult.Failure)
            val displayCitation = reply(text = "와이퍼 교체 안내 [1]")
            assertTrue(policy.accept(displayCitation, evidence) is ConversationResult.Failure)
            assertEquals(ManualReplyRejection.NUMERIC_CITATIONS, policy.rejectionReason(displayCitation, evidence))
            assertEquals(
                ManualReplyRejection.UNKNOWN_SOURCE,
                policy.rejectionReason(reply(listOf("ne1-9999")), evidence),
            )
        }

    @Test fun ordinaryConversationPassesReferenceValidationOnAndroid() =
        runBlocking {
            val reply = GroundedReply(1, "CONVERSATION", "응, 오늘 어떤 일이 있었어?", emptyList(), emptyList())
            assertEquals(ConversationResult.Success(reply.text), accept(reply))
            assertTrue(
                accept(GroundedReply(1, "CONVERSATION", "{{vehicle:0}}", emptyList(), emptyList())) is
                    ConversationResult.Failure,
            )
            assertTrue(
                accept(GroundedReply(1, "CONVERSATION", "안내 [ne1-0001]", emptyList(), emptyList())) is
                    ConversationResult.Failure,
            )
        }

    @Test fun obsoleteTokensAreRejectedOnAndroid() =
        runBlocking {
            val references = listOf(VehicleFactReference("capture", VehicleChatFieldCatalog.BATTERY))
            for (text in listOf("{{vehicle:0}}", "{{vehicle:0}} {{vehicle:0}}", "{{vehicle:1}}", "{{vehicle:0}")) {
                assertTrue(
                    accept(GroundedReply(1, "VEHICLE", text, emptyList(), references)) is ConversationResult.Failure,
                )
            }
        }

    private suspend fun accept(
        reply: GroundedReply,
        source: VehicleObservationSource = VehicleObservationSource.DEBUG_OVERRIDE,
    ): ConversationResult<String> {
        val observation =
            VehicleObservation(
                VehicleChatFieldCatalog.BATTERY,
                VehicleValue.Number(25.0),
                source,
                "session",
                1,
                10,
                10,
            )
        val capture =
            VehicleChatCapture(
                "capture",
                100,
                observation.sourceKind,
                "session",
                listOf(
                    VehicleChatField(
                        VehicleChatFieldCatalog.find(observation.fieldId)!!,
                        observation,
                        VehicleFieldValidity(SignalQuality.VALID, null, 90, "ON_CHANGE"),
                    ),
                ),
            )
        return GroundedConversationReplyPolicy(VehicleChatEvidenceSource { capture })
            .accept(reply, ConversationEvidenceSet(capture))
    }
}
