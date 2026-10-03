package com.monsters.mobimon.chat

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsters.mobimon.BuildConfig
import com.monsters.mobimon.core.auth.PersistentGitHubAuthentication
import com.monsters.mobimon.core.domain.ConversationConditionReason
import com.monsters.mobimon.core.domain.ConversationContext
import com.monsters.mobimon.core.domain.ConversationContextSource
import com.monsters.mobimon.core.domain.ConversationEvidenceSet
import com.monsters.mobimon.core.domain.ConversationGroundedReplyPolicy
import com.monsters.mobimon.core.domain.ConversationResult
import com.monsters.mobimon.core.domain.ConversationTools
import com.monsters.mobimon.core.domain.ConversationTurn
import com.monsters.mobimon.core.domain.GitHubSession
import com.monsters.mobimon.core.domain.GroundedReply
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.VehicleChatCapture
import com.monsters.mobimon.core.domain.VehicleChatEvidenceSource
import com.monsters.mobimon.core.domain.VehicleChatField
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleChatTopic
import com.monsters.mobimon.core.domain.VehicleFieldValidity
import com.monsters.mobimon.core.domain.VehicleObservation
import com.monsters.mobimon.core.domain.VehicleObservationSource
import com.monsters.mobimon.core.domain.VehicleValue
import com.monsters.mobimon.di.ConversationToolsModule
import com.monsters.mobimon.manual.AssetManualRetriever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Explicit paid live probe of synthetic inputs; not a real-vehicle/UI test. Never accesses chat storage. */
@RunWith(AndroidJUnit4::class)
class CopilotGroundingLiveProbeTest {
    @Test fun fixedVehicleAndManualTurnsPassProductionAcceptance() =
        runBlocking {
            val arguments = InstrumentationRegistry.getArguments()
            assumeTrue(arguments.getString("live_copilot") == "true")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            // This authorization applies only to the parked synthetic fixture below, never a runtime vehicle.
            val authentication = PersistentGitHubAuthentication.create(context, BuildConfig.GITHUB_CLIENT_ID) { true }
            authentication.restore()
            val session = authentication.session.value
            assertTrue("Live probe needs the existing verified account", session is GitHubSession.Authenticated)
            val account = (session as GitHubSession.Authenticated).account
            val fixtureState = arguments.getString("probe_state")
            require(fixtureState == null || fixtureState == "low_battery")
            val source = fixtureSource(lowBattery = fixtureState == "low_battery")
            val basic = source.capture(VehicleChatTopic.BASIC)
            require(basic.conditionReasons.all { basic.field(it.signal)?.value != null })
            val base = ConversationToolsModule.conversationTools(AssetManualRetriever(context.assets), source)
            val checked = requireNotNull(base.groundedReplyPolicy)
            val tools =
                ConversationTools(
                    base.tools,
                    base.instruction,
                    base.replyPolicy,
                    base.onUsage,
                    groundedReplyPolicy =
                        object : ConversationGroundedReplyPolicy {
                            override suspend fun checkCurrent(evidence: ConversationEvidenceSet) =
                                checked.checkCurrent(evidence)

                            override fun rejectionReason(
                                reply: GroundedReply,
                                evidence: ConversationEvidenceSet,
                            ) = checked.rejectionReason(reply, evidence)

                            override suspend fun accept(
                                reply: GroundedReply,
                                evidence: ConversationEvidenceSet,
                            ): ConversationResult<String> {
                                val obsoleteTokens = reply.text.contains("{{vehicle")
                                val matchedCaptures =
                                    reply.vehicleRefs.count {
                                        evidence.capture(
                                            it.evidenceId,
                                        ) != null
                                    }
                                val matchedFields =
                                    reply.vehicleRefs.count {
                                        evidence.capture(it.evidenceId)?.field(it.fieldId) !=
                                            null
                                    }
                                Log.i(
                                    "MobiMonLiveProbe",
                                    "status=${reply.status} refs=${reply.vehicleRefs.size} " +
                                        "obsoleteTokens=$obsoleteTokens " +
                                        "matchedCaptures=$matchedCaptures matchedFields=$matchedFields " +
                                        "manualSources=${evidence.manualSources.size} " +
                                        "manualReason=${checked.rejectionReason(reply, evidence) ?: "NONE"}",
                                )
                                return checked.accept(reply, evidence)
                            }
                        },
                    selectTools = VehicleToolRouting::select,
                )
            val provider =
                authentication.conversationProvider(
                    ConversationContextSource {
                        ConversationContext(
                            vehicleCapture = source.capture(VehicleChatTopic.BASIC),
                        )
                    },
                    tools,
                )
            val cases =
                listOf(
                    "state" to listOf(ConversationTurn("차량 상태", true)),
                    "state_after_manual" to
                        listOf(
                            ConversationTurn("와이퍼 교체방법", true),
                            ConversationTurn("와이퍼 블레이드는 취급설명서에 따라 교체해. [1] 테스트 설명서 출처", false),
                            ConversationTurn("차량 상태", true),
                        ),
                    "battery" to listOf(ConversationTurn("배터리 잔량 알려줘", true)),
                    "expression" to listOf(ConversationTurn("너 표정이 왜 그래", true)),
                    "wiper" to listOf(ConversationTurn("와이퍼 교체방법", true)),
                    "ordinary" to listOf(ConversationTurn("오늘 하루 이야기할래", true)),
                    "tires" to listOf(ConversationTurn("타이어 상태 알려줘", true)),
                    "tire_refill" to
                        listOf(
                            ConversationTurn("너 왜 아파", true),
                            ConversationTurn("지금 내가 아파. 내 앞 왼쪽 타이어에 공기압 부족 경고가 들어왔거든.", false),
                            ConversationTurn("타이어 공기압은 어떻게 충전할 수 있어", true),
                        ),
                )
            val selected = arguments.getString("probe_case")
            require(selected == null || cases.any { it.first == selected })
            val friend = arguments.getString("probe_friend") ?: "friend:mobi"
            require(friend in setOf("friend:mobi", "friend:luna"))
            val failures = mutableListOf<String>()
            for ((id, messages) in cases.filter { selected == null || it.first == selected }) {
                val started = SystemClock.elapsedRealtime()
                val result = provider.reply(account.id, "live-fixture-$id", friend, messages)
                val success = result is ConversationResult.Success
                val problem = (result as? ConversationResult.Failure)?.problem?.name ?: "NONE"
                Log.i(
                    "MobiMonLiveProbe",
                    "case=$id friend=$friend success=$success problem=$problem " +
                        "elapsedMs=${SystemClock.elapsedRealtime() - started}",
                )
                if (!success) failures.add("$id:$problem")
                if (id == "tires" && result is ConversationResult.Success) {
                    // Only fixed classifications of synthetic input, never provider body logging.
                    val internalPath = result.value.contains("Chassis") || result.value.contains("IsPressureLow")
                    val rawCode = Regex("\\bNG\\b").containsMatchIn(result.value)
                    Log.i("MobiMonLiveProbe", "case=tires internalPath=$internalPath rawCode=$rawCode")
                    assertTrue("Tire explanation must not expose protocol identifiers", !internalPath && !rawCode)
                }
            }
            assertTrue("Rejected synthetic cases: $failures", failures.isEmpty())
        }

