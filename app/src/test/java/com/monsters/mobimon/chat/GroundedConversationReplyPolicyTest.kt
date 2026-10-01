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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundedConversationReplyPolicyTest {
    @Test fun rendersSimulationAndAllowsEqualValueNewReceipt() =
        runTest {
            val initial = capture()
            val latest = capture(revision = 2)
            val result = policy(latest).accept(reply(), ConversationEvidenceSet(initial))
            assertEquals("시뮬레이션 · 배터리 잔량: 25%", (result as ConversationResult.Success).value)
        }

    @Test fun freshEqualReceiptCanRevalidateBeyondTheInitialDeadline() =
        runTest {
            val result =
                policy(capture(revision = 2, now = 1500, deadline = 2000)).accept(
                    reply(),
                    ConversationEvidenceSet(capture(deadline = 1000)),
                )
            assertTrue(result is ConversationResult.Success)
        }

    @Test fun rejectsChangedValueExpiredEvidenceAndChangedSession() =
        runTest {
            listOf(capture(26.0), capture(valid = false), capture(session = "other")).forEach { latest ->
                assertTrue(
                    policy(latest).accept(reply(), ConversationEvidenceSet(capture())) is ConversationResult.Failure,
                )
            }
            assertTrue(
                policy(
                    capture(now = 1001, valid = false),
                ).accept(reply(), ConversationEvidenceSet(capture(deadline = 1000))) is ConversationResult.Failure,
            )
        }

    @Test fun rejectsMissingExtraCrossTurnAndWrongKindReferences() =
        runTest {
            val evidence = ConversationEvidenceSet(capture())
            listOf(
                reply(text = "숫자를 직접 주장"),
                reply(text = "{{vehicle:1}}"),
                reply(id = "another-turn"),
                reply(id = "ne1-0001"),
                reply(status = "CONVERSATION"),
                reply(text = "{{vehicle:0}} {{vehicle:0}}"),
            ).forEach { assertTrue(policy(capture()).accept(it, evidence) is ConversationResult.Failure) }
        }

    @Test fun rendersUnavailableWithoutInventingAValue() =
        runTest {
            val result = policy(capture(valid = false)).accept(reply(), ConversationEvidenceSet(capture(valid = false)))
            val text = (result as ConversationResult.Success).value
            assertTrue(text.contains("확인할 수 없음"))
            assertTrue(!text.contains("25%"))
        }

    @Test fun advancingClockKeepsExplicitCaptureTimeAndDoesNotPretendItIsObservationTime() =
        runTest {
            val id = VehicleChatFieldCatalog.TIME
            val before = capture(fieldId = id, value = VehicleValue.Text("2026-10-02T10:00:00+09:00"), deadline = 1000)
            val after = capture(fieldId = id, value = VehicleValue.Text("2026-10-02T10:00:01+09:00"), now = 900)
            val result = policy(after).accept(reply(fieldId = id), ConversationEvidenceSet(before))
            assertTrue((result as ConversationResult.Success).value.contains("10:00:00"))
        }

    private fun policy(latest: VehicleChatCapture) =
        GroundedConversationReplyPolicy(VehicleChatEvidenceSource { latest })

    private fun reply(
        text: String = "{{vehicle:0}}",
        id: String = "capture",
        fieldId: String = VehicleChatFieldCatalog.BATTERY,
        status: String = "VEHICLE",
    ) = GroundedReply(1, status, text, emptyList(), listOf(VehicleFactReference(id, fieldId)))

    private fun capture(
        number: Double = 25.0,
        valid: Boolean = true,
        session: String = "session",
        revision: Long = 1,
        fieldId: String = VehicleChatFieldCatalog.BATTERY,
        value: VehicleValue = VehicleValue.Number(number),
        now: Long = 100,
        deadline: Long? = null,
    ): VehicleChatCapture {
        val observation =
            VehicleObservation(fieldId, value, VehicleObservationSource.DEBUG_OVERRIDE, session, revision, 10, 10)
        return VehicleChatCapture(
            "capture",
            now,
            observation.sourceKind,
            session,
            listOf(
                VehicleChatField(
                    VehicleChatFieldCatalog.find(fieldId)!!,
                    observation,
                    VehicleFieldValidity(
                        if (valid) SignalQuality.VALID else SignalQuality.UNAVAILABLE,
                        if (valid) null else "DISCONNECTED",
                        90,
                        "ON_CHANGE",
                    ),
                    validUntilElapsedMillis = deadline,
                ),
            ),
        )
    }
}
