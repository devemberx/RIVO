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
        assertEquals(
            listOf(
                "경고! 시스템 오류... 나 조금 어지러워, 삐빅.",
                "수호 일시 정지... 오늘은 천천히 가자, 휴먼.",
                "삐빅... 시스템 저하 중. 나 좀 간호해 줘!",
            ),
            pool(friendId = "friend:las", isSick = true),
        )
        assertEquals(
            listOf(
                "긴급! 에너지 위기! 이대로면 방전된다, 삐빅!",
                "충전 케이블 접속 필요! 전원 꺼지면 안 돼!",
                "에너지 부족! 충전 좀 해주면 다시 강해질게!",
            ),
            pool(friendId = "friend:las", isHungry = true),
        )
        assertEquals(
            listOf(
                "삐빅! 터치 감지! 내 노란 불빛 반짝이지?",
                "응급 신호인가? 앗, 심심해서 부른 거야?",
                "위험 감지! ...가 아니라 놀고 싶었던 거야?",
                "삐빅! 스캔 완료! 한참 폼 잡는 중이었는데!",
            ),
            pool(friendId = "friend:las", isTap = true),
        )
        assertEquals(
            listOf(
                "수호 프로토콜 가동! 오늘도 안전하게 가자.",
                "노란 불빛 반짝! 최강 수호자 라스 등장이야.",
                "전방 경계... 앗, 방금 지나간 강아지 봤어?",
                "삐빅! 수호자 라스 가동 완료, 준비됐어!",
            ),
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
