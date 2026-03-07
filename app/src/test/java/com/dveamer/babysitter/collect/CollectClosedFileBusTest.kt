package com.dveamer.babysitter.collect

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CollectClosedFileBusTest {

    @Test
    fun `type별 latest 유지`() {
        CollectClosedFileBus.clear()
        val audio = CollectClosedFileMeta(
            type = CollectFileType.AUDIO,
            file = File("/tmp/a.m4a"),
            startMs = 1_000L,
            closedAtMs = 2_000L
        )
        val video = CollectClosedFileMeta(
            type = CollectFileType.VIDEO,
            file = File("/tmp/v.mp4"),
            startMs = 3_000L,
            closedAtMs = 4_000L
        )

        CollectClosedFileBus.publish(audio)
        CollectClosedFileBus.publish(video)

        assertEquals(video, CollectClosedFileBus.latest())
        assertEquals(video, CollectClosedFileBus.latest(CollectFileType.VIDEO))
        assertEquals(audio, CollectClosedFileBus.latest(CollectFileType.AUDIO))
    }

    @Test
    fun `clear하면 모두 null`() {
        CollectClosedFileBus.clear()
        assertNull(CollectClosedFileBus.latest())
        assertNull(CollectClosedFileBus.latest(CollectFileType.VIDEO))
        assertNull(CollectClosedFileBus.latest(CollectFileType.AUDIO))

        CollectClosedFileBus.publish(
            CollectClosedFileMeta(
                type = CollectFileType.VIDEO,
                file = File("/tmp/v.mp4"),
                startMs = 1L,
                closedAtMs = 2L
            )
        )
        assertNotNull(CollectClosedFileBus.latest())

        CollectClosedFileBus.clear()
        assertNull(CollectClosedFileBus.latest())
    }
}
