package eu.darken.capod.debug.wakescan

import android.os.SystemClock

/** Small bounded in-memory cache whose entries expire after [ttlMs]. Not thread safe, callers synchronise. */
class RecentCache<V>(
    private val ttlMs: Long = 10_000L,
    private val maxSize: Int = 64,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private class Entry<V>(val value: V, val at: Long)

    // Insertion ordered, so the oldest entry is first.
    private val entries = LinkedHashMap<String, Entry<V>>()

    /** Wrapped so a cached null value can be told apart from a miss. */
    fun get(key: String): Box<V>? {
        val entry = entries[key] ?: return null
        if (clock() - entry.at >= ttlMs) {
            entries.remove(key)
            return null
        }
        return Box(entry.value)
    }

    fun put(key: String, value: V) {
        val now = clock()
        entries.remove(key)
        entries[key] = Entry(value, now)
        if (entries.size > maxSize) {
            entries.entries.removeAll { now - it.value.at >= ttlMs }
            while (entries.size > maxSize) entries.remove(entries.keys.first())
        }
    }

    data class Box<V>(val value: V)
}

/** Allows at most one [tryAcquire] per [intervalMs]. Not thread safe, callers synchronise. */
class Throttle(
    private val intervalMs: Long = 10_000L,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private var last: Long? = null

    fun tryAcquire(): Boolean {
        val now = clock()
        val previous = last
        if (previous != null && now - previous < intervalMs) return false
        last = now
        return true
    }
}
