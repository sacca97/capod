package eu.darken.capod.pods.core.apple.ble

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.BaseBlePodsTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ModelAdvertCodesTest : BaseBlePodsTest() {

    private val fakes = setOf(
        PodModel.FAKE_AIRPODS_GEN1,
        PodModel.FAKE_AIRPODS_GEN2,
        PodModel.FAKE_AIRPODS_GEN3,
        PodModel.FAKE_AIRPODS_PRO,
        PodModel.FAKE_AIRPODS_PRO2,
    )

    private fun advertHex(model: PodModel, code: ModelAdvertCode): String {
        // Official devices advertise 25 payload bytes, the fake clones 19.
        val length = if (model in fakes) 19 else 25
        val bytes = ByteArray(2 + length)
        bytes[0] = 0x07
        bytes[1] = length.toByte()
        bytes[2] = 0x01
        bytes[3] = (code.code.toInt() shr 8).toByte()
        bytes[4] = code.code.toByte()
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    @Test
    fun `unknown has no code`() {
        ModelAdvertCodes.codeFor(PodModel.UNKNOWN) shouldBe null
    }

    @Test
    fun `mapped code at the advertised offset resolves to the same model`() = runTest {
        var checked = 0
        PodModel.entries.forEach { model ->
            val code = ModelAdvertCodes.codeFor(model) ?: return@forEach
            create<BlePodSnapshot>(advertHex(model, code)) {
                this.model shouldBe model
            }
            checked++
        }
        checked shouldBe 35
    }

    @Test
    fun `non-fake codes are unique per model`() {
        val real = PodModel.entries.filter { it !in fakes }.mapNotNull { m -> ModelAdvertCodes.codeFor(m)?.let { m to it } }
        real.groupBy { it.second }.filterValues { it.size > 1 } shouldBe emptyMap()
    }
}
