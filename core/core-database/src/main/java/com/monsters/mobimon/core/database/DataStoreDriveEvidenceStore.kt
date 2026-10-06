package com.monsters.mobimon.core.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.monsters.mobimon.core.domain.DriveEvidenceState
import com.monsters.mobimon.core.domain.DriveEvidenceStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Stores drive evidence beside preferences; unreadable data restarts evidence instead of guessing. */
class DataStoreDriveEvidenceStore(
    private val dataStore: DataStore<Preferences>,
) : DriveEvidenceStore {
    override suspend fun read(): DriveEvidenceState? {
        val json = dataStore.data.first()[DRIVE_EVIDENCE] ?: return null
        return try {
            JSONObject(json).toState()
        } catch (_: JSONException) {
            null
        }
    }

    override suspend fun write(state: DriveEvidenceState) {
        val json = state.toJson().toString()
        dataStore.edit { it[DRIVE_EVIDENCE] = json }
    }

    private fun DriveEvidenceState.toJson() =
        JSONObject()
            .put("driveId", driveId)
            .put("driving", driving)
            .putOpt("startOdometerMeters", startOdometerMeters)
            .putOpt("lastOdometerMeters", lastOdometerMeters)
            .put("completedDistanceKm", completedDistanceKm.toDouble())
            .put("beltedMillis", beltedMillis)
            .put("hardAccelCount", hardAccelCount)
            .put("hardBrakeCount", hardBrakeCount)
            .put("overspeedCount", overspeedCount)
            .put("laneDepartureCount", laneDepartureCount)
            .put("distracted", distracted)
            .put("observedSignals", JSONArray(observedSignals.toList()))
            .put("activeEdges", JSONArray(activeEdges.toList()))
            .put("turnSignalDate", turnSignalDate)
            .put("turnSignalCount", turnSignalCount)
            .put("safeDriveWeek", safeDriveWeek)
            .put("safeDriveCount", safeDriveCount)
            .putOpt("tireNormalSinceUtcMillis", tireNormalSinceUtcMillis)
            .put("raining", raining)
            .put("batteryChargedProperly", batteryChargedProperly)
            .put("washerRefilled", washerRefilled)

    // Elapsed time does not survive a reboot, so belt timing restarts from the next observation.
    private fun JSONObject.toState() =
        DriveEvidenceState(
            driveId = getString("driveId"),
            driving = getBoolean("driving"),
            startOdometerMeters = optDoubleOrNull("startOdometerMeters"),
            lastOdometerMeters = optDoubleOrNull("lastOdometerMeters"),
            completedDistanceKm = getDouble("completedDistanceKm").toFloat(),
            beltedMillis = getLong("beltedMillis"),
            hardAccelCount = getInt("hardAccelCount"),
            hardBrakeCount = getInt("hardBrakeCount"),
            overspeedCount = getInt("overspeedCount"),
            laneDepartureCount = getInt("laneDepartureCount"),
            distracted = getBoolean("distracted"),
            observedSignals = getJSONArray("observedSignals").strings(),
            activeEdges = getJSONArray("activeEdges").strings(),
            turnSignalDate = getString("turnSignalDate"),
            turnSignalCount = getInt("turnSignalCount"),
            safeDriveWeek = getString("safeDriveWeek"),
            safeDriveCount = getInt("safeDriveCount"),
            tireNormalSinceUtcMillis =
                if (has(
                        "tireNormalSinceUtcMillis",
                    )
                ) {
                    getLong("tireNormalSinceUtcMillis")
                } else {
                    null
                },
            raining = getBoolean("raining"),
            batteryChargedProperly = getBoolean("batteryChargedProperly"),
            washerRefilled = getBoolean("washerRefilled"),
        )

    private fun JSONObject.optDoubleOrNull(name: String) = if (has(name)) getDouble(name) else null

    private fun JSONArray.strings() = (0 until length()).mapTo(mutableSetOf()) { getString(it) }

    private companion object {
        val DRIVE_EVIDENCE = stringPreferencesKey("drive_evidence")
    }
}
