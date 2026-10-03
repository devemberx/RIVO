package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.ConversationEvidence
import com.monsters.mobimon.core.domain.ConversationEvidenceSet
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationToolCall
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.GroundedReply
import com.monsters.mobimon.core.domain.ManualRetriever
import com.monsters.mobimon.core.domain.ManualSearchResult
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
import com.monsters.mobimon.manual.ManualConversationTools
import com.monsters.mobimon.manual.ManualReplyPolicy
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmptyManualVehicleReplyTest {
    @Test fun emptyManualLookupPreservesVehicleEvidenceWithoutAuthorizingManualCitations() =
        runTest {
            val tools =
                ManualConversationTools.create(
                    object : ManualRetriever {
                        override suspend fun search(query: String) = ManualSearchResult.NoEvidence
                    },
                )
            val result = tools.tools.single().execute(ConversationToolCall("m1", "search_vehicle_manual", "현재 배터리"))
            assertTrue(
                "An empty document search must return data for the ongoing turn",
                result is ConversationToolResult.Found,
            )
            result as ConversationToolResult.Found
            val json = JSONObject(result.content)
            assertEquals("NO_EVIDENCE", json.getString("status"))
            assertEquals(0, json.getJSONArray("sources").length())
            assertTrue(result.evidence.isEmpty())

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
            val evidence = ConversationEvidenceSet(capture).also { it.add(result) }
            val policy = GroundedConversationReplyPolicy(VehicleChatEvidenceSource { capture }, ManualReplyPolicy)
            val ordinary = GroundedReply(1, "CONVERSATION", "권장 공기압은 99 psi야.", emptyList(), emptyList())
            assertTrue(
                "An empty manual lookup cannot be discarded by an ordinary-conversation envelope",
                policy.accept(ordinary, evidence) is ConversationResult.Failure,
            )
            evidence.add(ConversationToolResult.Found("unrelated tool output"))
            assertTrue(
                "Later tool results must retain the previous manual lookup attempt",
                policy.accept(ordinary, evidence) is ConversationResult.Failure,
            )
            val refs = listOf(VehicleFactReference("v1", id))
            val accepted = policy.accept(GroundedReply(1, "VEHICLE", "배터리는 12% 남아 있어.", emptyList(), refs), evidence)
            assertTrue(accepted is ConversationResult.Success)
            assertTrue((accepted as ConversationResult.Success).value.contains("12%"))
            assertTrue(
                policy.accept(
                    GroundedReply(1, "ANSWERED", "배터리는 12% 남아 있어. [ne1-0001]", listOf("ne1-0001"), refs),
                    evidence,
                ) is ConversationResult.Failure,
            )
            assertTrue(
                policy.accept(
                    GroundedReply(1, "NO_EVIDENCE", "설명서 근거가 없어.", emptyList(), emptyList()),
                    evidence,
                ) is ConversationResult.Failure,
            )
            evidence.add(
                ConversationToolResult.Found(
                    "manual excerpt",
                    listOf(ConversationEvidence("ne1-0001", "fixture page")),
                ),
            )
            assertTrue(policy.accept(ordinary, evidence) is ConversationResult.Failure)
            assertTrue(
                policy.accept(
                    GroundedReply(1, "ANSWERED", "배터리는 12% 남아 있어. 설명서 안내 [ne1-0001]", listOf("ne1-0001"), refs),
                    evidence,
                ) is ConversationResult.Success,
            )
            assertTrue(
                "Vehicle-only evidence still permits ordinary companion conversation",
                policy.accept(
                    GroundedReply(1, "CONVERSATION", "안녕", emptyList(), emptyList()),
                    ConversationEvidenceSet(capture),
                ) is ConversationResult.Success,
            )
        }
}
