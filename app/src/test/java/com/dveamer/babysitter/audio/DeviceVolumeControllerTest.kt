package com.dveamer.babysitter.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceVolumeControllerTest {

    @Test
    fun percentToLevel_clampsIntoMusicStreamRange() {
        assertEquals(0, DeviceVolumeMath.percentToLevel(-10, 15))
        assertEquals(0, DeviceVolumeMath.percentToLevel(0, 15))
        assertEquals(7, DeviceVolumeMath.percentToLevel(50, 15))
        assertEquals(15, DeviceVolumeMath.percentToLevel(100, 15))
        assertEquals(15, DeviceVolumeMath.percentToLevel(180, 15))
    }

    @Test
    fun levelToPercent_clampsIntoPercentRange() {
        assertEquals(0, DeviceVolumeMath.levelToPercent(0, 0))
        assertEquals(0, DeviceVolumeMath.levelToPercent(-1, 15))
        assertEquals(46, DeviceVolumeMath.levelToPercent(7, 15))
        assertEquals(100, DeviceVolumeMath.levelToPercent(15, 15))
        assertEquals(100, DeviceVolumeMath.levelToPercent(20, 15))
    }
}
