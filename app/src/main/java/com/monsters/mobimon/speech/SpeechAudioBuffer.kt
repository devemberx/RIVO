package com.monsters.mobimon.speech

/** Bounded PCM handoff: backpressure must never block capture or overwrite earlier samples. */
internal class SpeechAudioBuffer(
    private val capacityBytes: Int,
) {
    private val lock = Object()
    private val chunks = ArrayDeque<ByteArray>()
    private var bytes = 0
    private var finished = false

    fun append(
        source: ByteArray,
        count: Int,
    ): Boolean =
        synchronized(lock) {
            if (finished || count > capacityBytes - bytes) return false
            chunks.addLast(source.copyOf(count))
            bytes += count
            lock.notifyAll()
            true
        }

    fun read(): ByteArray? =
        synchronized(lock) {
            while (chunks.isEmpty() && !finished) lock.wait()
            if (chunks.isEmpty()) return null
            chunks.removeFirst().also { bytes -= it.size }
        }

    fun finish() =
        synchronized(lock) {
            finished = true
            lock.notifyAll()
        }

    fun cancel() =
        synchronized(lock) {
            chunks.forEach { it.fill(0) }
            chunks.clear()
            bytes = 0
            finished = true
            lock.notifyAll()
        }
}
