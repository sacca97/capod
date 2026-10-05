package eu.darken.capod.debug.wakescan

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.bluetooth.redactedForLogs
import eu.darken.capod.common.hasApiLevel
import eu.darken.capod.common.notifications.PendingIntentCompat
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.startServiceCompat
import eu.darken.capod.monitor.core.worker.MonitorService
import eu.darken.capod.pods.core.apple.ble.protocol.RPAChecker
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.currentProfiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Debug-only prototype: can a PendingIntent BLE scan wake a killed process, and may that wake
 * start the monitor foreground service? Logging only, tag [TAG]. Not wired into the app.
 *
 * Arm/disarm via `adb shell am broadcast -a eu.darken.capod.debug.ARM_WAKE_SCAN -n <component>`.
 */
@AndroidEntryPoint
class BleWakeScanReceiver : BroadcastReceiver() {

    @Inject lateinit var profilesRepo: DeviceProfilesRepo
    @Inject lateinit var rpaChecker: RPAChecker

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        // Profiles and the IRK check suspend, so work off the main thread. finish() always runs in the finally.
        scope.launch {
            try {
                handle(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "onReceive failed: ${e.javaClass.name}: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        run {
            when (intent.action) {
                ACTION_ARM -> arm(context)
                ACTION_DISARM -> disarm(context)
                ACTION_STOP_MONITOR -> {
                    // Lets a test kill the process with no service running, so a restart can only come from the scan.
                    val stopped = context.stopService(MonitorService.intent(context))
                    Log.w(TAG, "monitor service stop requested, wasRunning=$stopped")
                }
                ACTION_DELIVER -> deliver(context, intent)
                else -> Log.w(TAG, "unknown action ${intent.action}")
            }
        }
    }

    private suspend fun profiles(): List<DeviceProfile>? = withTimeoutOrNull(PROFILES_TIMEOUT_MS) {
        profilesRepo.currentProfiles()
    }

    @SuppressLint("MissingPermission")
    private suspend fun arm(context: Context) {
        if (!hasScanPermission(context)) return
        val scanner = scanner(context) ?: return
        val profiles = profiles()
        if (profiles.isNullOrEmpty()) {
            Log.w(TAG, "WAKESCAN not armed: no profiles")
            return
        }
        val specs = WakeScanFilter.specs(profiles)
        val filters = specs.specs.map { WakeScanFilter.build(it) }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        // Replace a scan from an earlier ARM, its filters may be stale.
        scanner.stopScan(createPendingIntent(context))
        val rc = scanner.startScan(filters, settings, createPendingIntent(context))
        Log.w(
            TAG,
            "armed rc=$rc (0 = success) filters=${filters.size} fallbackTypeOnly=${specs.fallback} " +
                "models=${profiles.map { it.model.name }.distinct()}"
        )
    }

    @SuppressLint("MissingPermission")
    private fun disarm(context: Context) {
        if (!hasScanPermission(context)) return
        val scanner = scanner(context) ?: return
        scanner.stopScan(createPendingIntent(context))
        Log.w(TAG, "disarmed")
    }

    @Suppress("DEPRECATION")
    private suspend fun deliver(context: Context, intent: Intent) {
        val processAgeMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val first = firstDelivery
        firstDelivery = false
        val errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
        val callbackType = intent.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, -1)
        val results = intent.getParcelableArrayListExtra<ScanResult>(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)

        // processAge is the reliable cold signal; firstInProcess only shows no earlier delivery was seen here.
        Log.w(
            TAG,
            "delivery cold=$first processAgeMs=$processAgeMs firstInProcess=$first " +
                "callbackType=$callbackType errorCode=$errorCode count=${results?.size ?: 0}"
        )
        results?.forEach { result ->
            val apple = result.scanRecord?.getManufacturerSpecificData(APPLE_COMPANY_ID)
            val hex = apple?.take(8)?.joinToString(" ") { "%02X".format(it) } ?: "none"
            Log.w(TAG, "result addr=${result.device.address.redactedForLogs()} rssi=${result.rssi} apple8=[$hex]")
        }

        if (results.isNullOrEmpty()) {
            Log.w(TAG, "WAKESCAN ignored: no IRK match (results=0)")
            return
        }

        val appleProfiles = profiles().orEmpty().filterIsInstance<AppleDeviceProfile>()
        val unverifiable = appleProfiles.count { it.identityKey == null }
        if (unverifiable > 0) Log.w(TAG, "WAKESCAN unverifiable profiles=$unverifiable")
        val keyed = appleProfiles.filter { it.identityKey != null }

        val matched = results.firstNotNullOfOrNull { result -> authenticate(result.device.address, keyed) }
        if (matched == null) {
            Log.w(TAG, "WAKESCAN ignored: no IRK match (results=${results.size})")
            Log.w(TAG, "WAKESCAN decision=IGNORE matched=none")
            return
        }
        Log.w(TAG, "WAKESCAN decision=START matched=${matched.label}")

        if (!synchronized(startThrottle) { startThrottle.tryAcquire() }) {
            Log.w(TAG, "WAKESCAN fgs-start throttled")
            return
        }
        try {
            context.startServiceCompat(MonitorService.intent(context, false))
            Log.w(TAG, "fgs-start OK")
        } catch (e: Exception) {
            Log.w(TAG, "fgs-start FAILED ${e.javaClass.name}: ${e.message}")
        }
    }

