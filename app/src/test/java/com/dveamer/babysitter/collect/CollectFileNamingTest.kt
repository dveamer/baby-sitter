package com.dveamer.babysitter.collect

import java.io.File
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectFileNamingTest {

    @Test
    fun `파일명 생성과 파싱이 1분 단위로 일치`() {
        val zone = ZoneId.of("UTC")
        val epoch = 1_700_000_123_456L
        val floor = CollectFileNaming.minuteFloor(epoch)

        val videoName = CollectFileNaming.collectVideoFileName(epoch, zone)
        val audioName = CollectFileNaming.collectAudioFileName(epoch, zone)
        val memoryName = CollectFileNaming.memoryVideoFileName(epoch, zone)

        assertEquals(floor, CollectFileNaming.parseCollectVideoStartMs(File(videoName), zone))
        assertEquals(floor, CollectFileNaming.parseCollectAudioStartMs(File(audioName), zone))
        assertEquals(floor, CollectFileNaming.parseMemoryStartMs(File(memoryName), zone))
    }

    @Test
    fun `규칙 외 파일명은 null`() {
        assertNull(CollectFileNaming.parseCollectVideoStartMs(File("collect_invalid.mp4")))
        assertNull(CollectFileNaming.parseCollectAudioStartMs(File("collect_20250101_1200.mp3")))
        assertNull(CollectFileNaming.parseMemoryStartMs(File("memory_latest.mp4")))
    }
}
