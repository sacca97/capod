package eu.darken.capod.profiles.ui.add

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.openBluetoothPairing
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.error.ErrorEventHandler
import eu.darken.capod.common.navigation.NavigationEventHandler
import eu.darken.capod.pods.core.apple.PodModel

@Composable
fun AddDeviceDialogHost(
    onDismiss: () -> Unit,
    vm: AddDeviceViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    LaunchedEffect(vm.closeEvents) { vm.closeEvents.collect { onDismiss() } }

    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
    state?.let {
        AddDeviceDialog(
            state = it,
            onDismiss = onDismiss,
            onSelectModel = { model -> vm.selectModel(model) },
            onBack = { vm.backToModels() },
            onSelectDevice = { device -> vm.addDevice(device) },
            onPairNewDevice = { context.openBluetoothPairing() },
            onEnterKeysManually = { vm.enterKeysManually() },
        )
    }
}

@Composable
fun AddDeviceDialog(
    state: AddDeviceViewModel.State,
    onDismiss: () -> Unit,
    onSelectModel: (PodModel) -> Unit,
    onBack: () -> Unit,
    onSelectDevice: (BluetoothDevice2) -> Unit,
    onPairNewDevice: () -> Unit,
    onEnterKeysManually: () -> Unit,
) {
    val model = state.selectedModel
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (model == null) {
                    stringResource(R.string.profiles_add_device_model_title)
                } else {
                    stringResource(R.string.profiles_add_device_paired_title, model.label)
                },
            )
        },
        text = {
            if (model == null) {
                ModelList(models = state.models, onSelect = onSelectModel)
            } else {
                PairedDeviceList(
                    devices = state.pairedDevices,
                    onSelect = onSelectDevice,
                    onPairNewDevice = onPairNewDevice,
                )
            }
        },
        confirmButton = {
            if (model != null) {
                TextButton(onClick = onEnterKeysManually) {
                    Text(text = stringResource(R.string.profiles_add_device_enter_keys_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = if (model != null) onBack else onDismiss) {
                Text(text = stringResource(if (model != null) R.string.general_back_action else R.string.general_cancel_action))
            }
        },
    )
}

@Composable
private fun ModelList(
    models: List<PodModel>,
    onSelect: (PodModel) -> Unit,
) {
    LazyColumn {
        items(models, key = { it.name }) { model ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(model) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(model.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = model.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PairedDeviceList(
    devices: List<AddDeviceViewModel.PairedDevice>,
    onSelect: (BluetoothDevice2) -> Unit,
    onPairNewDevice: () -> Unit,
) {
    val unknownLabel = stringResource(R.string.pods_unknown_label)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (devices.isEmpty()) {
            Text(
                text = stringResource(R.string.profiles_add_device_paired_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(devices, key = { it.device.address }) { item ->
                val isClaimed = item.claimedByProfile != null
                val alpha = if (isClaimed) 0.38f else 1f
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isClaimed) Modifier else Modifier.clickable { onSelect(item.device) })
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = item.device.name ?: unknownLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    )
                    Text(
                        text = if (isClaimed) {
                            stringResource(R.string.profiles_paired_device_used_by, item.claimedByProfile!!)
                        } else {
                            item.device.address
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    )
                }
            }
        }
        TextButton(onClick = onPairNewDevice) {
            Text(text = stringResource(R.string.profiles_paired_device_pair_new))
        }
    }
}

@Preview2
@Composable
private fun AddDeviceDialogModelsPreview() = PreviewWrapper {
    AddDeviceDialog(
        state = AddDeviceViewModel.State(
            models = PodModel.entries.filter { it != PodModel.UNKNOWN },
            selectedModel = null,
            pairedDevices = emptyList(),
        ),
        onDismiss = {},
        onSelectModel = {},
        onBack = {},
        onSelectDevice = {},
        onPairNewDevice = {},
        onEnterKeysManually = {},
    )
}

@Preview2
@Composable
private fun AddDeviceDialogPairedEmptyPreview() = PreviewWrapper {
    AddDeviceDialog(
        state = AddDeviceViewModel.State(
            models = emptyList(),
            selectedModel = PodModel.AIRPODS_PRO2,
            pairedDevices = emptyList(),
        ),
        onDismiss = {},
        onSelectModel = {},
        onBack = {},
        onSelectDevice = {},
        onPairNewDevice = {},
        onEnterKeysManually = {},
    )
}
