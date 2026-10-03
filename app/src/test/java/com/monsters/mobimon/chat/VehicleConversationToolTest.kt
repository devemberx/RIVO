package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.ConversationToolCall
import com.monsters.mobimon.core.domain.ConversationToolResult
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleChatFieldCatalog
import com.monsters.mobimon.core.domain.VehicleChatTopic
import com.monsters.mobimon.core.domain.VehicleSnapshot
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
class VehicleConversationToolTest {
    private val snapshot =
        VehicleSnapshot("id", "epoch", 1, 0, SignalSource.REAL, DrivingState.UNKNOWN, SignalQuality.UNAVAILABLE)
    private val reader = VehicleChatEvidenceReader({ snapshot }, { 100 }, { false })

    @Test fun everyTopicIsBoundedAndAllAppFieldsAreQueryableWithoutDefaults() =
        runTest {
            val tool = VehicleConversationTool(reader)
            val ids = mutableSetOf<String>()
            tool.definition.allowedValues.forEach { topic ->
                val result = tool.execute(call(topic)) as ConversationToolResult.Found
                assertTrue(result.content.length < 64_000)
                val json = JSONObject(result.content)
                val fields = json.getJSONArray("fields")
                if (topic != "overview") {
                    repeat(fields.length()) { index ->
                        val field = fields.getJSONObject(index)
                        ids.add(field.getString("id"))
                        assertTrue(field.isNull("value"))
                        assertEquals("UNAVAILABLE", field.getString("quality"))
                    }
                }
                assertTrue(result.evidence.isEmpty())
                assertTrue(result.vehicleCapture != null)
            }
            assertEquals(VehicleChatFieldCatalog.fields.map { it.id }.toSet(), ids)
        }

    @Test fun overviewIsSmallAndArbitraryPathsAreNotAccepted() =
        runTest {
            val tool = VehicleConversationTool(reader)
            val overview = tool.execute(call("overview")) as ConversationToolResult.Found
            assertTrue(overview.vehicleCapture!!.fields.size < VehicleChatFieldCatalog.fields.size)
            assertEquals(VehicleChatTopic.entries.size - 1, tool.definition.allowedValues.size)
            listOf("basic", "Vehicle.Speed", "all", "BATTERY", "battery;service").forEach {
                assertEquals(ConversationToolResult.Unavailable, tool.execute(call(it)))
            }
            // Capture IDs are random and may contain digits; check vehicle values, not metadata.
            val fields = JSONObject(overview.content).getJSONArray("fields")
            repeat(fields.length()) { index -> assertTrue(fields.getJSONObject(index).isNull("value")) }
        }

    private fun call(topic: String) = ConversationToolCall("id", "get_vehicle_context", topic)
}
