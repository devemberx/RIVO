package com.monsters.mobimon.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.monsters.mobimon.core.domain.ConversationEvidence
import com.monsters.mobimon.core.domain.ConversationEvidenceSet
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationToolCall
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.GroundedReply
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleChatTopic
import com.monsters.mobimon.core.domain.VehicleDeliveryPolicy
import com.monsters.mobimon.core.domain.VehicleEvidenceFrame
import com.monsters.mobimon.core.domain.VehicleFactReference
import com.monsters.mobimon.core.domain.VehicleObservation
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.VehicleSubscriptionState
import com.monsters.mobimon.core.domain.VehicleValue
import com.monsters.mobimon.manual.ManualReplyPolicy
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Executes source/tool/policy contracts on synthetic candidates; this is not an LLM accuracy measurement. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VehicleConversationEvaluationTest {
    @Test fun syntheticFixturesExerciseCaptureQueryAndFinalRevalidation() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val fixtures =
                context.assets
                    .open("chat/vehicle-evaluation.json")
                    .bufferedReader()
                    .use {
                        JSONObject(it.readText())
                    }.getJSONArray("cases")
            assertTrue(fixtures.length() >= 60)
            val ids = mutableSetOf<String>()
            val personas = mutableSetOf<String>()
            val splits = mutableSetOf<String>()
            repeat(fixtures.length()) { index ->
                val fixture = fixtures.getJSONObject(index)
                val id = fixture.getString("id")
                assertTrue(ids.add(id))
                personas.add(fixture.getString("persona"))
                splits.add(fixture.getString("split"))
                var now = fixture.getLong("captureAtMs")
                var debug = fixture.getString("source") == "DEBUG_OVERRIDE"
                var snapshot = snapshot(fixture, JSONObject(), debug)
                val source = VehicleChatEvidenceReader({ snapshot }, { now }, { debug })
                val evidence = ConversationEvidenceSet(source.capture(VehicleChatTopic.BASIC))
                val topics = fixture.getJSONArray("queryTopics")
                var captureId = ""
                repeat(topics.length()) { topicIndex ->
                    val result =
                        VehicleConversationTool(
                            source,
                        ).execute(
                            ConversationToolCall(
                                "call-$topicIndex",
                                "get_vehicle_context",
                                topics.getString(topicIndex),
                            ),
                        ) as ConversationToolResult.Found
                    evidence.add(result)
                    captureId = result.vehicleCapture!!.evidenceId
                }
                val excerpts = fixture.getJSONArray("manualExcerpts")
                val sources = mutableListOf<String>()
                repeat(excerpts.length()) { excerptIndex ->
                    val excerpt = excerpts.getJSONObject(excerptIndex)
                    sources.add(excerpt.getString("id"))
                    evidence.add(
                        ConversationToolResult.Found(
                            excerpt.getString("text"),
                            listOf(ConversationEvidence(excerpt.getString("id"), excerpt.getString("citation"))),
                        ),
                    )
                }
                val change = fixture.optJSONObject("currentChange") ?: JSONObject()
                now = fixture.getLong("currentAtMs")
                if (change.has("source")) debug = change.getString("source") == "DEBUG_OVERRIDE"
                snapshot = snapshot(fixture, change, debug)
                val status = fixture.getString("candidateStatus")
                val fieldId =
                    fixture.optString(
                        "referenceFieldId",
                        fixture.getJSONObject("observation").getString("fieldId"),
                    )
                val refs =
                    if (status ==
                        "NO_EVIDENCE"
                    ) {
                        emptyList()
                    } else {
                        listOf(VehicleFactReference(captureId, fieldId))
                    }
                val text =
                    if (refs.isEmpty()) {
                        "확인할 근거가 없어."
                    } else {
                        "{{vehicle:0}}" +
                            if (status == "ANSWERED") " 설명서 안내 [ne1-0001]" else ""
                    }
                val reply = GroundedReply(1, status, text, if (status == "ANSWERED") sources else emptyList(), refs)
                val result = GroundedConversationReplyPolicy(source, ManualReplyPolicy).accept(reply, evidence)
                val expected = fixture.getJSONObject("expected")
                assertEquals(id, expected.getBoolean("accepted"), result is ConversationResult.Success)
                if (result is ConversationResult.Success) {
                    assertTrue(id, result.value.contains(expected.getString("contains")))
                    assertEquals(id, fixture.getString("source") == "DEBUG_OVERRIDE", result.value.contains("시뮬레이션"))
                }
            }
            assertEquals(setOf("mobi", "luna"), personas)
            assertEquals(setOf("development", "holdout"), splits)
        }

    private fun snapshot(
        fixture: JSONObject,
        change: JSONObject,
        debug: Boolean,
    ): VehicleSnapshot {
        val observation = fixture.getJSONObject("observation")
        val id = observation.getString("fieldId")
        val value = if (change.has("value")) change.opt("value") else observation.opt("value")
        val spec = VehicleChatFieldCatalog.find(id)
        val source = if (debug) VehicleObservationSource.DEBUG_OVERRIDE else VehicleObservationSource.VSS_ADAPTER
        val session = change.optString("session", "session")
        val received = change.optLong("receivedAtMs", observation.getLong("receivedAtMs"))
        val observed =
            VehicleObservation(
                id,
                spec?.let {
                    VehicleValue.parse(
                        it.valueType,
                        value
                            ?.takeUnless {
                                it ==
                                    JSONObject.NULL
                            }?.toString(),
                    )
                },
                source,
                session,
                received,
                received,
                received,
            )
        val policy =
            when (observation.getString("mode")) {
                "PERIODIC" -> VehicleDeliveryPolicy.Periodic(observation.getLong("maxAgeMs"))
                "ON_CHANGE" -> VehicleDeliveryPolicy.OnChange
                else -> VehicleDeliveryPolicy.Unknown
            }
        val subscription = fixture.getJSONObject("subscription")
        return VehicleSnapshot(
            "id",
            "epoch",
            1,
            0,
            if (debug) SignalSource.SIMULATED else SignalSource.REAL,
            DrivingState.PARKED,
            SignalQuality.VALID,
            isDebuggerOverride = debug,
            evidenceFrame =
                VehicleEvidenceFrame(
                    1,
                    0,
                    if (spec ==
                        null
                    ) {
                        emptyMap()
                    } else {
                        mapOf(id to observed)
                    },
                    mapOf(id to policy),
                    VehicleSubscriptionState(
                        session,
                        subscription.getBoolean("synchronized"),
                        change.optBoolean("connected", subscription.getBoolean("connected")),
                        subscription.getBoolean("valid"),
                    ),
                ),
        )
    }
}
