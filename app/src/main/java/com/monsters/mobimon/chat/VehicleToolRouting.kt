package com.monsters.mobimon.chat

import com.monsters.mobimon.core.domain.ConversationTurn
import java.text.Normalizer
import java.util.Locale

/** Narrow complete-utterance rules; mixed or unrecognized language keeps model tool selection. */
internal object VehicleToolRouting {
    fun select(messages: List<ConversationTurn>): Set<String>? {
        val latest = messages.lastOrNull()?.takeIf { it.fromUser } ?: return null
        val question = normalize(latest.text)
        val stateOnly =
            if (question in evidenceFollowups) {
                // User intent supplies the antecedent. Assistant prose/citations are not authoritative metadata.
                messages
                    .dropLast(1)
                    .asReversed()
                    .asSequence()
                    .filter { it.fromUser }
                    .map { normalize(it.text) }
                    .firstOrNull { it !in evidenceFollowups }
                    ?.let(::isStateOnly) == true
            } else {
                isStateOnly(question)
            }
        return if (stateOnly) setOf("get_vehicle_context") else null
    }

    private fun isStateOnly(question: String): Boolean = stateRequests.any { it.matches(question) }

    private fun normalize(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .filterNot(Char::isWhitespace)
            .trimEnd('?', '!', '.', '。')

    private val stateRequests =
        listOf(
            Regex("(?:너|너는|넌|모비|모비는|루나|루나는)?(?:지금)?왜(?:아파|아픈거야|아픈거니|배고파|배고픈거야|배고픈거니)"),
            Regex(
                "(?:지금|현재)?(?:내차의|내차|우리차의|우리차)?(?:타이어상태|타이어공기압|타이어압력|배터리상태|배터리잔량|배터리충전량|차량상태|차상태|충전상태)(?:를|을|가|이|는|은)?(?:확인|확인해|확인해줘|확인해줄래|알려줘|보여줘|어때|얼마야|몇이야|몇퍼센트야)",
            ),
            Regex("(?:지금|현재)?(?:내차의|내차|우리차의|우리차)?(?:타이어|배터리|차량|차|충전)상태"),
            Regex("why(?:areyou|ismobi|isluna)(?:sick|hungry)"),
            Regex(
                "(?:check|showme|tellme|whatis|what's)(?:my|the|current)?(?:current)?(?:tirestatus|tirepressure|batterylevel|batterystatus|vehiclestatus|chargingstatus)",
            ),
            Regex("howare(?:my|the)?tires"),
        )

    private val evidenceFollowups =
        setOf(
            "근거",
            "근거는",
            "근거보여줘",
            "근거알려줘",
            "출처",
            "출처는",
            "출처보여줘",
            "출처알려줘",
            "근거확인",
            "근거확인해줘",
            "답변의근거확인",
            "답변근거확인",
            "답변의근거확인해줘",
            "답변근거확인해줘",
            "차량근거",
            "차량근거보여줘",
            "차량근거알려줘",
            "whatistheevidence",
            "showmetheevidence",
            "what'syoursource",
            "whatisyoursource",
        )
}
