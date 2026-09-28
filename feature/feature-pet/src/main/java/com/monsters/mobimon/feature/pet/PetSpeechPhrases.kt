package com.monsters.mobimon.feature.pet

import com.monsters.mobimon.core.ui.companionTimePeriod

internal object PetSpeechPhrases {
    val mobiAutonomous =
        listOf(
            "뭐 하고 있었어? 나 심심했잖아!",
            "오늘 기분은 어때? 히히.",
            "혹시 어디 가려고? 나도 데려가 줘!",
            "내 쫑긋한 귀로 네 마음 다 들어줄게!",
        )
    val mobiTap =
        listOf(
            "앗, 나 불렀어? 왜 부른 거야?",
            "왜 그래, 왜 그래? 나랑 놀고 싶어?",
            "히히, 찌르니까 간지러워! 무슨 일 있어?",
            "짠! 모비 준비 완료! 뭐 할까?",
        )
    val mobiSick =
        listOf(
            "으응... 머리가 띵해... 쓰다듬어 줘...",
            "기운이 하나도 없어... 잠깐만 쉬면 안 될까?",
            "몸이 좀 쑤시는 것 같아... 옆에 있어 줘...",
        )
    val mobiHungry =
        listOf(
            "배에서 꼬르륵 소리가 나! 간식 먹고 싶다~",
            "당 충전이 필요해! 당근 한 조각만 주면 안 돼?",
            "배고파서 기운이 쏙 빠졌어... 얼른 밥 먹자!",
        )

    val lunaAutonomous =
        listOf(
            "너 무슨 생각해? 표정이 멍해보이는데.",
            "심심해 보이네. 내 옆에 좀 앉아있든가.",
            "어디 가려는 건 아니지? 가려면 말하고 가.",
            "멍하니 있지 말고 나랑 눈맞춤이나 하자.",
        )
    val lunaTap =
        listOf(
            "...응? 왜 불러, 무슨 일 있어?",
            "왜 그래? 심심해서 나 건드린 거지?",
            "툭툭 치지 마~ 볼일 있으면 말로 해.",
            "왜, 같이 놀자고? 못 이기는 척 놀아줄게.",
        )
    val lunaSick =
        listOf(
            "...컨디션이 좀 안 좋네. 내 옆에 조금만 있어봐.",
            "으... 열이 좀 나는 것 같은데. 방심했나...",
            "오늘따라 몸이 찌뿌둥하네. 부드럽게 쓰다듬어봐.",
        )
    val lunaHungry =
        listOf(
            "배에서 소리 났지? 못 들은 척해 줘... 그리고 간식 줘.",
            "슬슬 배고플 때 됐는데, 밥 챙겨줄 마음 없어?",
            "기운 없어서 장난칠 힘도 없다. 맛있는 거 줘.",
        )

    fun getPool(
        friendId: String?,
        isTap: Boolean,
        isSick: Boolean,
        isHungry: Boolean,
        timeOfDay: String?,
    ): List<String> {
        val isMobi = friendId != "friend:luna"
        if (isSick) return if (isMobi) mobiSick else lunaSick
        if (isHungry) return if (isMobi) mobiHungry else lunaHungry
        if (isTap) return if (isMobi) mobiTap else lunaTap

        val base = if (isMobi) mobiAutonomous else lunaAutonomous
        return base + timePhrase(isMobi, companionTimePeriod(timeOfDay))
    }

    private fun timePhrase(
        isMobi: Boolean,
        period: String,
    ): String =
        if (isMobi) {
            when (period) {
                "sunrise", "morning" -> "좋은 아침! 기지개 크게 켜고 같이 출발하자!"
                "day", "afternoon" -> "햇살 진짜 좋다! 어디로 놀러 갈까?"
                "sunset" -> "오늘 하루도 진짜 고생 많았어! 토닥토닥~"
                else -> "하품 나온다... 우리 같이 꿈나라로 갈까?"
            }
        } else {
            when (period) {
                "sunrise", "morning" -> "눈 떴어? 조급해하지 말고 천천히 시작해."
                "day", "afternoon" -> "햇볕 따스하네. 지금이 딱 낮잠 타임인데..."
                "sunset" -> "오늘 노을 예쁘다. 너도 잠시 쉬어가."
                else -> "벌써 이 시간이네. 늦었으니까 얼른 자."
            }
        }
}

internal class PetSpeechDeckManager {
    private val decks = mutableMapOf<List<String>, MutableList<String>>()
    private var lastPhrase: String? = null

    fun nextPhrase(pool: List<String>): String {
        if (pool.isEmpty()) return ""
        val key = pool.distinct()
        val deck = decks.getOrPut(key) { mutableListOf() }
        if (deck.isEmpty()) refill(deck, key)
        return deck.removeAt(0).also { lastPhrase = it }
    }

    private fun refill(
        deck: MutableList<String>,
        pool: List<String>,
    ) {
        deck += pool.shuffled()
        if (deck.size > 1 && deck.first() == lastPhrase) {
            val swapIndex = deck.indexOfFirst { it != lastPhrase }
            deck[0] = deck[swapIndex].also { deck[swapIndex] = deck[0] }
        }
        if (deck.size > 2 && deck == pool) {
            deck[deck.lastIndex] = deck[deck.lastIndex - 1].also { deck[deck.lastIndex - 1] = deck.last() }
        }
    }
}
