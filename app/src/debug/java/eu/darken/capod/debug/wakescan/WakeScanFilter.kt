package eu.darken.capod.debug.wakescan

import android.bluetooth.le.ScanFilter
import eu.darken.capod.pods.core.apple.ble.protocol.ContinuityProtocol

/** Debug-only wake scan prototype: builds the proximity pairing filter with an exact type match. */
object WakeScanFilter {
    private const val DATA_LENGTH = 27
    private const val TYPE_PROXIMITY_PAIRING = 0x07
    private const val PAIRING_MESSAGE_LENGTH = 25

    fun data(): ByteArray = ByteArray(DATA_LENGTH).apply {
        this[0] = TYPE_PROXIMITY_PAIRING.toByte()
        this[1] = PAIRING_MESSAGE_LENGTH.toByte()
    }

    /** Byte 0 (message type) must match exactly. Byte 1 keeps the production mask of 1. */
    fun mask(): ByteArray = ByteArray(DATA_LENGTH).apply {
        this[0] = 0xFF.toByte()
        this[1] = 1
    }

    fun build(): ScanFilter = ScanFilter.Builder()
        .setManufacturerData(ContinuityProtocol.APPLE_COMPANY_IDENTIFIER, data(), mask())
        .build()
}
