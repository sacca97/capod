package eu.darken.capod.monitor.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CompactBatteryTextTest : BaseTest() {

    private fun earbuds(left: Float, right: Float, case: Float) = listOf(
        CompactBatteryLevel("L", left),
        CompactBatteryLevel("R", right),
        CompactBatteryLevel("Case", case),
    )

    @Test
    fun `all levels known`() {
        compactBatteryText(earbuds(0.8f, 0.86f, 0.85f), "-") shouldBe "L 80%  ·  R 86%  ·  Case 85%"
    }

    @Test
    fun `unknown levels show a dash`() {
        compactBatteryText(earbuds(0.8f, -1f, Float.NaN), "-") shouldBe "L 80%  ·  R -  ·  Case -"
    }

    @Test
    fun `all levels unknown`() {
        compactBatteryText(earbuds(-1f, -1f, -1f), "-") shouldBe "L -  ·  R -  ·  Case -"
    }

    @Test
    fun `zero is a known level`() {
        compactBatteryText(earbuds(0f, 1f, 0.5f), "-") shouldBe "L 0%  ·  R 100%  ·  Case 50%"
    }

    @Test
    fun `headset without case shows the single level`() {
        compactBatteryText(listOf(CompactBatteryLevel(null, 0.8f)), "-") shouldBe "80%"
        compactBatteryText(listOf(CompactBatteryLevel(null, -1f)), "-") shouldBe "-"
    }

    @Test
    fun `single device with a case keeps the case labelled`() {
        compactBatteryText(
            listOf(CompactBatteryLevel(null, 0.8f), CompactBatteryLevel("Case", 0.4f)),
            "-",
        ) shouldBe "80%  ·  Case 40%"
    }
}
