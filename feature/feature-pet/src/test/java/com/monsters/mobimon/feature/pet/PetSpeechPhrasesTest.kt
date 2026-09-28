package com.monsters.mobimon.feature.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetSpeechPhrasesTest {
    @Test
    fun deckCyclesWithoutDuplicatesAndKeepsProgressAcrossPoolSwitches() {
        val firstPool = listOf("1", "2", "3", "4", "5")
        val secondPool = listOf("a", "b", "c")
        val deck = PetSpeechDeckManager()

        val firstPart = (1..2).map { deck.nextPhrase(firstPool) }
        repeat(3) { deck.nextPhrase(secondPool) }
        val firstCycle = firstPart + (1..3).map { deck.nextPhrase(firstPool) }

        assertEquals(firstPool.toSet(), firstCycle.toSet())
        assertEquals(firstCycle.size, firstCycle.distinct().size)
        assertFalse(firstCycle == firstPool)
    }

    @Test
    fun autonomousPoolAddsOnlyItsCurrentTimePhrase() {
        val morning = pool(friendId = "friend:mobi", timeOfDay = "08:00:00")
        assertEquals(PetSpeechPhrases.mobiAutonomous.size + 1, morning.size)
        assertTrue(morning.contains("좋은 아침! 기지개 크게 켜고 같이 출발하자!"))

        val afternoon = pool(friendId = "friend:luna", timeOfDay = "16:00:00")
        assertEquals(PetSpeechPhrases.lunaAutonomous.size + 1, afternoon.size)
        assertTrue(afternoon.contains("햇볕 따스하네. 지금이 딱 낮잠 타임인데..."))
    }

    @Test
    fun tapPoolExcludesTimePhrases() {
        val result = pool(friendId = "friend:luna", isTap = true, timeOfDay = "08:00:00")
        assertEquals(PetSpeechPhrases.lunaTap, result)
        assertFalse(result.contains("눈 떴어? 조급해하지 말고 천천히 시작해."))
    }

    @Test
    fun sickAndHungryPoolsAreExclusive() {
        assertEquals(PetSpeechPhrases.mobiSick, pool(friendId = "friend:mobi", isSick = true))
        assertEquals(PetSpeechPhrases.lunaHungry, pool(friendId = "friend:luna", isTap = true, isHungry = true))
    }

    private fun pool(
        friendId: String,
        isTap: Boolean = false,
        isSick: Boolean = false,
        isHungry: Boolean = false,
        timeOfDay: String = "12:00:00",
    ) = PetSpeechPhrases.getPool(
        friendId = friendId,
        isTap = isTap,
        isSick = isSick,
        isHungry = isHungry,
        timeOfDay = timeOfDay,
    )
}
