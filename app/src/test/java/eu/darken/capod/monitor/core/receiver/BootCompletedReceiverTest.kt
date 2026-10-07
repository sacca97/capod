package eu.darken.capod.monitor.core.receiver

import eu.darken.capod.common.bluetooth.BluetoothDevice2
import eu.darken.capod.common.bluetooth.BluetoothManager2
import eu.darken.capod.monitor.core.worker.MonitorControl
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BootCompletedReceiverTest : BaseTest() {
    @Test
    fun `boot waits for Bluetooth events unless a configured device is connected`() = runTest {
        val address = "AA:BB:CC:DD:EE:FF"
        val profile = AppleDeviceProfile(label = "Pods", address = address)
        var connected = emptyList<BluetoothDevice2>()
        val control = mockk<MonitorControl>(relaxed = true)
        val receiver = BootCompletedReceiver().apply {
            monitorControl = control
            profilesRepo = mockk { every { profiles } answers { flowOf(listOf(profile)) } }
            bluetoothManager = mockk { every { connectedDevices } answers { flowOf(connected) } }
        }
        receiver.startMonitorIfNeeded()
        verify(exactly = 0) { control.startMonitor(any()) }
        // The system may report the address in a different case than the stored profile.
        connected = listOf(mockk { every { this@mockk.address } returns address.lowercase() })
        receiver.startMonitorIfNeeded()
        verify(exactly = 1) { control.startMonitor(false) }
        connected = emptyList()
        receiver.startMonitorIfNeeded()
        verify(exactly = 1) { control.startMonitor(false) }
    }
}
