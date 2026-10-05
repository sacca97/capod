package eu.darken.capod.profiles.ui.add

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class AddDeviceViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val deviceProfilesRepo: DeviceProfilesRepo,
    private val bluetoothManager: BluetoothManager2,
) : ViewModel4(dispatcherProvider) {

    private val selectedModel = MutableStateFlow<PodModel?>(null)

    val closeEvents = SingleEventFlow<Unit>()

    data class PairedDevice(
        val device: BluetoothDevice2,
        val claimedByProfile: String?,
    )

    private val pairedDevices = combine(
        // Re-queried on every bond change, so a device paired meanwhile shows up without reopening.
        bluetoothManager.bondedDeviceAddresses.flatMapLatest {
            bluetoothManager.bondedDevices().catch { emit(emptySet()) }
        },
        deviceProfilesRepo.profiles,
    ) { bonded, profiles ->
        val claimed = profiles
            .mapNotNull { profile -> profile.address?.let { it.uppercase() to profile.label } }
            .toMap()
        bonded
            .map { PairedDevice(device = it, claimedByProfile = claimed[it.address.uppercase()]) }
            .sortedWith(compareBy({ it.claimedByProfile != null }, { it.device.name ?: "" }, { it.device.address }))
    }

    val state = combine(selectedModel, pairedDevices) { model, devices ->
        State(
            models = PodModel.entries.filter { it != PodModel.UNKNOWN },
            selectedModel = model,
            pairedDevices = devices,
        )
    }.asLiveState()

    data class State(
        val models: List<PodModel>,
        val selectedModel: PodModel?,
        val pairedDevices: List<PairedDevice>,
    )

    fun selectModel(model: PodModel) {
        log(TAG, INFO) { "selectModel($model)" }
        selectedModel.value = model
    }

    fun backToModels() {
        selectedModel.value = null
    }

    fun addDevice(device: BluetoothDevice2) = launch {
        val model = selectedModel.value ?: return@launch
        log(TAG, INFO) { "addDevice(model=$model, address=${device.address})" }
        try {
            deviceProfilesRepo.addProfile(
                AppleDeviceProfile(
                    label = device.name?.takeIf { it.isNotBlank() } ?: model.label,
                    model = model,
                    address = device.address,
                )
            )
            closeEvents.tryEmit(Unit)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to add device: $e" }
            errorEvents.emitBlocking(e)
        }
    }

    /** For pods whose IRK and encryption key have to be typed in, the full editor is still available. */
    fun enterKeysManually() {
        val model = selectedModel.value ?: return
        log(TAG, INFO) { "enterKeysManually(model=$model)" }
        navTo(Nav.Main.DeviceProfileCreation(presetModel = model.name))
        closeEvents.tryEmit(Unit)
    }

    companion object {
        private val TAG = logTag("Profiles", "AddDevice")
    }
}
