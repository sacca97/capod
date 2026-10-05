package eu.darken.capod.debug.wakescan

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.profiles.core.AppleDeviceProfile
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class WakeScanFilterTest : BaseTest() {

    private fun profile(model: PodModel) = AppleDeviceProfile(label = model.name, model = model)

    private fun WakeScanFilter.Spec.hex(array: ByteArray) = array.joinToString(" ") { "%02X".format(it) }

    private fun hexes(spec: WakeScanFilter.Spec) = spec.hex(spec.data) to spec.hex(spec.mask)

    @Test
    fun `case model gets two lid open filters`() {
        val result = WakeScanFilter.specs(listOf(profile(PodModel.AIRPODS_PRO3), profile(PodModel.AIRPODS_PRO3)))
        result.fallback shouldBe false
        result.specs.map { hexes(it) } shouldBe listOf(
            "07 00 00 27 20 40 00 00 00" to "FF 00 00 FF FF 40 00 00 08",
            "07 00 00 27 20 04 00 00 00" to "FF 00 00 FF FF 04 00 00 08",
        )
    }

    @Test
    fun `distinct case models get two filters each`() {
        val result = WakeScanFilter.specs(listOf(profile(PodModel.AIRPODS_PRO2), profile(PodModel.AIRPODS_GEN3)))
        result.specs shouldHaveSize 4
    }

    @Test
    fun `caseless model keeps the model only filter`() {
        val spec = WakeScanFilter.specs(listOf(profile(PodModel.AIRPODS_MAX))).specs.single()
        spec.hex(spec.data) shouldBe "07 00 00 0A 20"
        spec.hex(spec.mask) shouldBe "FF 00 00 FF FF"
    }

    @Test
    fun `unknown model falls back to type only`() {
        val result = WakeScanFilter.specs(listOf(profile(PodModel.UNKNOWN)))
        result.fallback shouldBe true
        result.specs shouldBe setOf(WakeScanFilter.typeOnly())
        WakeScanFilter.typeOnly().let { it.hex(it.mask) } shouldBe "FF 00 00 00 00"
    }

    @Test
    fun `beats studio 3 only checks the high byte`() {
        val spec = WakeScanFilter.specs(listOf(profile(PodModel.BEATS_STUDIO_3))).specs.single()
        spec.hex(spec.data) shouldBe "07 00 00 09 00"
        spec.hex(spec.mask) shouldBe "FF 00 00 FF 00"
    }

    @Test
    fun `no profiles gives no specs`() {
        WakeScanFilter.specs(emptyList()).specs shouldHaveSize 0
    }
}
