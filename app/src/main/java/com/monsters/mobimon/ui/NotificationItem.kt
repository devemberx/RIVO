package com.monsters.mobimon.ui

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
}

enum class NotificationKind { VEHICLE, QUEST }

fun notificationItems(
    vehicle: List<VehicleCautionAlert>,
    quests: List<QuestAlert>,
): List<NotificationItem> =
    vehicle.map { NotificationItem(it.id, it.title, NotificationKind.VEHICLE) } +
        quests.map { NotificationItem(it.id, it.title, NotificationKind.QUEST) }
