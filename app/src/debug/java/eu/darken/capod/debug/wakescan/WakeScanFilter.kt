package eu.darken.capod.debug.wakescan

import android.bluetooth.le.ScanFilter
import eu.darken.capod.pods.core.apple.ble.ModelAdvertCodes
import eu.darken.capod.pods.core.apple.ble.protocol.ContinuityProtocol
import eu.darken.capod.profiles.core.DeviceProfile

/**
 * Debug-only wake scan prototype: builds proximity pairing filters narrowed to the models of the configured profiles.
 *
 * Advert layout after the company id: `07 <len> 01 <model hi> <model lo> ...`. Type and model bytes must match,
 * length (varies on clones) and prefix are ignored.
 */
object WakeScanFilter {
    // Type, length, prefix, model hi, model lo. Data and mask must have the same length.
    private const val DATA_LENGTH = 5
    private const val TYPE_PROXIMITY_PAIRING = 0x07

    /** Raw manufacturer data and mask of one filter. Pure, so it can be unit tested without the framework. */
    data class Spec(val data: ByteArray, val mask: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Spec && data.contentEquals(other.data) && mask.contentEquals(other.mask)

        override fun hashCode(): Int = 31 * data.contentHashCode() + mask.contentHashCode()
    }

    /** Result of [specs]: [fallback] is true if any profile had no known model code. */
    data class Specs(val specs: Set<Spec>, val fallback: Boolean)

    /** Type-only filter, matches every proximity pairing advert. */
    fun typeOnly(): Spec = Spec(
        data = ByteArray(DATA_LENGTH).apply { this[0] = TYPE_PROXIMITY_PAIRING.toByte() },
        mask = ByteArray(DATA_LENGTH).apply { this[0] = 0xFF.toByte() },
    )

    fun forModel(code: Int, codeMask: Int): Spec = Spec(
        data = typeOnly().data.apply {
            this[3] = (code shr 8).toByte()
            this[4] = code.toByte()
        },
        mask = typeOnly().mask.apply {
            this[3] = (codeMask shr 8).toByte()
            this[4] = codeMask.toByte()
        },
    )

    /** One spec per distinct model code. A profile without a code (e.g. UNKNOWN) degrades to the type-only filter. */
    fun specs(profiles: List<DeviceProfile>): Specs {
        val specs = LinkedHashSet<Spec>()
        var fallback = false
        profiles.forEach { profile ->
            val code = ModelAdvertCodes.codeFor(profile.model)
            if (code == null) {
                fallback = true
            } else {
                specs.add(forModel(code.code.toInt(), code.mask.toInt()))
            }
        }
        if (fallback) specs.add(typeOnly())
        return Specs(specs, fallback)
    }

    fun build(spec: Spec): ScanFilter = ScanFilter.Builder()
        .setManufacturerData(ContinuityProtocol.APPLE_COMPANY_IDENTIFIER, spec.data, spec.mask)
        .build()
}
