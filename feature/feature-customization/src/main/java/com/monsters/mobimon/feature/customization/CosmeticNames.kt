package com.monsters.mobimon.feature.customization

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

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
        itemId == "background:starlight_yarn_basket" -> stringResource(R.string.pet_item_yarn_basket)
        itemId == "background:star_hanger" -> stringResource(R.string.pet_item_star_hanger)
        itemId == "background:star" -> stringResource(R.string.pet_background_star)
        itemId == "background:snow" -> stringResource(R.string.pet_background_snow)
        itemId == "background:petal" -> stringResource(R.string.pet_background_petal)
        else -> itemId
    }
