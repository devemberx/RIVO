package com.monsters.mobimon.core.ui

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

data class CharacterAsset(
    @DrawableRes val resourceId: Int,
    val crop: AssetCrop? = null,
    val visualScale: Float = 1f,
    val translationXFraction: Float = 0f,
    val translationYFraction: Float = 0f,
)

data class AssetCrop(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/** Asset IDs stay independent of composition and can be extended when artwork arrives. */
object CharacterArtwork {
    val characters =
        mapOf(
            "friend:mobi" to CharacterAsset(R.drawable.mobimon_mobi, translationYFraction = -35.24f / 1254f),
            "friend:luna" to CharacterAsset(R.drawable.mobimon_luna, visualScale = 0.87f),
        )

    val equippedLooks =
        mapOf(
            "accessory:mobi_headphones" to
                CharacterAsset(
                    R.drawable.mobimon_mobi_headphones,
                    visualScale = 0.93f,
                    translationYFraction = -35.24f / 1254f,
                ),
            "accessory:mobi_goggles" to
                CharacterAsset(R.drawable.mobimon_mobi_goggles, translationYFraction = -35.24f / 1254f),
            "accessory:luna_cap" to
                CharacterAsset(
                    R.drawable.mobimon_luna_cap,
                    visualScale = 0.97f,
                    translationXFraction = 0.022f,
                    translationYFraction = -0.075f,
                ),
            "accessory:luna_sunglasses" to
                CharacterAsset(
                    R.drawable.mobimon_luna_sunglasses,
                    visualScale = 0.97f,
                ),
        )

    val itemIcons =
        mapOf(
            "accessory:mobi_headphones" to CharacterAsset(R.drawable.mobimon_mobi_items, AssetCrop(0, 0, 475, 724)),
            "accessory:mobi_goggles" to CharacterAsset(R.drawable.mobimon_mobi_items, AssetCrop(480, 0, 468, 724)),
            "accessory:luna_cap" to CharacterAsset(R.drawable.mobimon_luna_items, AssetCrop(0, 0, 500, 724)),
            "accessory:luna_sunglasses" to
                CharacterAsset(
                    R.drawable.mobimon_luna_items,
                    AssetCrop(510, 0, 460, 724),
                ),
        )

    // Add drawable resource mappings here when selectable background art is delivered.
    val backgrounds: Map<String, CharacterAsset> = emptyMap()

    val happyCharacters =
        mapOf(
            "friend:mobi" to CharacterAsset(R.drawable.mobimon_mobi_happy, AssetCrop(244, 96, 2156, 2272)),
            "friend:luna" to CharacterAsset(R.drawable.mobimon_luna_happy, AssetCrop(49, 50, 1166, 1158)),
        )

    val happyEquippedLooks =
        mapOf(
            "accessory:mobi_headphones" to
                CharacterAsset(R.drawable.mobimon_mobi_headphones_happy, AssetCrop(129, 64, 1008, 1139)),
            "accessory:mobi_goggles" to
                CharacterAsset(R.drawable.mobimon_mobi_goggles_happy, AssetCrop(152, 58, 985, 1159)),
            "accessory:luna_cap" to
                CharacterAsset(R.drawable.mobimon_luna_cap_happy, AssetCrop(49, 17, 1205, 1237)),
            "accessory:luna_sunglasses" to
                CharacterAsset(R.drawable.mobimon_luna_sunglasses_happy, AssetCrop(49, 50, 1166, 1204)),
        )

    fun preview(
        friendId: String,
        accessoryId: String?,
    ): CharacterAsset = equippedLooks[accessoryId] ?: characters[friendId] ?: characters.getValue("friend:mobi")

    fun happy(
        friendId: String,
        accessoryId: String? = null,
    ): CharacterAsset =
        happyEquippedLooks[accessoryId] ?: happyCharacters[friendId] ?: happyCharacters.getValue("friend:mobi")

    val hungryCharacters =
        mapOf(
            "friend:mobi" to CharacterAsset(R.drawable.mobimon_mobi_hungry, translationYFraction = -35.24f / 1254f),
            "friend:luna" to CharacterAsset(R.drawable.mobimon_luna_hungry, visualScale = 0.87f),
        )

    val sickCharacters =
        mapOf(
            "friend:luna" to CharacterAsset(R.drawable.mobimon_luna_sick, visualScale = 0.87f),
        )

    fun hungry(
        friendId: String,
        accessoryId: String? = null,
    ): CharacterAsset = hungryCharacters[friendId] ?: preview(friendId, accessoryId)

    fun sick(
        friendId: String,
        accessoryId: String? = null,
    ): CharacterAsset = sickCharacters[friendId] ?: preview(friendId, accessoryId)
}

@Composable
fun CharacterAssetImage(
    asset: CharacterAsset,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val crop = asset.crop
    val painter =
        if (crop == null) {
            painterResource(asset.resourceId)
        } else {
            val bitmap =
                remember(
                    asset.resourceId,
                ) { BitmapFactory.decodeResource(context.resources, asset.resourceId).asImageBitmap() }
            remember(bitmap, crop) {
                BitmapPainter(bitmap, srcOffset = IntOffset(crop.x, crop.y), srcSize = IntSize(crop.width, crop.height))
            }
        }
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painter,
            contentDescription,
            modifier =
                Modifier
                    .fillMaxSize(asset.visualScale)
                    .graphicsLayer {
                        translationX = size.width * asset.translationXFraction
                        translationY = size.height * asset.translationYFraction
                    },
            contentScale = ContentScale.Fit,
        )
    }
}
