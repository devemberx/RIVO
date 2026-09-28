package com.monsters.mobimon.ui

import com.monsters.mobimon.R
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.navigation.QuestRoute
import com.monsters.mobimon.core.navigation.VehicleRoute
import com.monsters.mobimon.feature.quest.QuestAlert
import com.monsters.mobimon.feature.vehicle.VehicleCautionAlert

data class NotificationItem(
    val id: String,
    val title: String,
    val kind: NotificationKind,
) {
    val route: AppRoute
        get() = if (kind == NotificationKind.VEHICLE) VehicleRoute.VEHICLE_INFO else QuestRoute.QUESTS

    val iconResource: Int
        get() =
            if (kind == NotificationKind.QUEST) {
                R.drawable.notification_gift
            } else {
                when (id) {
                    "battery", "battery-health", "battery-range", "battery-time", "battery-error",
                    "charging", "charging-time",
                    ->
                        R.drawable.notification_battery
                    "tire", "tire-low" -> R.drawable.notification_tire
                    "washer", "washer-low" -> R.drawable.notification_washer
                    "brake-fluid", "pad-wear", "pad-warning", "abs", "parking-brake" -> R.drawable.notification_brake
                    "driver-door", "hood", "trunk" -> R.drawable.notification_door
                    "low-beam", "brake-light" -> R.drawable.notification_lights
                    "assist", "fatigue", "distraction", "driver-belt" -> R.drawable.notification_driver
                    else -> R.drawable.notification_warning
                }
            }

    val supportingText: String
        get() =
            when {
                kind == NotificationKind.QUEST -> "받을 수 있는 보상이 있어요."
                id == "battery" -> "충전 상태를 확인해 주세요."
                id == "tire" || id == "tire-low" -> "차량 상태에서 공기압 정보를 확인하세요."
                else -> "차량 상태에서 자세히 확인하세요."
            }
}

enum class NotificationKind { VEHICLE, QUEST }

fun notificationItems(
    vehicle: List<VehicleCautionAlert>,
    quests: List<QuestAlert>,
): List<NotificationItem> =
    vehicle.map { NotificationItem(it.id, it.title, NotificationKind.VEHICLE) } +
        quests.map { NotificationItem(it.id, it.title, NotificationKind.QUEST) }
