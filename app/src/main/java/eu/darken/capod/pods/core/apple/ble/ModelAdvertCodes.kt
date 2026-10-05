package eu.darken.capod.pods.core.apple.ble

import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.airpods.*
import eu.darken.capod.pods.core.apple.ble.devices.beats.*
import eu.darken.capod.pods.core.apple.ble.devices.misc.*

/**
 * Model code of a proximity pairing advert, as the two bytes following prefix `0x01`,
 * i.e. advert bytes `07 <len> 01 <code hi> <code lo>`. Mirrors the `getModelInfo()` checks of the device classes.
 * [mask] is 0xFFFF for an exact match, Beats Studio 3 only checks the high byte.
 */
data class ModelAdvertCode(val code: UShort, val mask: UShort = 0xFFFFu)

object ModelAdvertCodes {

    private val codes: Map<PodModel, ModelAdvertCode> = mapOf(
        PodModel.AIRPODS_GEN1 to ModelAdvertCode(AirPodsGen1.DEVICE_CODE),
        PodModel.AIRPODS_GEN2 to ModelAdvertCode(AirPodsGen2.DEVICE_CODE),
        PodModel.AIRPODS_GEN3 to ModelAdvertCode(AirPodsGen3.DEVICE_CODE),
        PodModel.AIRPODS_GEN4_ANC to ModelAdvertCode(AirPodsGen4Anc.DEVICE_CODE),
        PodModel.AIRPODS_GEN4 to ModelAdvertCode(AirPodsGen4.DEVICE_CODE),
        PodModel.AIRPODS_GEN5 to ModelAdvertCode(AirPodsGen5.DEVICE_CODE),
        PodModel.AIRPODS_GEN5_WIRELESS to ModelAdvertCode(AirPodsGen5Wireless.DEVICE_CODE),
        PodModel.AIRPODS_MAX2 to ModelAdvertCode(AirPodsMax2.DEVICE_CODE),
        PodModel.AIRPODS_MAX to ModelAdvertCode(AirPodsMax.DEVICE_CODE),
        PodModel.AIRPODS_MAX_USBC to ModelAdvertCode(AirPodsMaxUsbc.DEVICE_CODE),
        PodModel.AIRPODS_PRO2 to ModelAdvertCode(AirPodsPro2.DEVICE_CODE),
        PodModel.AIRPODS_PRO2_USBC to ModelAdvertCode(AirPodsPro2Usbc.DEVICE_CODE),
        PodModel.AIRPODS_PRO3 to ModelAdvertCode(AirPodsPro3.DEVICE_CODE),
        PodModel.AIRPODS_PRO to ModelAdvertCode(AirPodsPro.DEVICE_CODE),
        PodModel.BEATS_360 to ModelAdvertCode(Beats360.DEVICE_CODE),
        PodModel.BEATS_FIT_PRO to ModelAdvertCode(BeatsFitPro.DEVICE_CODE),
        PodModel.BEATS_FLEX to ModelAdvertCode(BeatsFlex.DEVICE_CODE),
        PodModel.BEATS_SOLO_3 to ModelAdvertCode(BeatsSolo3.DEVICE_CODE),
        PodModel.BEATS_SOLO_4 to ModelAdvertCode(BeatsSolo4.DEVICE_CODE),
        PodModel.BEATS_SOLO_BUDS to ModelAdvertCode(BeatsSoloBuds.DEVICE_CODE),
        PodModel.BEATS_SOLO_PRO to ModelAdvertCode(BeatsSoloPro.DEVICE_CODE),
        PodModel.BEATS_STUDIO_3 to ModelAdvertCode(code = ((BeatsStudio3.DEVICE_CODE_DIRTY.toInt()) shl 8).toUShort(), mask = 0xFF00u),
        PodModel.BEATS_STUDIO_BUDS to ModelAdvertCode(BeatsStudioBuds.DEVICE_CODE),
        PodModel.BEATS_STUDIO_BUDS_PLUS to ModelAdvertCode(BeatsStudioBudsPlus.DEVICE_CODE),
        PodModel.BEATS_STUDIO_PRO to ModelAdvertCode(BeatsStudioPro.DEVICE_CODE),
        PodModel.BEATS_X to ModelAdvertCode(BeatsX.DEVICE_CODE),
        PodModel.POWERBEATS_3 to ModelAdvertCode(PowerBeats3.DEVICE_CODE),
        PodModel.POWERBEATS_4 to ModelAdvertCode(PowerBeats4.DEVICE_CODE),
        PodModel.POWERBEATS_PRO2 to ModelAdvertCode(PowerBeatsPro2.DEVICE_CODE),
        PodModel.POWERBEATS_PRO to ModelAdvertCode(PowerBeatsPro.DEVICE_CODE),
        PodModel.FAKE_AIRPODS_GEN1 to ModelAdvertCode(FakeAirPodsGen1.DEVICE_CODE),
        PodModel.FAKE_AIRPODS_GEN2 to ModelAdvertCode(FakeAirPodsGen2.DEVICE_CODE),
        PodModel.FAKE_AIRPODS_GEN3 to ModelAdvertCode(FakeAirPodsGen3.DEVICE_CODE),
        PodModel.FAKE_AIRPODS_PRO2 to ModelAdvertCode(FakeAirPodsPro2.DEVICE_CODE),
        PodModel.FAKE_AIRPODS_PRO to ModelAdvertCode(FakeAirPodsPro.DEVICE_CODE),
    )

    /** Null for [PodModel.UNKNOWN] and models without a known code. */
    fun codeFor(model: PodModel): ModelAdvertCode? = codes[model]
}
