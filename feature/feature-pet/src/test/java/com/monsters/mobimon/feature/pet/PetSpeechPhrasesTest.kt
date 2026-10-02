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

    @Test
    fun lasSpeechPhrasesReturnExpectedPools() {
        assertEquals(listOf("삐빅, 잠깐 쉬어갈까?", "오늘은 천천히 함께하자."), pool(friendId = "friend:las", isSick = true))
        assertEquals(listOf("에너지 충전할 시간이야!", "잠깐 쉬면서 힘을 채워보자."), pool(friendId = "friend:las", isHungry = true))
        assertEquals(listOf("라스 여기 있어! 반가워!", "삐빅, 나 불렀어?"), pool(friendId = "friend:las", isTap = true))
        assertEquals(
            listOf("반가워! 나는 라스야.", "오늘도 네 곁에서 함께할게.", "내 불빛 보이지? 인사하는 중이야!"),
            pool(friendId = "friend:las"),
        )
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
