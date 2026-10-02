package com.monsters.mobimon.feature.customization

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.ui.CharacterArtwork
import com.monsters.mobimon.core.ui.CharacterAssetImage
import com.monsters.mobimon.core.ui.FallingParticlesEffect
import com.monsters.mobimon.core.ui.MobiMonButton
import com.monsters.mobimon.core.ui.MobiMonColors
import com.monsters.mobimon.core.ui.MobiMonMessage
import com.monsters.mobimon.core.ui.MobiMonSelectionCard
import com.monsters.mobimon.core.ui.MobiMonTab
import com.monsters.mobimon.core.ui.ParticleType
import com.monsters.mobimon.core.ui.PetAvatar
import com.monsters.mobimon.core.ui.StarHanger
import com.monsters.mobimon.core.ui.StarlightYarnBasket
import com.monsters.mobimon.core.ui.companionBackgroundRes
import com.monsters.mobimon.core.ui.R as CoreUiR

internal enum class StoreSpaceCategory(
    val label: String,
) {
    BACKGROUNDS("배경"),
    EFFECTS("특수효과"),
    PROPS("소품"),
    ;

    fun includes(item: CosmeticItem): Boolean =
        when (this) {
            PROPS -> item.id in setOf("background:star_hanger", "background:starlight_yarn_basket")
            EFFECTS -> item.id in setOf("background:star", "background:snow", "background:petal")
            BACKGROUNDS ->
                item.id !in
                    setOf(
                        "background:star_hanger",
                        "background:starlight_yarn_basket",
                        "background:star",
                        "background:snow",
                        "background:petal",
                    )
        }
}

