package com.dveamer.babysitter.collect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectRecorderCoordinatorTest {

    private val coordinator = CollectRecorderCoordinator(ensureDirectories = {})

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
