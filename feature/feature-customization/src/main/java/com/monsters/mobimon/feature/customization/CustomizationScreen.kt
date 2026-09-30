package com.monsters.mobimon.feature.customization

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.ui.CharacterArtwork
import com.monsters.mobimon.core.ui.CharacterAssetImage
import com.monsters.mobimon.core.ui.MobiMonButton
import com.monsters.mobimon.core.ui.MobiMonColors
import com.monsters.mobimon.core.ui.MobiMonMessage
import com.monsters.mobimon.core.ui.MobiMonParkingInterruption
import com.monsters.mobimon.core.ui.MobiMonParkingStatusBadge
import com.monsters.mobimon.core.ui.MobiMonPointSummary
import com.monsters.mobimon.core.ui.MobiMonSelectionCard
import com.monsters.mobimon.core.ui.MobiMonTab
import com.monsters.mobimon.core.ui.PetAvatar
import com.monsters.mobimon.core.ui.StarHanger
import com.monsters.mobimon.core.ui.StarlightYarnBasket
import com.monsters.mobimon.core.ui.companionBackgroundRes
import kotlinx.coroutines.delay

@Composable
fun CustomizationScreen(
    inventory: CosmeticInventory?,
    catalog: List<CosmeticItem>,
    selectedItemId: String?,
    purchasing: Boolean,
    purchaseFailed: Boolean,
    onSelectItem: (String?) -> Unit,
    onPurchaseItem: (String, Long) -> Unit,
    onEquipItem: (String) -> Unit,
    onEquipFriend: (String) -> Unit,
    pointBalance: Long?,
    pointLoadFailed: Boolean,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
    loadFailed: Boolean = false,
    saveFailed: Boolean = false,
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
    timeOfDay: String? = null,
    catalogLoadFailed: Boolean = false,
    interactionAllowed: Boolean = true,
    parkingBadgeConfirmed: Boolean = interactionAllowed,
    storeInventoryReady: Boolean = inventory != null,
    parkingRequired: Boolean = false,
    onHome: (() -> Unit)? = null,
) {
    var tab by rememberSaveable { mutableStateOf(CosmeticSlot.FRIEND) }
    val inventoryPending = !storeInventoryReady
    val catalogPending = catalog.isEmpty()
    val displayCatalog = if (storeInventoryReady) catalog else emptyList()
    val recoveryNeeded = loadFailed || catalogLoadFailed || pointLoadFailed
    var showPending by remember(inventoryPending, catalogPending, recoveryNeeded) { mutableStateOf(false) }
    LaunchedEffect(inventoryPending, catalogPending, recoveryNeeded) {
        if ((inventoryPending || catalogPending) && !recoveryNeeded) {
            delay(200)
            showPending = true
        }
    }
    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier.fillMaxSize().background(MobiMonColors.background).focusProperties { canFocus = !parkingRequired },
        ) {
            val scale = maxWidth.value / 2560f
            val contentHeight = maxHeight
            val reference =
                maxWidth >= 1000.dp && maxHeight >= 1100.dp * scale && LocalDensity.current.fontScale <= 1.2f
            if (!reference) {
                Column(Modifier.fillMaxSize()) {
                    StoreHeader(
                        pointBalance,
                        pointLoadFailed,
                        onBack,
                        parkingBadgeConfirmed,
                        0.6f,
                        Modifier.fillMaxWidth().padding(16.dp),
                    )
                    CompactCustomizationScreen(
                        inventory,
                        displayCatalog,
                        selectedItemId,
                        purchasing,
                        purchaseFailed,
                        onSelectItem,
                        onPurchaseItem,
                        onEquipItem,
                        onEquipFriend,
                        pointBalance,
                        pointLoadFailed,
                        Modifier.weight(1f),
                        saving,
                        loadFailed,
                        saveFailed,
                        onRetry,
                        activeTab = tab,
                        onTabChange = { tab = it },
                        timeOfDay = timeOfDay,
                        catalogLoadFailed = catalogLoadFailed,
                        interactionAllowed = interactionAllowed,
                        showPending = showPending,
                        storeInventoryReady = storeInventoryReady,
                    )
                }
            } else {
                Box(Modifier.fillMaxSize().testTag("store-reference")) {
                    StoreHeader(
                        pointBalance,
                        pointLoadFailed,
                        onBack,
                        parkingBadgeConfirmed,
                        scale,
                        Modifier.offset(72.dp * scale, 36.dp * scale).size(2416.dp * scale, 104.dp * scale),
                    )
                    val presentation = customizationCatalog(inventory, displayCatalog, tab, selectedItemId)
                    val items = presentation.items
                    val selected = presentation.selected
                    val previewFriend = presentation.preview.friendId
                    val accessory = presentation.preview.accessoryId
                    val outfit = presentation.preview.outfitId
                    val background = presentation.preview.backgroundId
                    val equipped = presentation.selectedEquipped
                    val owned = presentation.selectedOwned
                    val observationFailed = loadFailed || catalogLoadFailed
                    val recoveryHeight = maxOf(76.dp, 112.dp * scale)
                    val actionTop = contentHeight - 136.dp * scale
                    val recoveryTop = actionTop - recoveryHeight - 16.dp * scale
                    val catalogBottom =
                        when {
                            recoveryNeeded -> recoveryTop
                            purchaseFailed || saveFailed -> actionTop - 68.dp * scale
                            tab == CosmeticSlot.ACCESSORY -> actionTop - 88.dp * scale
                            else -> actionTop
                        }
                    val catalogHeight = minOf(492.dp * scale, catalogBottom - 518.dp * scale).coerceAtLeast(0.dp)
                    Column(
                        Modifier
                            .offset(72.dp * scale, 196.dp * scale)
                            .size(916.dp * scale, contentHeight - 220.dp * scale)
                            .testTag("store-preview-panel")
                            .background(MobiMonColors.panel, RoundedCornerShape(48.dp * scale))
                            .padding(32.dp * scale),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .weight(
                                    1f,
                                ).clip(RoundedCornerShape(36.dp * scale))
                                .background(MobiMonColors.raised),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            BoxWithConstraints(
                                Modifier.fillMaxWidth().weight(1f).clipToBounds(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(companionBackgroundRes(timeOfDay)),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().testTag("preview-background"),
                                )
                                if (tab != CosmeticSlot.FRIEND) {
                                    if (background == "background:starlight_yarn_basket") {
                                        StarlightYarnBasket(Modifier.fillMaxSize().testTag("store-preview-yarn-basket"))
                                    } else if (background == "background:star_hanger") {
                                        StarHanger(
                                            Modifier.fillMaxSize().testTag("store-preview-star-hanger"),
                                        )
                                    } else if (background != null) {
                                        val particleType =
                                            when {
                                                background.contains(
                                                    "snow",
                                                ) -> com.monsters.mobimon.core.ui.ParticleType.SNOW
                                                background.contains(
                                                    "petal",
                                                ) ||
                                                    background.contains(
                                                        "flower",
                                                    ) -> com.monsters.mobimon.core.ui.ParticleType.PETAL
                                                else -> com.monsters.mobimon.core.ui.ParticleType.STAR
                                            }
                                        com.monsters.mobimon.core.ui.FallingParticlesEffect(
                                            particleType = particleType,
                                            modifier = Modifier.fillMaxSize().testTag("store-preview-particles"),
                                        )
                                    }
                                }
                                val characterSize = minOf(maxWidth, maxHeight) * 0.58f
                                val topOffset = maxHeight * 0.21f
                                if (inventory == null) {
                                    val placeholderSize = characterSize * 0.7f
                                    Box(
                                        Modifier
                                            .align(Alignment.TopCenter)
                                            .offset(y = topOffset + (characterSize - placeholderSize) / 2)
                                            .size(placeholderSize)
                                            .background(MobiMonColors.raised.copy(alpha = 0.75f), CircleShape)
                                            .testTag("store-preview-placeholder"),
                                    )
                                } else if (tab != CosmeticSlot.BACKGROUND) {
                                    PetAvatar(
                                        Modifier
                                            .align(Alignment.TopCenter)
                                            .offset(y = topOffset)
                                            .size(characterSize)
                                            .testTag("preview-character"),
                                        friendId = previewFriend,
                                        accessoryId = accessory,
                                        outfitId = outfit,
                                    )
                                }
                            }
                            val previewTitle =
                                if (inventory == null) {
                                    ""
                                } else if (tab == CosmeticSlot.BACKGROUND) {
                                    selected?.id?.let { cosmeticName(it) } ?: stringResource(R.string.pet_item_none)
                                } else {
                                    storeFriendName(previewFriend)
                                }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(108.dp * scale),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (inventory == null) {
                                    Box(
                                        Modifier
                                            .size(
                                                220.dp * scale,
                                                36.dp * scale,
                                            ).background(MobiMonColors.raised, CircleShape),
                                    )
                                } else {
                                    Text(
                                        previewTitle,
                                        fontSize = (48f * scale).sp,
                                        color = MobiMonColors.text,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().height(154.dp * scale).padding(start = 28.dp * scale),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            if (inventory == null) {
                                Box(
                                    Modifier
                                        .size(
                                            132.dp * scale,
                                            52.dp * scale,
                                        ).background(MobiMonColors.raised, CircleShape),
                                )
                                Spacer(Modifier.width(44.dp * scale))
                                Box(
                                    Modifier
                                        .size(
                                            320.dp * scale,
                                            28.dp * scale,
                                        ).background(MobiMonColors.raised, CircleShape),
                                )
                            } else {
                                Text(
                                    if (equipped) {
                                        when (tab) {
                                            CosmeticSlot.FRIEND -> "동행 중"
                                            CosmeticSlot.ACCESSORY -> "착용 중"
                                            else -> "사용 중"
                                        }
                                    } else {
                                        "미리보기"
                                    },
                                    color = MobiMonColors.accent,
                                    fontSize = (28f * scale).sp,
                                    modifier =
                                        Modifier.background(MobiMonColors.raised, CircleShape).padding(
                                            24.dp * scale,
                                            14.dp * scale,
                                        ),
                                )
                                Spacer(Modifier.width(44.dp * scale))
                                Text(
                                    storePreviewDescription(tab, selected?.id, previewFriend, equipped),
                                    color = MobiMonColors.muted,
                                    fontSize = (28f * scale).sp,
                                )
                            }
                        }
                    }
                    Row(
                        Modifier
                            .offset(
                                1040.dp * scale,
                                196.dp * scale,
                            ).size(1448.dp * scale, 100.dp * scale)
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp * scale),
                    ) {
                        listOf(
                            CosmeticSlot.FRIEND to "친구",
                            CosmeticSlot.ACCESSORY to "옷과 소품",
                            CosmeticSlot.BACKGROUND to "배경",
                        ).forEach { (slot, label) ->
                            MobiMonTab(
                                tab == slot,
                                {
                                    tab = slot
                                    onSelectItem(null)
                                },
                                Modifier
                                    .weight(
                                        when (slot) {
                                            CosmeticSlot.FRIEND -> 432f
                                            CosmeticSlot.ACCESSORY -> 472f
                                            else -> 496f
                                        },
                                    ).fillMaxHeight()
                                    .testTag("store-tab-${slot.name}"),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement =
                                        Arrangement.spacedBy(
                                            24.dp * scale,
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
                                    Text(label, fontSize = (40f * scale).sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Column(Modifier.offset(1040.dp * scale, 338.dp * scale).width(1448.dp * scale)) {
                        Text(
                            if (tab == CosmeticSlot.FRIEND) "함께할 친구" else "작은 소품으로, 새로운 기분",
                            color = MobiMonColors.text,
                            fontSize = (44f * scale).sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp * scale))
                        Text(
                            if (tab == CosmeticSlot.FRIEND) "친구를 바꿔도 보유 아이템은 그대로예요." else "보유한 아이템은 언제든 다시 사용할 수 있어요.",
                            color = MobiMonColors.muted,
                            fontSize = (28f * scale).sp,
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (tab == CosmeticSlot.FRIEND) 2 else 3),
                        modifier =
                            Modifier
                                .offset(
                                    1040.dp * scale,
                                    494.dp * scale,
                                ).size(
                                    1448.dp * scale,
                                    catalogHeight,
                                ).selectableGroup()
                                .testTag("shop-items"),
                        horizontalArrangement = Arrangement.spacedBy(24.dp * scale),
                        verticalArrangement = Arrangement.spacedBy(24.dp * scale),
                    ) {
                        if (items.isEmpty() && showPending && !recoveryNeeded) {
                            items(if (tab == CosmeticSlot.FRIEND) 2 else 3) {
                                StorePendingCard(
                                    Modifier.fillMaxWidth().height(
                                        minOf((if (tab == CosmeticSlot.FRIEND) 492 else 432).dp * scale, catalogHeight),
                                    ),
                                    shape = RoundedCornerShape(36.dp * scale),
                                )
                            }
                        }
                        items(items, key = { it.id }) { item ->
                            val isNone = item.isRemoval
                            val active = inventory?.isEquipped(item) == true
                            MobiMonSelectionCard(
                                item.id == selected?.id,
                                { onSelectItem(item.id) },
                                Modifier
                                    .fillMaxWidth()
                                    .height(
                                        minOf(
                                            (
                                                if (tab ==
                                                    CosmeticSlot.FRIEND
                                                ) {
                                                    492
                                                } else {
                                                    432
                                                }
                                            ).dp * scale,
                                            catalogHeight,
                                        ),
                                    ).then(storeCardRevealModifier(item.id)),
                                shape = RoundedCornerShape(36.dp * scale),
                            ) {
                                Column(Modifier.fillMaxSize().padding(36.dp * scale)) {
                                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                        if (item.slot == CosmeticSlot.FRIEND) {
                                            PetAvatar(Modifier.fillMaxSize(), friendId = item.id)
                                        } else if (isNone) {
                                            Icon(
                                                painterResource(R.drawable.store_check),
                                                null,
                                                Modifier.size(80.dp * scale),
                                                tint = MobiMonColors.accent,
                                            )
                                        } else if (item.id == "background:starlight_yarn_basket") {
                                            StarlightYarnBasket(
                                                Modifier.fillMaxSize(),
                                                centered = true,
                                                isAnimated = false,
                                            )
                                        } else if (item.id == "background:star_hanger") {
                                            StarHanger(Modifier.fillMaxSize(), centered = true, isAnimated = false)
                                        } else if (item.slot == CosmeticSlot.BACKGROUND ||
                                            item.id.startsWith("background:")
                                        ) {
                                            val particleType =
                                                when {
                                                    item.id.contains(
                                                        "snow",
                                                    ) -> com.monsters.mobimon.core.ui.ParticleType.SNOW
                                                    item.id.contains(
                                                        "petal",
                                                    ) ||
                                                        item.id.contains(
                                                            "flower",
                                                        ) -> com.monsters.mobimon.core.ui.ParticleType.PETAL
                                                    else -> com.monsters.mobimon.core.ui.ParticleType.STAR
                                                }
                                            com.monsters.mobimon.core.ui.FallingParticlesEffect(
                                                particleType = particleType,
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                                                particleCount = 18,
                                                minSize = 6.dp,
                                                maxSize = 12.dp,
                                            )
                                        } else {
                                            (
                                                CharacterArtwork.itemIcons[item.id]
                                                    ?: CharacterArtwork.backgrounds[item.id]
                                            )?.let {
                                                CharacterAssetImage(it, Modifier.fillMaxSize())
                                            } ?: Icon(
                                                painterResource(R.drawable.store_check),
                                                null,
                                                Modifier.size(80.dp * scale),
                                                tint = MobiMonColors.accent,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(20.dp * scale))
                                    if (item.slot == CosmeticSlot.FRIEND) {
                                        Row(
                                            Modifier.fillMaxWidth().height(76.dp * scale),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                storeFriendName(item.id),
                                                Modifier.weight(1f),
                                                fontSize = (38f * scale).sp,
                                                color = MobiMonColors.text,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            val selectedCard = item.id == selected?.id
                                            Text(
                                                if (active) {
                                                    "동행 중"
                                                } else if (selectedCard) {
                                                    "✓  선택됨"
                                                } else {
                                                    "보유 중"
                                                },
                                                Modifier
                                                    .background(
                                                        if (selectedCard &&
                                                            !active
                                                        ) {
                                                            MobiMonColors.accent
                                                        } else {
                                                            MobiMonColors.raised
                                                        },
                                                        CircleShape,
                                                    ).padding(horizontal = 36.dp * scale, vertical = 18.dp * scale),
                                                color =
                                                    if (selectedCard &&
                                                        !active
                                                    ) {
                                                        MobiMonColors.background
                                                    } else {
                                                        MobiMonColors.accent
                                                    },
                                                fontSize = (28f * scale).sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        Spacer(Modifier.height(16.dp * scale))
                                    } else {
                                        Text(
                                            cosmeticName(item.id),
                                            fontSize = (36f * scale).sp,
                                            color = MobiMonColors.text,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(Modifier.height(12.dp * scale))
                                        Text(
                                            when {
                                                active -> if (tab == CosmeticSlot.ACCESSORY) "착용 중" else "사용 중"
                                                item.id == selected?.id -> "✓ 선택됨"
                                                isNone || item.id in inventory?.ownedItemIds.orEmpty() -> "보유 중"
                                                else -> "${item.price} P"
                                            },
                                            fontSize = (28f * scale).sp,
                                            color = MobiMonColors.accent,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (tab == CosmeticSlot.ACCESSORY && !purchaseFailed && !saveFailed && !recoveryNeeded) {
                        Box(
                            Modifier
                                .offset(1040.dp * scale, actionTop - 88.dp * scale)
                                .size(1448.dp * scale, 64.dp * scale)
                                .background(MobiMonColors.raised, RoundedCornerShape(24.dp * scale))
                                .padding(horizontal = 40.dp * scale),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                "아이템을 선택하면 친구에게 먼저 입혀 볼 수 있어요.",
                                color = MobiMonColors.muted,
                                fontSize = (28f * scale).sp,
                            )
                        }
                    }
                    if (recoveryNeeded) {
                        Row(
                            Modifier.offset(1040.dp * scale, recoveryTop).size(1448.dp * scale, recoveryHeight),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp * scale),
                        ) {
                            MobiMonMessage(
                                stringResource(
                                    when {
                                        catalogLoadFailed -> R.string.customization_catalog_failed
                                        loadFailed -> R.string.customization_inventory_failed
                                        else -> com.monsters.mobimon.core.ui.R.string.mobimon_points_failed
                                    },
                                ),
                                modifier = Modifier.weight(1f),
                                isError = true,
                            )
                            MobiMonButton(onRetry) { Text(stringResource(R.string.customization_retry)) }
                        }
                    } else if (purchaseFailed || saveFailed) {
                        MobiMonMessage(
                            stringResource(
                                if (saveFailed) {
                                    R.string.pet_inventory_save_failed
                                } else {
                                    R.string.customization_inventory_failed
                                },
                            ),
                            isError = true,
                            modifier =
                                Modifier.offset(1040.dp * scale, actionTop - 68.dp * scale).width(
                                    1448.dp * scale,
                                ),
                        )
                    }
                    val action =
                        when {
                            saving || purchasing -> "적용 중…"
                            selected == null -> "아이템을 골라 주세요"
                            equipped && tab == CosmeticSlot.FRIEND -> "동행 중"
                            equipped && tab == CosmeticSlot.ACCESSORY -> "착용 중"
                            equipped -> "사용 중"
                            owned && tab == CosmeticSlot.FRIEND -> "${storeFriendName(selected.id)}와 함께하기"
                            owned -> "이 모습 적용"
                            pointBalance != null && pointBalance < selected.price -> "포인트 부족 · ${selected.price} P"
                            else -> "${selected.price} P 구매"
                        }
                    MobiMonButton(
                        onClick = {
                            selected?.let {
                                if (!owned) {
                                    onPurchaseItem(it.id, it.price)
                                } else if (it.slot == CosmeticSlot.FRIEND) {
                                    onEquipFriend(it.id)
                                } else {
                                    onEquipItem(it.id)
                                }
                            }
                        },
                        enabled =
                            interactionAllowed &&
                                storeInventoryReady &&
                                selected != null &&
                                !observationFailed &&
                                !equipped &&
                                !saving &&
                                !purchasing &&
                                (owned || (!pointLoadFailed && pointBalance != null && pointBalance >= selected.price)),
                        modifier = Modifier.offset(1040.dp * scale, actionTop).size(1448.dp * scale, 112.dp * scale),
                    ) {
                        if (owned && !equipped && !saving) {
                            Icon(painterResource(R.drawable.store_check), null, Modifier.size(40.dp * scale))
                            Spacer(Modifier.width(32.dp * scale))
                        }
                        Text(action, fontSize = (40f * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (parkingRequired) {
            MobiMonParkingInterruption(
                title = stringResource(R.string.customization_parking_popup_title),
                body = stringResource(R.string.customization_parking_popup_body),
                instruction = stringResource(R.string.customization_parking_popup_instruction),
                preserved = stringResource(R.string.customization_parking_popup_preserved),
                onHome = onHome ?: onBack,
            )
        }
    }
}

@Composable
private fun StoreHeader(
    balance: Long?,
    failed: Boolean,
    onBack: () -> Unit,
    parkingBadgeConfirmed: Boolean,
    scale: Float,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onBack,
            Modifier
                .size(
                    104.dp * scale,
                ).background(MobiMonColors.panel, CircleShape)
                .border(1.dp, MobiMonColors.border, CircleShape),
        ) {
            Icon(
                painterResource(R.drawable.store_back),
                stringResource(com.monsters.mobimon.core.ui.R.string.mobimon_back),
                tint = MobiMonColors.text,
                modifier = Modifier.size(40.dp * scale),
            )
        }
        Spacer(Modifier.width(32.dp * scale))
        Text(
            "꾸미기",
            Modifier.weight(1f).semantics {
                heading()
            },
            color = MobiMonColors.text,
            fontSize = (64f * scale).sp,
            fontWeight = FontWeight.Bold,
        )
        MobiMonPointSummary(
            balance,
            modifier = Modifier.offset(y = -7.dp * scale),
            failed = failed,
            scale = scale,
        )
        Spacer(Modifier.width(48.dp * scale))
        MobiMonParkingStatusBadge(
            confirmed = parkingBadgeConfirmed,
            modifier = Modifier.align(Alignment.Top),
            scale = scale,
        )
    }
}

private fun storeFriendName(id: String): String = if (id == "friend:luna") "루나" else "모비"

@Composable
internal fun storePreviewDescription(
    tab: CosmeticSlot,
    selectedItemId: String?,
    previewFriend: String,
    equipped: Boolean,
): String =
    when (tab) {
        CosmeticSlot.FRIEND ->
            when (previewFriend) {
                "friend:luna" -> stringResource(R.string.pet_preview_desc_friend_luna)
                else -> stringResource(R.string.pet_preview_desc_friend_mobi)
            }
        CosmeticSlot.ACCESSORY ->
            when (selectedItemId) {
                "accessory:luna_cap" -> stringResource(R.string.pet_preview_desc_luna_cap)
                "accessory:luna_sunglasses" -> stringResource(R.string.pet_preview_desc_luna_sunglasses)
                "accessory:mobi_headphones" -> stringResource(R.string.pet_preview_desc_mobi_headphones)
                "accessory:mobi_goggles" -> stringResource(R.string.pet_preview_desc_mobi_goggles)
                else -> stringResource(R.string.pet_preview_desc_none)
            }
        CosmeticSlot.BACKGROUND ->
            when (selectedItemId) {
                "background:starlight_yarn_basket" -> stringResource(R.string.pet_preview_desc_yarn_basket)
                "background:star_hanger" -> stringResource(R.string.pet_preview_desc_star_hanger)
                "background:star" -> stringResource(R.string.pet_preview_desc_background_star)
                "background:snow" -> stringResource(R.string.pet_preview_desc_background_snow)
                "background:petal" -> stringResource(R.string.pet_preview_desc_background_petal)
                else -> stringResource(R.string.pet_preview_desc_background_none)
            }
        else ->
            if (equipped) "내 친구에게 작은 선물을" else "아직 적용되지 않았어요"
    }
