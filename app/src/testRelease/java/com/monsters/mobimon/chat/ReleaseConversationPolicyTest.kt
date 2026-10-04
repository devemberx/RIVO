package com.monsters.mobimon.chat

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.monsters.mobimon.core.domain.ConversationEvidenceSet
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationToolCall
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.ConversationToolUsage
import com.monsters.mobimon.core.domain.ConversationTurn
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReleaseConversationPolicyTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val retriever get() = ConversationToolsModule.manualRetriever(context)

    @Test fun releaseSearchesBundledManualAndRequiresCurrentCitations() =
        runTest {
            val capture = capture()
            val tools = ConversationToolsModule.conversationTools(retriever, VehicleChatEvidenceSource { capture })
            val manual = tools.tools.single { it.definition.name == "search_vehicle_manual" }
            val result = manual.execute(ConversationToolCall("manual", manual.definition.name, "와이퍼 블레이드 교체 방법"))
            assertTrue(result is ConversationToolResult.Found)
            result as ConversationToolResult.Found
            assertTrue(result.evidence.isNotEmpty())
            assertTrue(result.manualLookupAttempted)
            val sourceId = result.evidence.first().id
            val reply = GroundedReply(1, "ANSWERED", "와이퍼 교체 안내 [$sourceId]", listOf(sourceId), emptyList())
            val evidence = ConversationEvidenceSet(capture).also { it.add(result) }
            val accepted = tools.groundedReplyPolicy!!.accept(reply, evidence)
            assertTrue(accepted is ConversationResult.Success)
            assertTrue((accepted as ConversationResult.Success).value.contains("출처 : 2027 한국형 아이오닉 5"))
            assertTrue(
                tools.groundedReplyPolicy!!.accept(
                    reply,
                    ConversationEvidenceSet(capture),
                ) is ConversationResult.Failure,
            )
            assertTrue(
                tools.groundedReplyPolicy!!.accept(
                    GroundedReply(1, "ANSWERED", reply.text, listOf("ne1-9999"), emptyList()),
                    evidence,
                ) is ConversationResult.Failure,
            )
        }

    @Test fun releaseQueriesDetailedTireEvidenceAndRejectsChangedValues() =
        runTest {
            val id = "interpreted.tirePressureStatus"
            var latest = capture(id, VehicleValue.Text("LOW"))
            val tools = ConversationToolsModule.conversationTools(retriever, VehicleChatEvidenceSource { latest })
            val vehicle = tools.tools.single { it.definition.name == "get_vehicle_context" }
            val result = vehicle.execute(ConversationToolCall("tires", vehicle.definition.name, "tires"))
            assertTrue(result is ConversationToolResult.Found)
            val captured = (result as ConversationToolResult.Found).vehicleCapture!!
            assertEquals(VehicleObservationSource.VSS_ADAPTER, captured.sourceKind)
            assertEquals(VehicleValue.Text("LOW"), captured.field(id)!!.value)
            val evidence = ConversationEvidenceSet(capture(evidenceId = "basic")).also { it.add(result) }
            val reply =
                GroundedReply(
                    1,
                    "VEHICLE",
                    "타이어 저압 상태가 확인됐어.",
                    emptyList(),
                    listOf(VehicleFactReference(captured.evidenceId, id)),
                )
            assertEquals(ConversationResult.Success(reply.text), tools.groundedReplyPolicy!!.accept(reply, evidence))
            latest = capture(id, VehicleValue.Text("NORMAL"))
            assertEquals(
                ConversationResult.Failure(ConversationProblem.NO_EVIDENCE),
                tools.groundedReplyPolicy!!.accept(reply, evidence),
            )
        }

    @Test fun releaseRoutesStateRequestsAndRetainsBothToolsForMixedQuestions() {
        val tools = ConversationToolsModule.conversationTools(retriever, VehicleChatEvidenceSource { capture() })

        fun names(question: String) =
            tools
                .forTurn(listOf(ConversationTurn(question, true)))
                .tools
                .map {
                    it.definition.name
                }.toSet()
        assertEquals(setOf("get_vehicle_context"), names("현재 타이어 상태 확인해줘"))
        assertEquals(setOf("search_vehicle_manual", "get_vehicle_context"), names("와이퍼 교체 방법 알려줘"))
        assertEquals(setOf("search_vehicle_manual", "get_vehicle_context"), names("현재 배터리 잔량과 설명서의 충전 방법 알려줘"))
    }

    @Test fun releaseCannotBypassReferenceValidationAndDoesNotLogDiagnostics() =
        runTest {
            ShadowLog.clear()
            val tools = ConversationToolsModule.conversationTools(retriever, VehicleChatEvidenceSource { capture() })
            tools.onUsage(ConversationToolUsage(1, 1, 10, 10, 10, 10))
            val result =
                tools.groundedReplyPolicy!!.accept(
                    GroundedReply(
                        1,
                        "VEHICLE",
                        "배터리는 25% 남아 있어.",
                        emptyList(),
                        listOf(VehicleFactReference("old-turn", "battery")),
                    ),
                    ConversationEvidenceSet(capture()),
                )
            assertTrue(result is ConversationResult.Failure)
            assertTrue(ShadowLog.getLogsForTag("MobiMonManual").isEmpty())
            assertTrue(ShadowLog.getLogsForTag("MobiMonCopilot").isEmpty())
        }

    @Test fun releasePreservesAiProseWithCurrentVehicleEvidence() =
        runTest {
            val id = VehicleChatFieldCatalog.BATTERY
            val capture = capture(id, VehicleValue.Number(25.0))
            val tools = ConversationToolsModule.conversationTools(retriever, VehicleChatEvidenceSource { capture })
            val text = "응, 확인해봤어! 배터리는 25% 남아 있어."
            val reply =
                GroundedReply(1, "VEHICLE", text, emptyList(), listOf(VehicleFactReference(capture.evidenceId, id)))
            assertEquals(
                ConversationResult.Success(text),
                tools.groundedReplyPolicy!!.accept(reply, ConversationEvidenceSet(capture)),
            )
        }

    @Test fun releaseExcludesDiagnosticComponentsAndEvaluationAssets() {
        for (name in listOf(
            "manuals/evaluation.json",
            "chat/vehicle-evaluation.json",
            "manuals/ioniq5_2027_ko/coverage.json",
        )) {
            assertThrows(IOException::class.java) { context.assets.open(name).close() }
        }
        assertFalse(
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES).activities.any {
                it.name.endsWith("CopilotToolProbeActivity") || it.name.endsWith("ConversationPreviewActivity")
            },
        )
    }

    private fun capture(
        id: String? = null,
        value: VehicleValue? = null,
        evidenceId: String = "initial",
    ): VehicleChatCapture =
        VehicleChatCapture(
            evidenceId,
            10,
            VehicleObservationSource.VSS_ADAPTER,
            "s",
            if (id == null) {
                emptyList()
            } else {
                listOf(
                    VehicleChatField(
                        VehicleChatFieldCatalog.find(id)!!,
                        VehicleObservation(id, value, VehicleObservationSource.VSS_ADAPTER, "s", 1, 0, 0),
                        VehicleFieldValidity(SignalQuality.VALID, null, 10, "ON_CHANGE"),
                    ),
                )
            },
        )
}
