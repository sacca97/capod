package eu.darken.capod.reaction.core.autoconnect

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.NudgeAvailability
import eu.darken.capod.common.bluetooth.NudgeCapabilityStore
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.hasApiLevel
import eu.darken.capod.monitor.core.DeviceMonitor
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests audio for pods that are worn while they are not audio-connected, for profiles set to
 * [AutoConnectCondition.IN_EAR]. Two triggers feed it:
 * - AAP: a live, READY session reports both pods (or either, in one-pod mode) in the ear.
 * - BLE: no AAP session exists yet (it needs the classic link), so an IRK-authenticated advert that
 *   reliably says "worn" is used instead, debounced and rate-limited, see [InEarBleDecision].
 *
 * The other conditions are handled when the pods open their own link, see `BluetoothEventReceiver`.
 */
@Singleton
class AutoConnect @Inject constructor(
    private val bluetoothManager: BluetoothManager2,
    private val aapManager: AapConnectionManager,
    private val profilesRepo: DeviceProfilesRepo,
    private val nudgeCapabilityStore: NudgeCapabilityStore,
    private val deviceMonitor: DeviceMonitor,
) {

    internal var nowMs: () -> Long = { System.currentTimeMillis() }

    fun monitor(): Flow<Unit> = merge(monitorAap(), monitorBle())

    private fun monitorBle(): Flow<Unit> {
        val streaks = mutableMapOf<String, Int>()
        val lastAttempt = mutableMapOf<String, Long>()
        return combine(deviceMonitor.devices, profilesRepo.profiles) { devices, profiles ->
            val qualifying = profiles.filterIsInstance<AppleDeviceProfile>().filter { profile ->
                val address = profile.address ?: return@filter false
                devices.any { it.profileId == profile.id && it.qualifiesForBleInEar(profile, address) }
            }
            val ids = qualifying.map { it.id }.toSet()
            streaks.keys.retainAll(ids)
            val now = nowMs()
            qualifying.mapNotNull { profile ->
                val address = profile.address ?: return@mapNotNull null
                val count = (streaks[profile.id] ?: 0) + 1
                streaks[profile.id] = count
                val last = lastAttempt[address]
                val decision = InEarBleDecision.decide(true, count, last?.let { now - it })
                if (decision != InEarBleDecision.Decision.Connect) return@mapNotNull null
                lastAttempt[address] = now
                profile.label to address
            }
        }
            .onEach { targets ->
                targets.forEach { (label, address) ->
                    log(TAG) { "In-ear (BLE) trigger for $label: connecting" }
                    connectAudio(address)
                }
            }
            .map { }
    }

    /**
     * BLE ear bits are unreliable for pods resting in the case (phantom "in ear"), so the advert is
     * only trusted when it is IRK-authenticated and no case bit is set at all: status bit 6 (the
     * broadcasting pod is in the case), bit 4 (one pod in case) and bit 2 (both pods in case).
     * This also means a one-pod-in-ear / other-in-case state never triggers, on purpose.
     */
    private fun PodDevice.qualifiesForBleInEar(profile: AppleDeviceProfile, address: String): Boolean {
        val reactions = profile.reactionConfig
        if (!reactions.autoConnect || reactions.autoConnectCondition != AutoConnectCondition.IN_EAR) return false
        if (isSystemConnected || hasAapEarDetection) return false
        val apple = ble as? ApplePods ?: return false
        if (!apple.meta.isIRKMatch) return false
        val dual = apple as? DualApplePods ?: return false
        if (dual.isThisPodInThecase || dual.isOnePodInCase || dual.areBothPodsInCase) return false
        return when {
            reactions.onePodMode -> dual.isEitherPodInEar
            else -> dual.isLeftPodInEar && dual.isRightPodInEar
        }
    }

    private fun monitorAap(): Flow<Unit> = combine(
        aapManager.allStates,
        profilesRepo.profiles,
        bluetoothManager.connectedDevices,
    ) { states, profiles, connected ->
        profiles.filterIsInstance<AppleDeviceProfile>()
            .filter { profile -> profile.wantsAudioWhenWorn(states[profile.address]) }
            .filter { profile -> connected.none { it.address.equals(profile.address, ignoreCase = true) } }
            .mapNotNull { it.address }
    }
        .distinctUntilChanged()
        .onEach { addresses -> addresses.forEach { connectAudio(it) } }
        .map { }

    private fun AppleDeviceProfile.wantsAudioWhenWorn(state: AapPodState?): Boolean {
        val reactions = reactionConfig
        if (!reactions.autoConnect || reactions.autoConnectCondition != AutoConnectCondition.IN_EAR) return false
        if (state?.connectionState != AapPodState.ConnectionState.READY) return false
        val ear = state.aapEarDetection ?: return false
        return when {
            reactions.onePodMode -> ear.isEitherPodInEar
            else -> ear.primaryPod == AapSetting.EarDetection.PodPlacement.IN_EAR &&
                ear.secondaryPod == AapSetting.EarDetection.PodPlacement.IN_EAR
        }
    }

    private suspend fun connectAudio(address: String) {
        if (hasApiLevel(37)) {
            if (!bluetoothManager.isCompanionAssociated(address)) {
                log(TAG, VERBOSE) { "Not connecting $address, no companion association" }
                return
            }
        } else if (nudgeCapabilityStore.availability.value == NudgeAvailability.BROKEN) {
            log(TAG, VERBOSE) { "Not connecting $address, the system connect method is known to be blocked" }
            return
        }
        val device = bluetoothManager.bondedDevices().first().firstOrNull { it.address == address }?.internal
        if (device == null) {
            log(TAG, VERBOSE) { "Not connecting $address, no bonded device" }
            return
        }
        val result = bluetoothManager.connectAudio(device)
        if (!hasApiLevel(37)) nudgeCapabilityStore.record(result)
        log(TAG) { "In-ear audio connection result=$result" }
    }

    /** Pure debounce and rate-limit rule for the BLE trigger. */
    internal object InEarBleDecision {
        const val MIN_CONSECUTIVE = 2
        const val MIN_RETRY_INTERVAL_MS = 20_000L

        sealed interface Decision {
            data object Connect : Decision
            data class Skip(val reason: String) : Decision
        }

        fun decide(qualifies: Boolean, consecutiveCount: Int, msSinceLastAttempt: Long?): Decision = when {
            !qualifies -> Decision.Skip("not qualifying")
            consecutiveCount < MIN_CONSECUTIVE -> Decision.Skip("debouncing")
            msSinceLastAttempt != null && msSinceLastAttempt < MIN_RETRY_INTERVAL_MS -> Decision.Skip("rate limited")
            else -> Decision.Connect
        }
    }

    companion object {
        private val TAG = logTag("Reaction", "AutoConnect")
    }
}
