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

    @Test
    fun `one filter per distinct model code`() {
        val result = WakeScanFilter.specs(
            listOf(profile(PodModel.AIRPODS_PRO2), profile(PodModel.AIRPODS_PRO2), profile(PodModel.AIRPODS_GEN3))
        )
        result.fallback shouldBe false
        result.specs shouldHaveSize 2
        val pro2 = result.specs.first()
        pro2.hex(pro2.data) shouldBe "07 00 00 14 20"
        pro2.hex(pro2.mask) shouldBe "FF 00 00 FF FF"
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
