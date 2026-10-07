package eu.darken.capod.reaction.core.autoconnect

import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.bluetooth.NudgeAvailability
import eu.darken.capod.common.bluetooth.NudgeCapabilityStore
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.hasApiLevel
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
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests audio for pods that report themselves worn over a live AAP session while they are not
 * audio-connected. The other conditions are handled when the pods open their own link, see
 * `BluetoothEventReceiver`.
 */
@Singleton
class AutoConnect @Inject constructor(
    private val bluetoothManager: BluetoothManager2,
    private val aapManager: AapConnectionManager,
    private val profilesRepo: DeviceProfilesRepo,
    private val nudgeCapabilityStore: NudgeCapabilityStore,
) {

    fun monitor(): Flow<Unit> = combine(
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

    companion object {
        private val TAG = logTag("Reaction", "AutoConnect")
    }
}
