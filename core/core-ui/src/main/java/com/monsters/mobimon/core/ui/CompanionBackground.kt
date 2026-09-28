package com.monsters.mobimon.core.ui

import androidx.annotation.DrawableRes

/** Normalizes an interpreted VSS time label or hour into a background period. */
fun companionTimePeriod(timeOfDay: String?): String {
    val value = timeOfDay?.trim()?.lowercase(java.util.Locale.ROOT)
    return when (value) {
        "sunrise", "일출", "새벽" -> "sunrise"
        "morning", "아침" -> "morning"
        "day", "낮" -> "day"
        "afternoon", "오후", "늦은 오후" -> "afternoon"
        "sunset", "노을", "저녁" -> "sunset"
        "night", "밤" -> "night"
        "midnight", "한밤", "한밤중", "자정" -> "midnight"
        else ->
            when (value?.substringBefore(':')?.toIntOrNull()) {
                in 0..4 -> "midnight"
                in 5..6 -> "sunrise"
                in 7..11 -> "morning"
                in 12..15 -> "day"
                in 16..17 -> "afternoon"
                in 18..19 -> "sunset"
                else -> "night"
            }
    }
}

/** Shared artwork mapping; missing time uses Night without reading a clock during composition. */
@DrawableRes
fun companionBackgroundRes(timeOfDay: String?): Int =
    when (companionTimePeriod(timeOfDay)) {
        "sunrise" -> R.drawable.pet_home_background_sunrise
        "morning" -> R.drawable.pet_home_background_morning
        "day" -> R.drawable.pet_home_background_day
        "afternoon" -> R.drawable.pet_home_background_afternoon
        "sunset" -> R.drawable.pet_home_background_sunset
        "midnight" -> R.drawable.pet_home_background_midnight
        else -> R.drawable.pet_home_background_night
    }
