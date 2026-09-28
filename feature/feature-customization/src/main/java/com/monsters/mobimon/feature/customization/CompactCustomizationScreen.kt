package com.monsters.mobimon.feature.customization

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.ui.CharacterArtwork
import com.monsters.mobimon.core.ui.CharacterAssetImage
import com.monsters.mobimon.core.ui.FallingParticlesEffect
import com.monsters.mobimon.core.ui.MobiMonButton
import com.monsters.mobimon.core.ui.MobiMonMessage
import com.monsters.mobimon.core.ui.MobiMonSelectionCard
import com.monsters.mobimon.core.ui.MobiMonTab
import com.monsters.mobimon.core.ui.MobiMonTabs
import com.monsters.mobimon.core.ui.ParticleType
import com.monsters.mobimon.core.ui.PetAvatar
import com.monsters.mobimon.core.ui.StarHanger
import com.monsters.mobimon.core.ui.companionBackgroundRes

@Composable
internal fun CompactCustomizationScreen(
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
    activeTab: CosmeticSlot,
    onTabChange: (CosmeticSlot) -> Unit,
    timeOfDay: String? = null,
    catalogLoadFailed: Boolean = false,
    interactionAllowed: Boolean = true,
    showPending: Boolean = false,
    storeInventoryReady: Boolean = inventory != null,
) {
    var subTab by rememberSaveable { mutableIntStateOf(0) } // 0: 전체, 1: 보유 중

    val observationFailed = loadFailed || catalogLoadFailed
    val recoveryNeeded = observationFailed || pointLoadFailed
    val presentation = customizationCatalog(inventory, catalog, activeTab, selectedItemId, ownedOnly = subTab == 1)
    val effectiveSelectedId = presentation.selected?.id
    val previewFriendId = presentation.preview.friendId
    val previewAccessoryId = presentation.preview.accessoryId
    val previewBackgroundId = presentation.preview.backgroundId
    val filteredItems = presentation.items

    BoxWithConstraints(modifier.fillMaxSize()) {
        val scrollCatalog =
            maxWidth / LocalDensity.current.fontScale < 1000.dp || maxHeight / LocalDensity.current.fontScale < 650.dp
        val catalogScroll = rememberScrollState()
        Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Left Panel: Preview
            Column(
                modifier = Modifier.weight(0.4f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Card(
                    modifier =
                        Modifier
                            .widthIn(max = 540.dp)
                            .fillMaxWidth()
                            .aspectRatio(1.35f)
                            .clip(MaterialTheme.shapes.extraLarge),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.extraLarge)
                                .testTag("preview-background"),
                        ) {
                            Image(
                                painter = painterResource(companionBackgroundRes(timeOfDay)),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.2f),
                                                Color.Black.copy(alpha = 0.35f),
                                            ),
                                        ),
                                    ),
                            )
                            if (previewBackgroundId == "background:star_hanger") {
                                StarHanger(Modifier.fillMaxSize().testTag("preview-star-hanger"), centered = true)
                            } else if (previewBackgroundId != null) {
                                val particleType =
                                    when {
                                        previewBackgroundId.contains("snow") -> ParticleType.SNOW
                                        previewBackgroundId.contains(
                                            "petal",
                                        ) ||
                                            previewBackgroundId.contains("flower") -> ParticleType.PETAL
                                        else -> ParticleType.STAR
                                    }
                                FallingParticlesEffect(
                                    particleType = particleType,
                                    modifier = Modifier.fillMaxSize().testTag("preview-background-particles"),
                                )
                            }
                        }
                        if (inventory != null && activeTab != CosmeticSlot.BACKGROUND) {
                            val characterSize = minOf(maxWidth, maxHeight) * 0.58f
                            val topOffset = maxHeight * 0.21f
                            PetAvatar(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(y = topOffset)
                                        .size(characterSize)
                                        .testTag("preview-character"),
                                friendId = previewFriendId,
                                accessoryId = previewAccessoryId,
                                outfitId = presentation.preview.outfitId,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                val isEquippedSelection = presentation.selectedEquipped
                val previewText =
                    when {
                        effectiveSelectedId == null -> ""
                        isEquippedSelection -> {
                            val name = cosmeticName(effectiveSelectedId)
                            "$name · 현재 착용"
                        }
                        else -> {
                            val name = cosmeticName(effectiveSelectedId)
                            "$name · 착용 미리보기"
                        }
                    }
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            // Right Panel: Catalog and Options
            Column(
                modifier =
                    Modifier
                        .weight(
                            0.6f,
                        ).fillMaxHeight()
                        .then(if (scrollCatalog) Modifier.verticalScroll(catalogScroll) else Modifier),
            ) {
                MobiMonTabs(Modifier.fillMaxWidth()) {
                    listOf(
                        CosmeticSlot.FRIEND to R.string.pet_customization_tab_friend,
                        CosmeticSlot.ACCESSORY to R.string.pet_customization_tab_accessory,
                        CosmeticSlot.BACKGROUND to R.string.pet_customization_tab_background,
                    ).forEach { (slot, label) ->
                        MobiMonTab(activeTab == slot, {
                            onTabChange(slot)
                            onSelectItem(null)
                        }) {
                            Text(stringResource(label))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                MobiMonTabs(Modifier.fillMaxWidth()) {
                    MobiMonTab(
                        subTab == 0,
                        { subTab = 0 },
                    ) { Text(stringResource(R.string.pet_customization_subtab_all)) }
                    MobiMonTab(
                        subTab == 1,
                        { subTab = 1 },
                    ) { Text(stringResource(R.string.pet_customization_subtab_owned)) }
                }
                Text(
                    stringResource(R.string.pet_customization_count_format, filteredItems.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Items Grid
                Box(
                    modifier =
                        Modifier.fillMaxWidth().then(
                            if (scrollCatalog) Modifier.height(400.dp) else Modifier.weight(1f),
                        ),
                ) {
                    LazyVerticalGrid(
                        modifier = Modifier.fillMaxSize().selectableGroup().testTag("shop-items"),
                        columns = GridCells.Adaptive(240.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (filteredItems.isEmpty() && showPending && !recoveryNeeded) {
                            items(2) {
                                StorePendingCard(Modifier.fillMaxWidth().heightIn(min = 180.dp))
                            }
                        }
                        items(filteredItems, key = { it.id }) { item ->
                            val isSelected = item.id == effectiveSelectedId
                            val isOwned = inventory?.isOwned(item) == true
                            val isEquipped = inventory?.isEquipped(item) == true

                            MobiMonSelectionCard(
                                selected = isSelected,
                                onClick = { onSelectItem(item.id) },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 180.dp)
                                        .then(storeCardRevealModifier(item.id)),
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        // Item Icon visual placeholder
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(70.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = CircleShape,
                                                    ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            val iconAsset =
                                                CharacterArtwork.characters[item.id]
                                                    ?: CharacterArtwork.itemIcons[item.id]
                                            if (iconAsset != null) {
                                                CharacterAssetImage(iconAsset, Modifier.size(64.dp))
                                            } else if (item.id == "background:star_hanger") {
                                                StarHanger(Modifier.fillMaxSize(), centered = true, isAnimated = false)
                                            } else if (item.slot == CosmeticSlot.BACKGROUND ||
                                                item.id.startsWith("background:")
                                            ) {
                                                val particleType =
                                                    when {
                                                        item.id.contains("snow") -> ParticleType.SNOW
                                                        item.id.contains(
                                                            "petal",
                                                        ) ||
                                                            item.id.contains("flower") -> ParticleType.PETAL
                                                        else -> ParticleType.STAR
                                                    }
                                                FallingParticlesEffect(
                                                    particleType = particleType,
                                                    modifier = Modifier.fillMaxSize(),
                                                    particleCount = 12,
                                                    minSize = 4.dp,
                                                    maxSize = 10.dp,
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(32.dp),
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        val title = cosmeticName(item.id)

                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )

                                        val subtitle =
                                            when {
                                                isEquipped -> stringResource(R.string.pet_item_status_equipped)
                                                isOwned -> stringResource(R.string.pet_item_status_owned)
                                                else -> stringResource(R.string.pet_item_status_unowned, item.price)
                                            }

                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.labelLarge,
                                            color =
                                                if (isEquipped) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outline
                                                },
                                            textAlign = TextAlign.Center,
                                        )
                                    }

                                    if (isSelected || isEquipped) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier =
                                                Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .size(24.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (recoveryNeeded) {
                    MobiMonMessage(
                        stringResource(
                            when {
                                catalogLoadFailed -> R.string.customization_catalog_failed
                                loadFailed -> R.string.customization_inventory_failed
                                else -> com.monsters.mobimon.core.ui.R.string.mobimon_points_failed
                            },
                        ),
                        isError = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    MobiMonButton(
                        onRetry,
                        Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.customization_retry)) }
                    Spacer(Modifier.height(12.dp))
                }

                if (purchaseFailed) {
                    MobiMonMessage(
                        stringResource(R.string.customization_inventory_failed),
                        isError = true,
                    )
                }
                if (saveFailed) MobiMonMessage(stringResource(R.string.pet_inventory_save_failed), isError = true)

                val selectedItem = presentation.selected
                if (selectedItem != null) {
                    val isNoneItem = selectedItem.isRemoval
                    val isOwned = presentation.selectedOwned
                    val isEquipped = presentation.selectedEquipped

                    val buttonText =
                        when {
                            purchasing -> stringResource(R.string.pet_action_purchasing)
                            saving -> stringResource(R.string.pet_action_equipping)
                            isEquipped -> stringResource(R.string.pet_action_equipped)
                            isOwned -> stringResource(R.string.pet_appearance_apply)
                            else -> {
                                if (pointBalance != null && pointBalance < selectedItem.price) {
                                    stringResource(R.string.pet_action_insufficient_points, selectedItem.price)
                                } else {
                                    stringResource(R.string.pet_action_purchase, selectedItem.price)
                                }
                            }
                        }

                    val buttonEnabled =
                        interactionAllowed &&
                            storeInventoryReady &&
                            !purchasing &&
                            !observationFailed &&
                            !saving &&
                            !isEquipped &&
                            (
                                isOwned ||
                                    (!pointLoadFailed && pointBalance != null && pointBalance >= selectedItem.price)
                            )

                    MobiMonButton(
                        onClick = {
                            if (isNoneItem) {
                                onEquipItem(selectedItem.id)
                            } else if (isOwned) {
                                if (selectedItem.slot == CosmeticSlot.FRIEND) {
                                    onEquipFriend(selectedItem.id)
                                } else {
                                    onEquipItem(selectedItem.id)
                                }
                            } else {
                                onPurchaseItem(selectedItem.id, selectedItem.price)
                            }
                        },
                        enabled = buttonEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = buttonText, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
internal fun cosmeticName(itemId: String): String =
    when {
        itemId.startsWith("none") -> stringResource(R.string.pet_item_none)
        itemId == "friend:mobi" -> stringResource(R.string.pet_friend_mobi)
        itemId == "friend:luna" -> stringResource(R.string.pet_friend_luna)
        itemId == "accessory:mobi_headphones" -> stringResource(R.string.pet_item_mobi_headphones)
        itemId == "accessory:mobi_goggles" -> stringResource(R.string.pet_item_mobi_goggles)
        itemId == "accessory:luna_cap" -> stringResource(R.string.pet_item_luna_cap)
        itemId == "accessory:luna_sunglasses" -> stringResource(R.string.pet_item_luna_sunglasses)
        itemId == "background:star_hanger" -> stringResource(R.string.pet_item_star_hanger)
        itemId == "background:star" -> stringResource(R.string.pet_background_star)
        itemId == "background:snow" -> stringResource(R.string.pet_background_snow)
        itemId == "background:petal" -> stringResource(R.string.pet_background_petal)
        else -> itemId
    }
