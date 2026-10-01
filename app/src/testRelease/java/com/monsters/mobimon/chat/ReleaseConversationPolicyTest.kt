package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.ConversationEvidenceSet
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.GroundedReply
import com.monsters.mobimon.core.domain.VehicleChatCapture
import com.monsters.mobimon.core.domain.VehicleChatEvidenceSource
import com.monsters.mobimon.core.domain.VehicleFactReference
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.di.ConversationToolsModule
import kotlinx.coroutines.test.runTest
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
                        "{{vehicle:0}}",
                        emptyList(),
                        listOf(VehicleFactReference("old-turn", "battery")),
                    ),
                    ConversationEvidenceSet(capture),
                )
            assertTrue(result is ConversationResult.Failure)
        }
}