    private fun fixtureSource(lowBattery: Boolean): VehicleChatEvidenceSource {
        val sequence = AtomicInteger()
        val values =
            mapOf(
                VehicleChatFieldCatalog.BATTERY to VehicleValue.Number(if (lowBattery) 0.0 else 72.0),
                VehicleChatFieldCatalog.TIME to VehicleValue.Text("2026-10-03T09:00:00+09:00"),
                VehicleChatFieldCatalog.CONDITION to VehicleValue.Text(if (lowBattery) "LOW_BATTERY" else "WARNING"),
                "interpreted.tirePressureStatus" to VehicleValue.Text(if (lowBattery) "OK" else "NG"),
                "Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.IsPressureLow" to VehicleValue.Boolean(!lowBattery),
            )
        val basicIds =
            setOf(VehicleChatFieldCatalog.BATTERY, VehicleChatFieldCatalog.TIME, VehicleChatFieldCatalog.CONDITION) +
                if (lowBattery) emptySet() else setOf("Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.IsPressureLow")
        return VehicleChatEvidenceSource { topic ->
            val fields =
                VehicleChatFieldCatalog.fields
                    .filter {
                        topic == VehicleChatTopic.OVERVIEW ||
                            (topic == VehicleChatTopic.BASIC && it.id in basicIds) ||
                            it.topic == topic
                    }.map { spec ->
                        val value = values[spec.id]
                        val observation =
                            value?.let {
                                VehicleObservation(
                                    spec.id,
                                    it,
                                    VehicleObservationSource.DEBUG_OVERRIDE,
                                    "live-fixture",
                                    1,
                                    100,
                                    100,
                                )
                            }
                        VehicleChatField(
                            spec,
                            observation,
                            VehicleFieldValidity(
                                if (value == null) SignalQuality.UNAVAILABLE else SignalQuality.VALID,
                                if (value == null) "fixture unavailable" else null,
                                0,
                                "ON_CHANGE",
                            ),
                        )
                    }
            VehicleChatCapture(
                "live-fixture-${sequence.incrementAndGet()}",
                100,
                VehicleObservationSource.DEBUG_OVERRIDE,
                "live-fixture",
                fields,
                listOf(
                    if (lowBattery) {
                        ConversationConditionReason("HUNGRY", VehicleChatFieldCatalog.BATTERY, "0", "배터리 잔량 낮음")
                    } else {
                        ConversationConditionReason(
                            "SICK",
                            "Vehicle.Chassis.Axle.Row1.Wheel.Left.Tire.IsPressureLow",
                            "true",
                            "앞 왼쪽 타이어 공기압 부족 경고",
                        )
                    },
                ),
            )
        }
    }
}
