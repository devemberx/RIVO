package com.monsters.mobimon.feature.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.ui.MobiMonDimensions
import com.monsters.mobimon.core.ui.MobiMonColors as Colors
import com.monsters.mobimon.core.ui.R as CoreUiR

@Composable
internal fun QuestHeader(
    friendId: String,
    scale: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onHome: (() -> Unit)? = null,
    isDetail: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val buttonSize = if (scale >= 0.7f) 104.dp * scale else MobiMonDimensions.touchTarget
        val iconSize = if (scale >= 0.7f) 40.dp * scale else 24.dp
        IconButton(
            onClick = onBack,
            modifier =
                Modifier
                    .size(buttonSize)
                    .background(Colors.panel, CircleShape)
                    .border(1.dp, Colors.border, CircleShape)
                    .testTag("quest-header-back-button"),
        ) {
            Icon(
                painter = painterResource(R.drawable.quest_icon_back),
                contentDescription =
                    stringResource(
                        if (isDetail) {
                            R.string.quest_back_to_list
                        } else {
                            CoreUiR.string.mobimon_back
                        },
                    ),
                tint = Colors.text,
                modifier = Modifier.size(iconSize),
            )
        }
        Spacer(Modifier.width(if (scale >= 0.7f) 32.dp * scale else 16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.quest_header_title),
                style = questTextStyle(46f, scale, bold = true, color = Colors.text),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text =
                    stringResource(R.string.quest_header_subtitle).replace(
                        "모비",
                        when (friendId) {
                            "friend:luna" -> "루나"
                            "friend:las" -> "라스"
                            else -> "모비"
                        },
                    ),
                style = questTextStyle(28f, scale, bold = false, color = Colors.muted),
            )
        }
    }
}
