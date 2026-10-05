package eu.darken.capod.debug

import eu.darken.capod.debug.wakescan.WakeScanFilter
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class WakeScanFilterTest : BaseTest() {
    @Test
    fun `type byte is matched exactly`() {
        WakeScanFilter.data()[0] shouldBe 0x07.toByte()
        WakeScanFilter.mask()[0] shouldBe 0xFF.toByte()
    }

    @Test
    fun `length byte keeps the lenient mask and the rest is unmasked`() {
        WakeScanFilter.data()[1] shouldBe 25.toByte()
        WakeScanFilter.mask()[1] shouldBe 1.toByte()
        WakeScanFilter.mask().drop(2).all { it == 0.toByte() } shouldBe true
    }
}