@Composable
internal fun StoreContent(
    presentation: CustomizationCatalog,
    inventory: CosmeticInventory?,
    catalog: List<CosmeticItem>,
    tab: CosmeticSlot,
    ownedOnly: Boolean,
    category: StoreSpaceCategory,
    action: String,
    actionEnabled: Boolean,
    selectionEnabled: Boolean,
    pending: Boolean,
    recovery: Boolean,
    failed: Boolean,
    onTab: (CosmeticSlot) -> Unit,
    onOwned: () -> Unit,
    onCategory: (StoreSpaceCategory) -> Unit,
    onFriend: (String) -> Unit,
    onSelect: (String) -> Unit,
    onAction: () -> Unit,
    onRetry: () -> Unit,
    timeOfDay: String?,
    scale: Float,
    compact: Boolean,
    errorText: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(48.dp * scale)) {
        Column(
            Modifier
                .weight(1192f)
                .fillMaxHeight()
                .testTag("store-preview-panel")
                .clip(RoundedCornerShape(32.dp * scale))
                .background(MobiMonColors.panel)
                .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        ) {
            StorePreview(
                presentation.preview,
                inventory != null,
                tab,
                timeOfDay,
                Modifier.fillMaxWidth().then(if (compact) Modifier.aspectRatio(16f / 9f) else Modifier.weight(1f)),
            )
            Column(
                Modifier.fillMaxWidth().padding(32.dp * scale),
                verticalArrangement =
                    Arrangement.spacedBy(
                        24.dp * scale,
                    ),
            ) {
                if (inventory != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                24.dp * scale,
                            ),
                    ) {
                        Text(
                            if (tab == CosmeticSlot.FRIEND) {
                                storeFriendName(presentation.preview.friendId)
                            } else {
                                presentation.selected?.let { cosmeticName(it.id, category) }.orEmpty()
                            },
                            color = MobiMonColors.text,
                            fontSize = (44f * scale).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.34f),
                        )
                        Text(
                            storePreviewDescription(
                                tab,
                                presentation.selected?.id,
                                presentation.preview.friendId,
                                presentation.selectedEquipped,
                                category,
                            ),
                            color = MobiMonColors.muted,
                            fontSize = (28f * scale).sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(0.66f),
                        )
                    }
                } else {
                    Box(Modifier.size(220.dp * scale, 48.dp * scale).background(MobiMonColors.raised, CircleShape))
                }
                StoreActionButton(
                    onAction,
                    Modifier.fillMaxWidth().heightIn(min = 104.dp * scale).testTag("store-action"),
                    enabled = actionEnabled,
                    scale = scale,
                ) {
                    Text(action, fontSize = (40f * scale).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(
            Modifier
                .weight(
                    1176f,
                ).fillMaxHeight()
                .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        ) {
            Row(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        24.dp * scale,
                    ),
            ) {
                listOf(
                    CosmeticSlot.FRIEND to "친구",
                    CosmeticSlot.ACCESSORY to "옷",
                    CosmeticSlot.BACKGROUND to "공간",
                ).forEach { (slot, label) ->
                    MobiMonTab(
                        tab == slot,
                        {
                            onTab(slot)
                        },
                        Modifier
                            .weight(
                                1f,
                            ).heightIn(min = 100.dp * scale)
                            .testTag("store-tab-${slot.name}"),
                        enabled = selectionEnabled,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    16.dp * scale,
                                ),
                        ) {
                            Icon(
                                painterResource(
                                    when (slot) {
                                        CosmeticSlot.FRIEND -> R.drawable.store_friends
                                        CosmeticSlot.ACCESSORY -> R.drawable.store_clothes
                                        else -> R.drawable.store_background
                                    },
                                ),
                                null,
                                Modifier.size(
                                    40.dp * scale,
                                ),
                            )
                            Text(label, fontSize = (36f * scale).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp * scale))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement =
                    Arrangement.spacedBy(
                        16.dp * scale,
                    ),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (tab) {
                            CosmeticSlot.FRIEND -> "함께할 친구"
                            CosmeticSlot.ACCESSORY -> "친구의 옷장"
                            else -> "나만의 공간"
                        },
                        color = MobiMonColors.text,
                        fontSize = (34f * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp * scale))
                    Text(
                        when (tab) {
                            CosmeticSlot.FRIEND -> "친구를 바꿔도 보유 아이템은 그대로예요."
                            CosmeticSlot.ACCESSORY -> "아이템을 선택하면 친구에게 먼저 입혀 볼 수 있어요."
                            else -> "배경과 소품, 특수효과로 나만의 공간을 꾸며 보세요."
                        },
                        color = MobiMonColors.muted,
                        fontSize = (28f * scale).sp,
                    )
                }
                Row(
                    Modifier
                        .width(256.dp * scale)
                        .heightIn(min = 76.dp * scale)
                        .clip(RoundedCornerShape(16.dp * scale))
                        .background(if (ownedOnly) MobiMonColors.accent else MobiMonColors.panel)
                        .border(1.dp, MobiMonColors.border, RoundedCornerShape(16.dp * scale))
                        .toggleable(
                            ownedOnly,
                            enabled = selectionEnabled,
                            role = Role.Checkbox,
                            onValueChange = { onOwned() },
                        ).testTag("store-owned-filter")
                        .padding(horizontal = 24.dp * scale, vertical = 16.dp * scale),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp * scale, Alignment.CenterHorizontally),
                ) {
                    Icon(
                        painterResource(R.drawable.store_filter),
                        null,
                        Modifier.size(28.dp * scale),
                        tint = if (ownedOnly) MobiMonColors.onButton else MobiMonColors.muted,
                    )
                    Text(
                        "보유만",
                        color = if (ownedOnly) MobiMonColors.onButton else MobiMonColors.muted,
                        fontSize = (28f * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (tab != CosmeticSlot.FRIEND) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()) {
                    if (tab == CosmeticSlot.ACCESSORY) {
                        catalog.filter { it.slot == CosmeticSlot.FRIEND }.forEach { friend ->
                            StoreSubTab(
                                storeFriendName(friend.id),
                                presentation.preview.friendId == friend.id,
                                { onFriend(friend.id) },
                                scale,
                                selectionEnabled,
                            )
                        }
                    } else {
                        StoreSpaceCategory.entries.forEach {
                            StoreSubTab(
                                it.label,
                                category == it,
                                { onCategory(it) },
                                scale,
                                selectionEnabled,
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MobiMonColors.border.copy(alpha = 0.3f)))
                Spacer(Modifier.height(24.dp * scale))
            } else {
                Spacer(Modifier.height(26.dp * scale))
            }
            LazyVerticalGrid(
                columns =
                    GridCells.Fixed(
                        if (compact ||
                            (tab == CosmeticSlot.BACKGROUND && category == StoreSpaceCategory.BACKGROUNDS)
                        ) {
                            2
                        } else {
                            3
                        },
                    ),
                modifier =
                    Modifier
                        .then(if (compact) Modifier.fillMaxWidth() else Modifier.width(1160.dp * scale))
                        .then(
                            if (compact) Modifier.height(500.dp) else Modifier.weight(1f),
                        ).selectableGroup()
                        .testTag("shop-items"),
                horizontalArrangement = Arrangement.spacedBy(16.dp * scale),
                verticalArrangement =
                    Arrangement.spacedBy(
                        16.dp * scale,
                    ),
            ) {
                if (presentation.items.isEmpty() && pending) {
                    items(if (compact) 2 else 3) {
                        StorePendingCard(
                            Modifier.fillMaxWidth().height(350.dp * scale),
                            RoundedCornerShape(
                                24.dp * scale,
                            ),
                        )
                    }
                }
                items(presentation.items, key = { it.id }) { item ->
                    StoreCard(
                        item,
                        inventory?.isOwned(item) == true,
                        inventory?.isEquippedForFriend(item, presentation.preview.friendId) == true,
                        item.id == presentation.selected?.id,
                        tab,
                        category,
                        timeOfDay,
                        scale,
                        { onSelect(item.id) },
                        selectionEnabled,
                    )
                }
            }
            if (recovery || failed) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp * scale),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MobiMonMessage(errorText, Modifier.weight(1f), isError = true)
                    if (recovery) MobiMonButton(onRetry) { Text("다시 시도") }
                }
            }
        }
    }
}

