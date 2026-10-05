package eu.darken.capod.debug.wakescan

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class WakeScanDecisionTest : BaseTest() {

    private fun decide(
        enabled: Boolean = true,
        address: Boolean = true,
        connected: Boolean = false,
        since: Long? = null,
    ) = WakeScanDecision.decide(enabled, address, connected, since)

    @Test
    fun `connects when everything allows it`() {
        decide() shouldBe WakeScanDecision.Result.Connect
    }

    @Test
    fun `skips when auto connect is off`() {
        decide(enabled = false) shouldBe WakeScanDecision.Result.Skip(WakeScanDecision.Reason.AUTO_CONNECT_OFF)
    }

    @Test
    fun `skips without address`() {
        decide(address = false) shouldBe WakeScanDecision.Result.Skip(WakeScanDecision.Reason.NO_ADDRESS)
    }

    @Test
    fun `skips when already connected`() {
        decide(connected = true) shouldBe WakeScanDecision.Result.Skip(WakeScanDecision.Reason.ALREADY_CONNECTED)
    }

    @Test
    fun `skips within the throttle window`() {
        decide(since = 29_999L) shouldBe WakeScanDecision.Result.Skip(WakeScanDecision.Reason.THROTTLED)
    }

    @Test
    fun `connects once the throttle window elapsed`() {
        decide(since = 30_000L) shouldBe WakeScanDecision.Result.Connect
    }

    @Test
    fun `auto connect off wins over other reasons`() {
        decide(enabled = false, address = false, connected = true, since = 1L) shouldBe
            WakeScanDecision.Result.Skip(WakeScanDecision.Reason.AUTO_CONNECT_OFF)
    }

    @Test
    fun `attempt tracker reports time since last attempt`() {
        var now = 1_000L
        val tracker = AttemptTracker { now }
        tracker.millisSinceLastAttempt("a") shouldBe null
        tracker.markAttempt("a")
        now = 1_500L
        tracker.millisSinceLastAttempt("a") shouldBe 500L
        tracker.millisSinceLastAttempt("b") shouldBe null
    }
}
