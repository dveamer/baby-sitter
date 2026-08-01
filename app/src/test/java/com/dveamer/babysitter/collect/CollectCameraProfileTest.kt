package com.dveamer.babysitter.collect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectCameraProfileTest {

    @Test
    fun `web preview keeps collect recording resolution`() {
        assertEquals(CollectCameraProfile.VIDEO_WIDTH, CollectCameraProfile.PREVIEW_WIDTH)
        assertEquals(CollectCameraProfile.VIDEO_HEIGHT, CollectCameraProfile.PREVIEW_HEIGHT)
    }

    @Test
    fun `motion analysis remains lower resolution than preview`() {
        assertTrue(CollectCameraProfile.MOTION_FRAME_WIDTH < CollectCameraProfile.PREVIEW_WIDTH)
        assertTrue(CollectCameraProfile.MOTION_FRAME_HEIGHT < CollectCameraProfile.PREVIEW_HEIGHT)
    }

    @Test
    fun `collect recording keeps requested quality profile`() {
        assertEquals(640, CollectCameraProfile.VIDEO_WIDTH)
        assertEquals(480, CollectCameraProfile.VIDEO_HEIGHT)
        assertEquals(20, CollectCameraProfile.VIDEO_FRAME_RATE)
        assertEquals(3_000_000, CollectCameraProfile.VIDEO_BIT_RATE)
    }
}
