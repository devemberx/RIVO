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
    @Test fun rejectionMetadataSeparatesMarkersFromMissingEvidence() =
        runTest {
            val reasons = mutableListOf<GroundedReplyRejection>()
            val checked =
                GroundedConversationReplyPolicy(VehicleChatEvidenceSource { capture() }, onRejected = reasons::add)
            assertTrue(
                checked.accept(
                    GroundedReply(1, "CONVERSATION", "{{vehicle:0}}", emptyList(), emptyList()),
                    ConversationEvidenceSet(capture()),
                ) is ConversationResult.Failure,
            )
            assertTrue(
                checked.accept(
                    reply(id = "another-turn"),
                    ConversationEvidenceSet(capture()),
                ) is ConversationResult.Failure,
            )
            assertEquals(
                listOf(GroundedReplyRejection.VEHICLE_MARKERS, GroundedReplyRejection.MISSING_VEHICLE_EVIDENCE),
                reasons,
            )
            assertTrue(checked.accept(reply(), ConversationEvidenceSet(capture())) is ConversationResult.Success)
            assertEquals(2, reasons.size)
        }

    @Test fun emptyReferencesAllowOrdinaryChatButCannotHideEvidenceMarkers() =
        runTest {
            val evidence = ConversationEvidenceSet(capture())
            val ordinary = GroundedReply(1, "CONVERSATION", "응, 오늘 어떤 일이 있었어?", emptyList(), emptyList())
            assertEquals(ConversationResult.Success(ordinary.text), policy(capture()).accept(ordinary, evidence))
            for (text in listOf("{{vehicle:0}}", "설명서 안내 [ne1-0001]")) {
                val unreferenced = GroundedReply(1, "CONVERSATION", text, emptyList(), emptyList())
                assertTrue(
                    policy(capture()).accept(unreferenced, evidence) is ConversationResult.Failure,
                )
            }
        }

    @Test fun preservesAiProseAndAllowsEqualValueNewReceipt() =
        runTest {
            val initial = capture()
            val latest = capture(revision = 2)
            val result = policy(latest).accept(reply(), ConversationEvidenceSet(initial))
            assertEquals(reply().text, (result as ConversationResult.Success).value)
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

    @Test fun obsoletePlaceholdersAreRejectedInsteadOfReplacingTheReply() =
        runTest {
            for (text in listOf("{{vehicle:0}}", "{{vehicle:1}}", "{{vehicle:0}", "{{vehicle:0}} {{vehicle:0}}")) {
                assertTrue(
                    policy(
                        capture(),
                    ).accept(reply(text = text), ConversationEvidenceSet(capture())) is ConversationResult.Failure,
                )
            }
        }

    @Test fun aiOwnsWordingForBothSourcesAndMultipleReferences() =
        runTest {
            for (source in listOf(VehicleObservationSource.DEBUG_OVERRIDE, VehicleObservationSource.VSS_ADAPTER)) {
                val battery = capture(source = source).fields.single()
                val condition =
                    capture(
                        source = source,
                        fieldId = VehicleChatFieldCatalog.CONDITION,
                        value = VehicleValue.Text("CHECKED"),
                    ).fields.single()
                val initial = VehicleChatCapture("capture", 100, source, "session", listOf(battery, condition))
                val text = "응, 확인해봤어! 배터리는 25%이고, 확인한 항목에는 경고가 없어."
                val candidate =
                    reply(
                        text = text,
                        refs =
                            listOf(
                                VehicleFactReference("capture", battery.spec.id),
                                VehicleFactReference("capture", condition.spec.id),
                            ),
                    )
                assertEquals(
                    ConversationResult.Success(text),
                    policy(initial).accept(candidate, ConversationEvidenceSet(initial)),
                )
            }
        }

    @Test fun referenceValidationDoesNotClaimToValidateProseSemantics() =
        runTest {
            val candidate = reply(text = "배터리는 99%야.")
            assertEquals(
                ConversationResult.Success(candidate.text),
                policy(capture()).accept(candidate, ConversationEvidenceSet(capture())),
            )
        }

    @Test fun freeProseStillRequiresValidReferencesAndUnchangedEvidence() =
        runTest {
            val evidence = ConversationEvidenceSet(capture())
            val malformed = reply(text = "배터리는 99%")
            listOf(
                reply(text = malformed.text, refs = emptyList()),
                reply(text = malformed.text, refs = malformed.vehicleRefs + malformed.vehicleRefs),
                reply(text = malformed.text, sources = listOf("ne1-0001")),
                reply(text = "설명서 안내 [ne1-0001]"),
                reply(text = malformed.text, id = "another-turn"),
                reply(text = malformed.text, fieldId = "unknown"),
                reply(text = malformed.text, status = "CONVERSATION"),
                reply(text = malformed.text, status = "ANSWERED"),
            ).forEach { assertTrue(policy(capture()).accept(it, evidence) is ConversationResult.Failure) }
            listOf(
                capture(26.0),
                capture(valid = false),
                capture(session = "other"),
                capture(source = VehicleObservationSource.VSS_ADAPTER),
            ).forEach { latest ->
                assertTrue(policy(latest).accept(malformed, evidence) is ConversationResult.Failure)
            }
        }

    @Test fun rejectsCrossTurnAndWrongKindReferences() =
        runTest {
            val evidence = ConversationEvidenceSet(capture())
            listOf(
                reply(id = "another-turn"),
                reply(id = "ne1-0001"),
                reply(status = "CONVERSATION"),
            ).forEach { assertTrue(policy(capture()).accept(it, evidence) is ConversationResult.Failure) }
        }

    @Test fun preservesAiExplanationOfUnavailableEvidence() =
        runTest {
            val result =
                policy(
                    capture(valid = false),
                ).accept(reply(text = "지금은 배터리 잔량을 확인할 수 없어."), ConversationEvidenceSet(capture(valid = false)))
            val text = (result as ConversationResult.Success).value
            assertTrue(text.contains("확인할 수 없어"))
            assertTrue(!text.contains("25%"))
        }

    @Test fun advancingClockKeepsExplicitCaptureTimeAndDoesNotPretendItIsObservationTime() =
        runTest {
            val id = VehicleChatFieldCatalog.TIME
            val before = capture(fieldId = id, value = VehicleValue.Text("2026-10-02T10:00:00+09:00"), deadline = 1000)
            val after = capture(fieldId = id, value = VehicleValue.Text("2026-10-02T10:00:01+09:00"), now = 900)
            val result =
                policy(
                    after,
                ).accept(reply(text = "조회 시점 차량 시계는 10:00:00이었어.", fieldId = id), ConversationEvidenceSet(before))
            assertTrue((result as ConversationResult.Success).value.contains("10:00:00"))
        }

    private fun policy(latest: VehicleChatCapture) =
        GroundedConversationReplyPolicy(VehicleChatEvidenceSource { latest })

    private fun reply(
        text: String = "응, 시뮬레이션에서는 배터리가 25% 남아 있어!",
        id: String = "capture",
        fieldId: String = VehicleChatFieldCatalog.BATTERY,
        status: String = "VEHICLE",
        sources: List<String> = emptyList(),
        refs: List<VehicleFactReference> = listOf(VehicleFactReference(id, fieldId)),
    ) = GroundedReply(1, status, text, sources, refs)

    private fun capture(
        number: Double = 25.0,
        valid: Boolean = true,
        session: String = "session",
        revision: Long = 1,
        fieldId: String = VehicleChatFieldCatalog.BATTERY,
        value: VehicleValue = VehicleValue.Number(number),
        now: Long = 100,
        deadline: Long? = null,
        source: VehicleObservationSource = VehicleObservationSource.DEBUG_OVERRIDE,
    ): VehicleChatCapture {
        val observation =
            VehicleObservation(fieldId, value, source, session, revision, 10, 10)
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
