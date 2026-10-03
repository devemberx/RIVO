package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.ConversationEvidenceSet
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.GroundedReply
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
import com.monsters.mobimon.di.ConversationToolsModule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseConversationPolicyTest {
    @Test fun releaseHasNoToolsButCannotBypassReferenceValidation() =
        runTest {
            val capture = VehicleChatCapture("initial", 10, VehicleObservationSource.VSS_ADAPTER, "s", emptyList())
            val tools = ConversationToolsModule.conversationTools(VehicleChatEvidenceSource { capture })
            assertTrue(tools.tools.isEmpty())
            val result =
                tools.groundedReplyPolicy!!.accept(
                    GroundedReply(
                        1,
                        "VEHICLE",
                        "배터리는 25% 남아 있어.",
                        emptyList(),
                        listOf(VehicleFactReference("old-turn", "battery")),
                    ),
                    ConversationEvidenceSet(capture),
                )
            assertTrue(result is ConversationResult.Failure)
        }

    @Test fun releasePreservesAiProseWithCurrentVehicleEvidence() =
        runTest {
            val id = VehicleChatFieldCatalog.BATTERY
            val capture =
                VehicleChatCapture(
                    "initial",
                    10,
                    VehicleObservationSource.VSS_ADAPTER,
                    "s",
                    listOf(
                        VehicleChatField(
                            VehicleChatFieldCatalog.find(id)!!,
                            VehicleObservation(
                                id,
                                VehicleValue.Number(25.0),
                                VehicleObservationSource.VSS_ADAPTER,
                                "s",
                                1,
                                0,
                                0,
                            ),
                            VehicleFieldValidity(SignalQuality.VALID, null, 10, "ON_CHANGE"),
                        ),
                    ),
                )
            val tools = ConversationToolsModule.conversationTools(VehicleChatEvidenceSource { capture })
            val text = "응, 확인해봤어! 배터리는 25% 남아 있어."
            val reply = GroundedReply(1, "VEHICLE", text, emptyList(), listOf(VehicleFactReference("initial", id)))
            assertTrue(tools.tools.isEmpty())
            assertEquals(
                ConversationResult.Success(text),
                tools.groundedReplyPolicy!!.accept(reply, ConversationEvidenceSet(capture)),
            )
        }
}
