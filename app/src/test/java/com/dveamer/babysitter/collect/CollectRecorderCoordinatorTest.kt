package com.dveamer.babysitter.collect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectRecorderCoordinatorTest {

    private val coordinator = CollectRecorderCoordinator(ensureDirectories = {})

    @Test
    fun `sleep off does not enable camera or audio without preview demand`() {
        val policy = coordinator.updateInputs(
            sleepEnabled = false,
            cameraMonitoringEnabled = true,
            webCameraEnabled = true,
            soundMonitoringEnabled = true
        )

        assertFalse(policy.cameraInputEnabled)
        assertFalse(policy.audioInputEnabled)
        assertFalse(policy.motionAnalysisEnabled)
        assertFalse(policy.webPreviewAllowed)
    }

    @Test
    fun `sleep off enables camera only while preview subscriber is active`() {
        coordinator.updateInputs(
            sleepEnabled = false,
            cameraMonitoringEnabled = true,
            webCameraEnabled = true,
            soundMonitoringEnabled = true
        )

        val activeSnapshot = coordinator.onWebPreviewSubscriberConnected()
        val activePolicy = coordinator.currentPolicy()

        assertTrue(activeSnapshot.active)
        assertTrue(activePolicy.cameraInputEnabled)
        assertFalse(activePolicy.audioInputEnabled)
        assertFalse(activePolicy.motionAnalysisEnabled)
        assertTrue(activePolicy.webPreviewAllowed)

        coordinator.onWebPreviewSubscriberDisconnected()
        val inactivePolicy = coordinator.currentPolicy()

        assertFalse(inactivePolicy.cameraInputEnabled)
        assertFalse(inactivePolicy.audioInputEnabled)
        assertFalse(inactivePolicy.motionAnalysisEnabled)
        assertFalse(inactivePolicy.webPreviewAllowed)
    }

    @Test
    fun `preview demand callback fires only on active state changes`() {
        var callbackCount = 0
        val coordinator = CollectRecorderCoordinator(
            ensureDirectories = {},
            onWebPreviewDemandChanged = { callbackCount += 1 }
        )

        coordinator.updateInputs(
            sleepEnabled = true,
            cameraMonitoringEnabled = false,
            webCameraEnabled = true,
            soundMonitoringEnabled = false
        )

        coordinator.onWebPreviewSubscriberConnected()
        coordinator.onWebPreviewSubscriberConnected()
        coordinator.onWebPreviewSubscriberDisconnected()
        coordinator.onWebPreviewSubscriberDisconnected()

        assertEquals(2, callbackCount)
    }

    @Test
    fun `preview demand stays active until last subscriber disconnects`() {
        val first = coordinator.onWebPreviewSubscriberConnected()
        val second = coordinator.onWebPreviewSubscriberConnected()

        assertTrue(first.active)
        assertEquals(2, second.subscriberCount)
        assertTrue(coordinator.isWebPreviewDemandActive())

        val afterFirstDisconnect = coordinator.onWebPreviewSubscriberDisconnected(
            disconnectedAtMs = 1_000L
        )
        assertEquals(1, afterFirstDisconnect.subscriberCount)
        assertTrue(afterFirstDisconnect.active)
        assertNull(afterFirstDisconnect.lastSubscriberDisconnectedAtMs)

        val afterLastDisconnect = coordinator.onWebPreviewSubscriberDisconnected(
            disconnectedAtMs = 2_000L
        )
        assertEquals(0, afterLastDisconnect.subscriberCount)
        assertFalse(afterLastDisconnect.active)
        assertEquals(2_000L, afterLastDisconnect.lastSubscriberDisconnectedAtMs)
        assertFalse(coordinator.isWebPreviewDemandActive())
    }

    @Test
    fun `disconnect underflow does not make subscriber count negative`() {
        val snapshot = coordinator.onWebPreviewSubscriberDisconnected(
            disconnectedAtMs = 3_000L
        )

        assertEquals(0, snapshot.subscriberCount)
        assertFalse(snapshot.active)
        assertNull(snapshot.lastSubscriberDisconnectedAtMs)
    }
}
