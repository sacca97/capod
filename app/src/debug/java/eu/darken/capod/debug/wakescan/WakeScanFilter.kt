package eu.darken.capod.debug.wakescan

import android.bluetooth.le.ScanFilter
import eu.darken.capod.pods.core.apple.ble.ModelAdvertCodes
import eu.darken.capod.pods.core.apple.ble.protocol.ContinuityProtocol
import eu.darken.capod.profiles.core.DeviceProfile

/**
 * Debug-only wake scan prototype: builds proximity pairing filters narrowed to the models of the configured profiles.
 *
 * Advert layout after the company id: `07 <len> 01 <model hi> <model lo> <status> <pods batt> <flags|case batt>
 * <lid> <color> <suffix> <16 encrypted>`. Type and model bytes must match, length (varies on clones) and prefix are
 * ignored.
 *
 * Models with a case additionally only match lid-OPEN adverts: status bit 6 (this pod in case) or bit 2 (both pods in
 * case) must be set, because only then the lid bit is trustworthy, and lid bit 3 (offset 8) must be clear (0 = open).
 * Same semantics as `DualApplePods.caseLidState`. Models without a case keep the model-only filter.
 */
object WakeScanFilter {
    // Type, length, prefix, model hi, model lo. Data and mask must have the same length.
    private const val DATA_LENGTH = 5
    private const val TYPE_PROXIMITY_PAIRING = 0x07

    // Covers offsets 0..8 so status (5) and lid (8) can be matched.
    private const val LID_DATA_LENGTH = 9
    private const val STATUS_THIS_POD_IN_CASE = 0x40
    private const val STATUS_BOTH_PODS_IN_CASE = 0x04
    private const val LID_CLOSED_BIT = 0x08

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

    /** Model code, status bit [statusBit] set and lid bit clear: a lid-open advert from a pod broadcasting in the case. */
    fun forCaseModelLidOpen(code: Int, codeMask: Int, statusBit: Int): Spec {
        val base = forModel(code, codeMask)
        return Spec(
            data = base.data.copyOf(LID_DATA_LENGTH).apply {
                this[5] = statusBit.toByte()
                this[8] = 0x00
            },
            mask = base.mask.copyOf(LID_DATA_LENGTH).apply {
                this[5] = statusBit.toByte()
                this[8] = LID_CLOSED_BIT.toByte()
            },
        )
    }

    /**
     * Two specs per distinct case model code (OR'd), one model-only spec per distinct caseless model code.
     * A profile without a code (e.g. UNKNOWN) degrades to the type-only filter.
     */
    fun specs(profiles: List<DeviceProfile>): Specs {
        val specs = LinkedHashSet<Spec>()
        var fallback = false
        profiles.forEach { profile ->
            val code = ModelAdvertCodes.codeFor(profile.model)
            if (code == null) {
                fallback = true
            } else {
                if (profile.model.features.hasCase) {
                    specs.add(forCaseModelLidOpen(code.code.toInt(), code.mask.toInt(), STATUS_THIS_POD_IN_CASE))
                    specs.add(forCaseModelLidOpen(code.code.toInt(), code.mask.toInt(), STATUS_BOTH_PODS_IN_CASE))
                } else {
                    specs.add(forModel(code.code.toInt(), code.mask.toInt()))
                }
            }
        }
        if (fallback) specs.add(typeOnly())
        return Specs(specs, fallback)
    }

    fun build(spec: Spec): ScanFilter = ScanFilter.Builder()
        .setManufacturerData(ContinuityProtocol.APPLE_COMPANY_IDENTIFIER, spec.data, spec.mask)
        .build()
}