@Composable
private fun StoreSubTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    scale: Float,
    enabled: Boolean,
) {
    Column(
        Modifier
            .width(
                216.dp * scale,
            ).heightIn(min = 76.dp)
            .selectable(selected, enabled = enabled, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            label,
            Modifier.padding(vertical = 16.dp * scale),
            color = if (selected) MobiMonColors.accent else MobiMonColors.muted,
            fontSize = (32f * scale).sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        Box(
            Modifier
                .width(
                    152.dp * scale,
                ).height(3.dp * scale)
                .background(if (selected) MobiMonColors.accent else MobiMonColors.background),
        )
    }
}

@Composable
private fun StoreCard(
    item: CosmeticItem,
    owned: Boolean,
    equipped: Boolean,
    selected: Boolean,
    tab: CosmeticSlot,
    category: StoreSpaceCategory,
    timeOfDay: String?,
    scale: Float,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    MobiMonSelectionCard(
        selected,
        onClick,
        Modifier.fillMaxWidth().testTag("store-item-${item.id}").then(storeCardRevealModifier(item.id)),
        enabled = enabled,
        shape =
            RoundedCornerShape(
                24.dp * scale,
            ),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp * scale)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(
                        (
                            if (tab == CosmeticSlot.FRIEND) {
                                328
                            } else if (tab == CosmeticSlot.ACCESSORY) {
                                144
                            } else if (category == StoreSpaceCategory.BACKGROUNDS) {
                                295
                            } else {
                                184
                            }
                        ).dp * scale,
                    ).clip(RoundedCornerShape(20.dp * scale))
                    .background(MobiMonColors.raised),
                contentAlignment = Alignment.Center,
            ) {
                StoreItemArtwork(item, category, timeOfDay, Modifier.fillMaxSize().padding(12.dp * scale))
            }
            Spacer(Modifier.height(20.dp * scale))
            Text(
                if (item.slot == CosmeticSlot.FRIEND) storeFriendName(item.id) else cosmeticName(item.id, category),
                color = MobiMonColors.text,
                fontSize = (34f * scale).sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp * scale))
            Text(
                when {
                    equipped ->
                        if (tab ==
                            CosmeticSlot.FRIEND
                        ) {
                            "✓ 동행 중"
                        } else {
                            "✓ 적용 중"
                        }
                    ; item.isRemoval -> "기본 제공"
                    owned -> "보유 중"
                    else -> "${item.price} P"
                },
                color = MobiMonColors.muted,
                fontSize = (28f * scale).sp,
            )
        }
    }
}

