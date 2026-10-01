package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.ConversationEvidence
import com.monsters.mobimon.core.domain.ConversationEvidenceSet
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationToolResult
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
import com.monsters.mobimon.manual.ManualReplyPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MixedGroundedReplyTest {
    @Test fun manualAndVehicleReferencesCannotSubstituteForOneAnother() =
        runTest {
            val id = VehicleChatFieldCatalog.BATTERY
            val observation =
                VehicleObservation(id, VehicleValue.Number(12.0), VehicleObservationSource.DEBUG_OVERRIDE, "s", 1, 0, 0)
            val capture =
                VehicleChatCapture(
                    "v1",
                    10,
                    observation.sourceKind,
                    "s",
                    listOf(
                        VehicleChatField(
                            VehicleChatFieldCatalog.find(id)!!,
                            observation,
                            VehicleFieldValidity(SignalQuality.VALID, null, 10, "ON_CHANGE"),
                        ),
                    ),
                )
            val evidence = ConversationEvidenceSet(capture)
            evidence.add(
                ConversationToolResult.Found(
                    "fixture excerpt",
                    listOf(ConversationEvidence("ne1-0001", "fixture page")),
                ),
            )
            val policy = GroundedConversationReplyPolicy(VehicleChatEvidenceSource { capture }, ManualReplyPolicy)

            fun reply(sources: List<String>) =
                GroundedReply(
                    1,
                    "ANSWERED",
                    "{{vehicle:0}}. 설명서 안내 [ne1-0001]",
                    sources,
                    listOf(VehicleFactReference("v1", id)),
                )
            val result = policy.accept(reply(listOf("ne1-0001")), evidence)
            val text = (result as ConversationResult.Success).value
            assertTrue(text.contains("12%") && text.contains("[1] fixture page"))
            assertTrue(policy.accept(reply(listOf("v1")), evidence) is ConversationResult.Failure)
        }
}
