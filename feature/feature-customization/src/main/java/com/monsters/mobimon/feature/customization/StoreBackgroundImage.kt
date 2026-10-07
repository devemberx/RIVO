package com.monsters.mobimon.feature.customization

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.monsters.mobimon.core.ui.MobiMonColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keep at most two full-resolution scene images at the current 2560 x 1440 size. */
internal object StoreBackgroundCache {
    private data class Key(
        val resources: Resources,
        val id: Int,
    )

    private val images =
        object : LruCache<Key, Bitmap>(30 * 1024 * 1024) {
            override fun sizeOf(
                key: Key,
                value: Bitmap,
            ): Int = value.allocationByteCount
        }
    private val decodeLock = Any()

    fun peek(
        resources: Resources,
        id: Int,
    ): Bitmap? = images.get(Key(resources, id))

    fun load(
        resources: Resources,
        id: Int,
    ): Bitmap? {
        val key = Key(resources, id)
        images.get(key)?.let { return it }
        // Separate from the LRU monitor: a UI cache lookup cannot wait behind decoding.
        return synchronized(decodeLock) {
            images.get(key) ?: try {
                BitmapFactory.decodeResource(resources, id)?.also {
                    it.prepareToDraw()
                    images.put(key, it)
                }
            } catch (_: Resources.NotFoundException) {
                null
            }
        }
    }
}

@Composable
internal fun StoreBackgroundImage(
    drawableRes: Int,
    modifier: Modifier = Modifier,
) {
    val resources = LocalContext.current.resources
    var bitmap by remember(resources, drawableRes) { mutableStateOf(StoreBackgroundCache.peek(resources, drawableRes)) }
    LaunchedEffect(resources, drawableRes) {
        bitmap = withContext(Dispatchers.IO) { StoreBackgroundCache.load(resources, drawableRes) }
    }
    Box(modifier.background(MobiMonColors.raised)) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                null,
                Modifier.fillMaxSize().testTag("store-background-ready-$drawableRes"),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
