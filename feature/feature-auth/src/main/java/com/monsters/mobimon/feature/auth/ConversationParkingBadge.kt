package com.monsters.mobimon.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.ui.mobiMonReferenceTextStyle
import com.monsters.mobimon.core.ui.MobiMonColors as Colors
import com.monsters.mobimon.core.ui.R as CoreUiR

/** Conversation-specific parking geometry from the v5 chat frame. */
@Composable
internal fun ConversationParkingBadge(
    confirmed: Boolean,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val enlargedText = LocalDensity.current.fontScale > 1f
    val width = (if (confirmed) 258.dp else 272.dp) * scale
    val height = 60.dp * scale
    val sizing =
        if (enlargedText) {
            Modifier.widthIn(min = width).heightIn(min = height)
        } else {
            Modifier.size(width, height)
        }
    val status =
        stringResource(
            if (confirmed) CoreUiR.string.mobimon_parking_confirmed else CoreUiR.string.mobimon_parking_restricted,
        )
    Box(
        modifier
            .then(sizing)
            .background(Colors.raised, RoundedCornerShape(30.dp * scale))
            .testTag("chat-parking-badge")
            .semantics(mergeDescendants = true) { contentDescription = status },
    ) {
        val rowModifier =
            if (enlargedText) {
                Modifier.align(Alignment.Center).padding(horizontal = 12.dp * scale, vertical = 8.dp * scale)
            } else {
                Modifier.fillMaxSize()
            }
        Row(
            rowModifier,
            horizontalArrangement =
                Arrangement.spacedBy(
                    (if (confirmed) 16.dp else 24.dp) * scale,
                    Alignment.CenterHorizontally,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(
                    if (confirmed) {
                        CoreUiR.drawable.mobimon_parking
                    } else {
                        CoreUiR.drawable.mobimon_parking_unconfirmed
                    },
                ),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(
                            (if (confirmed) 40.dp else 20.dp) * scale,
                            (if (confirmed) 40.dp else 24.dp) * scale,
                        ).testTag("chat-parking-icon"),
                tint = if (confirmed) Colors.accent else Colors.destructive,
            )
            Text(
                status,
                style = mobiMonReferenceTextStyle(if (confirmed) 26f else 24f, scale),
                color = if (confirmed) Colors.accent else Colors.destructive,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Conversation connection status shown alongside the parking badge. */

@Composable
internal fun ConversationAuthBadge(
    connection: ConversationConnection,
    problem: ConversationProblem?,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val ready = connection == ConversationConnection.READY && problem == null
    val status = stringResource(if (ready) R.string.chat_ready else R.string.chat_checking)
    Box(
        modifier
            .size(244.dp * scale, 60.dp * scale)
            .background(Colors.raised, RoundedCornerShape(30.dp * scale))
            .testTag("chat-auth-badge")
            .semantics(mergeDescendants = true) { contentDescription = status },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            status,
            modifier = Modifier.offset(y = -2.dp * scale),
            style = mobiMonReferenceTextStyle(26f, scale),
            color = if (ready) Colors.success else Colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
