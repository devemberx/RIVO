package com.monsters.mobimon.feature.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsters.mobimon.core.domain.CosmeticInventory
import com.monsters.mobimon.core.domain.CosmeticItem
import com.monsters.mobimon.core.domain.CosmeticSlot
import com.monsters.mobimon.core.ui.MobiMonColors
import com.monsters.mobimon.core.ui.MobiMonParkingInterruption
import com.monsters.mobimon.core.ui.MobiMonParkingStatusBadge
import com.monsters.mobimon.core.ui.MobiMonPointSummary
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
    var ownedOnly by rememberSaveable { mutableStateOf(false) }
    var clothesFriend by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf(StoreSpaceCategory.BACKGROUNDS) }
    var selectedBackgroundThemeId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedBackgroundPropId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedBackgroundEffectId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmingId by rememberSaveable { mutableStateOf<String?>(null) }
    var submittedId by remember { mutableStateOf<String?>(null) }
    val recoveryNeeded = loadFailed || catalogLoadFailed || pointLoadFailed
    val pending = !storeInventoryReady || catalog.isEmpty()
    var showPending by remember(pending, recoveryNeeded) { mutableStateOf(false) }
    LaunchedEffect(pending, recoveryNeeded) {
        if (pending && !recoveryNeeded) {
            delay(200)
            showPending = true
        }
    }
    LaunchedEffect(parkingRequired, interactionAllowed) {
        if (parkingRequired || !interactionAllowed) {
            confirmingId = null
            submittedId = null
        }
    }
    LaunchedEffect(selectedItemId, tab, category) {
        if (tab == CosmeticSlot.BACKGROUND && selectedItemId != null) {
            when (category) {
                StoreSpaceCategory.BACKGROUNDS -> selectedBackgroundThemeId = selectedItemId
                StoreSpaceCategory.PROPS -> selectedBackgroundPropId = selectedItemId
                StoreSpaceCategory.EFFECTS -> selectedBackgroundEffectId = selectedItemId
            }
        }
    }
    val available = if (storeInventoryReady) catalog else emptyList()
    val scopedCatalog =
        if (tab ==
            CosmeticSlot.BACKGROUND
        ) {
            available.filter { it.slot != tab || category.includes(it) }
        } else {
            available
        }
    val presentation =
        customizationCatalog(
            inventory,
            scopedCatalog,
            tab,
            selectedItemId,
            ownedOnly,
            clothesFriend,
            category,
            selectedBackgroundThemeId,
            selectedBackgroundPropId,
            selectedBackgroundEffectId,
        )
    val selected = presentation.selected
    val activeFriend = inventory?.equippedItemIds?.get(CosmeticSlot.FRIEND) ?: "friend:mobi"
    val otherFriend = tab == CosmeticSlot.ACCESSORY && presentation.preview.friendId != activeFriend
    val previewFriendOwned =
        storeInventoryReady && inventory?.ownedItemIds?.contains(presentation.preview.friendId) == true
    val unownedOtherFriend = otherFriend && storeInventoryReady && !previewFriendOwned
    val switchFriend = otherFriend && previewFriendOwned && presentation.selectedOwned
    val busy =
        saving ||
            purchasing ||
            (submittedId != null && !purchaseFailed && submittedId !in inventory?.ownedItemIds.orEmpty())
    LaunchedEffect(submittedId, inventory, purchaseFailed) {
        if (purchaseFailed || submittedId in inventory?.ownedItemIds.orEmpty()) submittedId = null
    }
    val enabled =
        interactionAllowed &&
            !parkingRequired &&
            storeInventoryReady &&
            selected != null &&
            !loadFailed &&
            !catalogLoadFailed &&
            !busy &&
            !unownedOtherFriend &&
            (!presentation.selectedEquipped || switchFriend) &&
            (
                presentation.selectedOwned ||
                    (!pointLoadFailed && pointBalance != null && pointBalance >= selected.price)
            )
    val action =
        when {
            busy -> "적용 중…"
            selected == null -> "아이템을 골라 주세요"
            unownedOtherFriend -> "구매 전"
            switchFriend -> "${storeFriendName(presentation.preview.friendId)}와 함께하기"
            presentation.selectedEquipped ->
                when (tab) {
                    CosmeticSlot.FRIEND -> "동행 중"
                    CosmeticSlot.ACCESSORY -> "착용 중"
                    else -> "사용 중"
                }
            presentation.selectedOwned && tab == CosmeticSlot.FRIEND -> "${storeFriendName(selected.id)}와 함께하기"
            presentation.selectedOwned -> "이 모습 적용"
            pointBalance != null && pointBalance < selected.price -> "${selected.price - pointBalance} P 부족"
            else -> "${selected.price} P로 구매하기"
        }
    BoxWithConstraints(modifier.fillMaxSize().background(MobiMonColors.background)) {
        val scale = maxWidth.value / 2560f
        val reference = maxWidth >= 1000.dp && maxHeight >= 1100.dp * scale && LocalDensity.current.fontScale <= 1.2f
        val uiScale = if (reference) scale else maxOf(scale, 0.55f)
        Column(Modifier.fillMaxSize().then(if (reference) Modifier.testTag("store-reference") else Modifier)) {
            StoreHeader(
                pointBalance,
                pointLoadFailed,
                onBack,
                parkingBadgeConfirmed,
                uiScale,
                Modifier
                    .fillMaxWidth()
                    .padding(start = 72.dp * scale, end = 72.dp * scale, top = 36.dp * scale)
                    .then(
                        if (reference) Modifier.height(104.dp * uiScale) else Modifier.heightIn(min = 104.dp * uiScale),
                    ),
            )
            Spacer(Modifier.height(56.dp * scale))
            StoreContent(
                presentation,
                inventory,
                available,
                tab,
                ownedOnly,
                category,
                action,
                enabled,
                !busy,
                showPending && !recoveryNeeded,
                recoveryNeeded,
                purchaseFailed || saveFailed,
                onTab = {
                    if (!busy) {
                        tab = it
                        onSelectItem(null)
                    }
                },
                onOwned = {
                    if (!busy) {
                        ownedOnly = !ownedOnly
                        onSelectItem(null)
                    }
                },
                onCategory = {
                    if (!busy) {
                        category = it
                        onSelectItem(null)
                    }
                },
                onFriend = {
                    if (!busy) {
                        clothesFriend = it
                        onSelectItem(null)
                    }
                },
                onSelect = { if (!busy) onSelectItem(it) },
                onAction = {
                    if (enabled) {
                        val item = requireNotNull(selected)
                        when {
                            !presentation.selectedOwned -> confirmingId = item.id
                            switchFriend -> onEquipFriend(presentation.preview.friendId)
                            item.slot == CosmeticSlot.FRIEND -> onEquipFriend(item.id)
                            else -> {
                                val equipId =
                                    if (item.isRemoval && tab == CosmeticSlot.BACKGROUND) {
                                        when (category) {
                                            StoreSpaceCategory.BACKGROUNDS -> "none:background"
                                            StoreSpaceCategory.PROPS -> "none:background_prop"
                                            StoreSpaceCategory.EFFECTS -> "none:background_effect"
                                        }
                                    } else {
                                        item.id
                                    }
                                onEquipItem(equipId)
                            }
                        }
                    }
                },
                onRetry = onRetry,
                timeOfDay = timeOfDay,
                scale = uiScale,
                compact = !reference,
                modifier = Modifier.weight(1f).padding(horizontal = 72.dp * scale).padding(bottom = 56.dp * scale),
                errorText =
                    stringResource(
                        when {
                            catalogLoadFailed -> R.string.customization_catalog_failed
                            loadFailed || purchaseFailed -> R.string.customization_inventory_failed
                            saveFailed -> R.string.pet_inventory_save_failed
                            else -> com.monsters.mobimon.core.ui.R.string.mobimon_points_failed
                        },
                    ),
            )
        }
        val confirming = available.firstOrNull { it.id == confirmingId }
        if (confirming != null && !parkingRequired && interactionAllowed) {
            StorePurchaseDialog(
                confirming,
                pointBalance,
                purchasing || submittedId != null,
                enabled = enabled && selected?.id == confirming.id && !pointLoadFailed,
                onDismiss = { confirmingId = null },
                onConfirm = {
                    if (enabled && submittedId == null && pointBalance != null && pointBalance >= confirming.price) {
                        submittedId = confirming.id
                        confirmingId = null
                        onPurchaseItem(confirming.id, confirming.price)
                    }
                },
            )
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
        Column(Modifier.weight(1f)) {
            Text(
                "꾸미기",
                Modifier.semantics { heading() },
                color = MobiMonColors.text,
                fontSize = (48f * scale).sp,
                lineHeight = (56f * scale).sp,
                fontWeight = FontWeight.Bold,
            )
            Text("나만의 친구와 공간", color = MobiMonColors.muted, fontSize = (28f * scale).sp, lineHeight = (40f * scale).sp)
        }
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

internal fun storeFriendName(id: String): String =
    when (id) {
        "friend:luna" -> "루나"
        "friend:las" -> "라스"
        else -> "모비"
    }

@Composable
internal fun storePreviewDescription(
    tab: CosmeticSlot,
    selectedItemId: String?,
    previewFriend: String,
    equipped: Boolean,
    category: StoreSpaceCategory = StoreSpaceCategory.BACKGROUNDS,
): String =
    when (tab) {
        CosmeticSlot.FRIEND ->
            when (previewFriend) {
                "friend:luna" -> stringResource(R.string.pet_preview_desc_friend_luna)
                "friend:las" -> stringResource(R.string.pet_preview_desc_friend_las)
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
                "background:cyberpunk_city" -> stringResource(R.string.pet_preview_desc_background_cyberpunk_city)
                else ->
                    stringResource(
                        when (category) {
                            StoreSpaceCategory.BACKGROUNDS -> R.string.pet_preview_desc_background_none
                            StoreSpaceCategory.EFFECTS -> R.string.pet_preview_desc_effect_none
                            StoreSpaceCategory.PROPS -> R.string.pet_preview_desc_prop_none
                        },
                    )
            }
        else ->
            if (equipped) "내 친구에게 작은 선물을" else "아직 적용되지 않았어요"
    }
