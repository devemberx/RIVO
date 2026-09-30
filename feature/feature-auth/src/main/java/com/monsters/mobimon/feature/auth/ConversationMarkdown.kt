package com.monsters.mobimon.feature.auth

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

private val strongEmphasis = Regex("\\*\\*(.+?)\\*\\*", RegexOption.DOT_MATCHES_ALL)
private val sourceHeading = Regex("\\n\\n(출처[ \\t]*:[ \\t]*[^\\n]+)")
private val sourceEntry = Regex("(?m)^\\[[1-4]\\][ \\t]+[^\\n]+")
private val sourceMarker = Regex("\\[[1-4]\\]")

internal fun parseConversationMarkdown(text: String): AnnotatedString {
    val normalized = text.replace("\\*", "*")
    val rendered =
        buildAnnotatedString {
            var position = 0
            strongEmphasis.findAll(normalized).forEach { match ->
                if (match.groupValues[1].isNotEmpty()) {
                    append(normalized, position, match.range.first)
                    val start = length
                    append(match.groupValues[1])
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                    position = match.range.last + 1
                }
            }
            append(normalized, position, normalized.length)
        }
    val heading = sourceHeading.find(rendered.text)?.groups?.get(1) ?: return rendered
    return buildAnnotatedString {
        append(rendered)
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), heading.range.first, heading.range.last + 1)
        sourceEntry.findAll(rendered.text, heading.range.last + 1).forEach { match ->
            addStyle(SpanStyle(fontSize = 0.8.em), match.range.first, match.range.last + 1)
        }
        sourceMarker.findAll(rendered.text).forEach { match ->
            addStyle(SpanStyle(fontSize = 0.7.em), match.range.first, match.range.last + 1)
        }
    }
}