    /** Same ownership check as AppleFactory.create, first profile whose IRK resolves [address] wins. */
    private fun authenticate(address: String, keyed: List<AppleDeviceProfile>): AppleDeviceProfile? {
        synchronized(checkedAddresses) { checkedAddresses.get(address) }?.let { return it.value }
        val match = keyed.firstOrNull { profile ->
            val key = profile.identityKey ?: return@firstOrNull false
            rpaChecker.resolve(address, key) != null
        }
        synchronized(checkedAddresses) { checkedAddresses.put(address, match) }
        return match
    }

    private fun hasScanPermission(context: Context): Boolean {
        // BLUETOOTH_SCAN only exists on Android 12+, older versions use legacy BLUETOOTH + location.
        if (!hasApiLevel(31)) return true
        return Permission.BLUETOOTH_SCAN.isGranted(context).also {
            if (!it) Log.w(TAG, "BLUETOOTH_SCAN not granted, cannot scan")
        }
    }

    private fun scanner(context: Context): BluetoothLeScanner? {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter missing or disabled")
            return null
        }
        return adapter.bluetoothLeScanner ?: run {
            Log.w(TAG, "bluetoothLeScanner is null")
            null
        }
    }

    companion object {
        const val TAG = "WAKESCAN"
        const val ACTION_ARM = "eu.darken.capod.debug.ARM_WAKE_SCAN"
        const val ACTION_DISARM = "eu.darken.capod.debug.DISARM_WAKE_SCAN"
        const val ACTION_STOP_MONITOR = "eu.darken.capod.debug.STOP_MONITOR"
        const val ACTION_DELIVER = "eu.darken.capod.debug.DELIVER_WAKE_SCAN"
        private const val REQUEST_CODE = 271
        private const val APPLE_COMPANY_ID = 0x004C
        private const val PROFILES_TIMEOUT_MS = 5_000L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // Per process: addresses checked in the last 10 s (with their outcome) and the service start throttle.
        private val checkedAddresses = RecentCache<AppleDeviceProfile?>()
        private val startThrottle = Throttle()

        // Initialised at class load, i.e. in the delivery that created the process if it was the first broadcast.
        @Volatile private var firstDelivery = true

        // Start and stop must use the identical intent, request code and flags, or stopScan won't match.
        private fun createPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, BleWakeScanReceiver::class.java).setAction(ACTION_DELIVER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentCompat.FLAG_MUTABLE,
        )
    }
}
