package eu.darken.capod.debug.wakescan

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class WakeScanGuardsTest : BaseTest() {

    private var now = 1_000L

    @Test
    fun `throttle allows one per interval`() {
        val throttle = Throttle(10_000L) { now }
        throttle.tryAcquire() shouldBe true
        now += 9_999
        throttle.tryAcquire() shouldBe false
        now += 1
        throttle.tryAcquire() shouldBe true
    }

    @Test
    fun `cache expires and distinguishes cached null from a miss`() {
        val cache = RecentCache<String?>(ttlMs = 10_000L, clock = { now })
        cache.get("a") shouldBe null
        cache.put("a", null)
        cache.get("a") shouldBe RecentCache.Box(null)
        now += 10_000
        cache.get("a") shouldBe null
    }

    @Test
    fun `cache is bounded and evicts the oldest`() {
        val cache = RecentCache<Int>(ttlMs = 10_000L, maxSize = 2, clock = { now })
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)
        cache.get("a") shouldBe null
        cache.get("b") shouldBe RecentCache.Box(2)
        cache.get("c") shouldBe RecentCache.Box(3)
    }
}
