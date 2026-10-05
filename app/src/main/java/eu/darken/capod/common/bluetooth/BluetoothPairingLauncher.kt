package eu.darken.capod.common.bluetooth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag

private val TAG = logTag("Bluetooth", "PairingLauncher")

// Not a public Settings constant. AOSP-based Settings apps open straight into scanning for new devices.
private const val ACTION_BLUETOOTH_PAIRING = "android.settings.BLUETOOTH_PAIRING_SETTINGS"

/** Opens the system's "pair new device" screen, or plain Bluetooth settings where that is unavailable. */
fun Context.openBluetoothPairing() {
    val intents = listOf(
        Intent(ACTION_BLUETOOTH_PAIRING),
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
    )

    for (intent in intents) {
        try {
            startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            log(TAG, WARN) { "${intent.action} not handled: $e" }
        } catch (e: SecurityException) {
            // Some devices require BT permission to open BT settings
            log(TAG, WARN) { "${intent.action} denied: $e" }
        }
    }
}