@Composable
private fun StoreItemArtwork(
    item: CosmeticItem,
    category: StoreSpaceCategory,
    timeOfDay: String?,
    modifier: Modifier = Modifier,
) {
    when {
        item.isRemoval && item.slot == CosmeticSlot.BACKGROUND && category == StoreSpaceCategory.BACKGROUNDS ->
            Image(
                painterResource(companionBackgroundRes(timeOfDay)),
                null,
                modifier,
                contentScale = ContentScale.Crop,
            )
        item.isRemoval ->
            Box(modifier, contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.store_none), null, Modifier.size(48.dp), tint = MobiMonColors.muted)
            }
        item.slot == CosmeticSlot.FRIEND ->
            PetAvatar(
                modifier,
                friendId = item.id,
                isAnimated =
                    item.id == "friend:luna" || item.id == "friend:las",
            )
        item.id == "background:star_hanger" -> StarHanger(modifier, centered = true, isAnimated = false)
        item.id == "background:starlight_yarn_basket" ->
            StarlightYarnBasket(
                modifier,
                centered = true,
                isAnimated = false,
            )
        item.id == "background:cyberpunk_city" ->
            Image(
                painterResource(CoreUiR.drawable.pet_background_cyberpunk_city),
                null,
                modifier,
                contentScale = ContentScale.Crop,
            )
        item.slot == CosmeticSlot.BACKGROUND ->
            FallingParticlesEffect(
                particleType = storeParticleType(item.id),
                modifier = modifier,
                particleCount = 18,
            )
        else -> CharacterArtwork.itemIcons[item.id]?.let { CharacterAssetImage(it, modifier) }
    }
}

@Composable
private fun StorePreview(
    preview: CosmeticPreview,
    ready: Boolean,
    tab: CosmeticSlot,
    timeOfDay: String?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp))) {
        val zoomBackground = tab == CosmeticSlot.FRIEND || tab == CosmeticSlot.ACCESSORY
        val backgroundRes =
            if (preview.backgroundId == "background:cyberpunk_city") {
                CoreUiR.drawable.pet_background_cyberpunk_city
            } else {
                companionBackgroundRes(timeOfDay)
            }
        Image(
            painterResource(backgroundRes),
            null,
            (
                if (zoomBackground) {
                    Modifier
                        .align(
                            Alignment.Center,
                        ).offset(y = (-20).dp)
                        .requiredSize(maxWidth * 1.7f, maxHeight * 1.7f)
                } else {
                    Modifier.fillMaxSize()
                }
            ).testTag("preview-background"),
            contentScale = ContentScale.Crop,
        )
        if (tab != CosmeticSlot.FRIEND) {
            when (preview.backgroundId) {
                "background:star_hanger" -> StarHanger(Modifier.fillMaxSize().testTag("store-preview-star-hanger"))
                "background:starlight_yarn_basket" ->
                    StarlightYarnBasket(
                        Modifier.fillMaxSize().testTag("store-preview-yarn-basket"),
                    )
                "background:cyberpunk_city" -> Unit
                null -> Unit
                else ->
                    FallingParticlesEffect(
                        particleType = storeParticleType(preview.backgroundId),
                        modifier = Modifier.fillMaxSize().testTag("store-preview-particles"),
                    )
            }
        }
        val characterSize = minOf(maxWidth, maxHeight) * 0.75f
        if (!ready) {
            Box(
                Modifier
                    .align(
                        Alignment.Center,
                    ).size(
                        characterSize * 0.7f,
                    ).background(MobiMonColors.raised.copy(alpha = 0.75f), CircleShape)
                    .testTag("store-preview-placeholder"),
            )
        } else if (tab != CosmeticSlot.BACKGROUND) {
            PetAvatar(
                Modifier
                    .align(
                        Alignment.TopCenter,
                    ).offset(y = maxHeight * 0.17f)
                    .size(characterSize)
                    .testTag("preview-character"),
                friendId = preview.friendId,
                accessoryId = preview.accessoryId,
                outfitId = preview.outfitId,
            )
        }
    }
}

private fun storeParticleType(id: String): ParticleType =
    when {
        id.contains("snow") -> ParticleType.SNOW
        id.contains("petal") || id.contains("flower") -> ParticleType.PETAL
        else -> ParticleType.STAR
    }
