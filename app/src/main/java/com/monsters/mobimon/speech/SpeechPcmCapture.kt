package com.monsters.mobimon.speech

/** Normal Stop drains a bounded native tail; Cancel intentionally discards pending input. */
internal fun captureSpeechPcm(
    cancelled: () -> Boolean,
    stopping: () -> Boolean,
    read: (ByteArray, Boolean) -> Int,
    append: (ByteArray, Int) -> Boolean,
    nativeBufferBytes: Int,
    onFailure: () -> Unit,
) {
    val chunk = ByteArray(640)
    var remainingDrain = nativeBufferBytes
    try {
        while (!cancelled()) {
            val draining = stopping()
            val count = read(chunk, draining)
            if (count <= 0) {
                if (!cancelled() && (count < 0 || !draining)) onFailure()
                break
            }
            if (cancelled()) break
            if (!append(chunk, count)) {
                onFailure()
                break
            }
            if (draining) {
                remainingDrain -= count
                if (remainingDrain <= 0) break
            }
        }
    } finally {
        chunk.fill(0)
    }
}
