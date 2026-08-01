package com.dveamer.babysitter.collect

internal object CollectCameraProfile {
    const val VIDEO_WIDTH = 640
    const val VIDEO_HEIGHT = 480
    const val VIDEO_FRAME_RATE = 20
    const val VIDEO_BIT_RATE = 3_000_000

    // The web preview uses the same spatial resolution as the collect recording.
    const val PREVIEW_WIDTH = VIDEO_WIDTH
    const val PREVIEW_HEIGHT = VIDEO_HEIGHT
    const val PREVIEW_JPEG_QUALITY = 95

    // Motion stays on the existing lightweight grayscale representation.
    const val MOTION_FRAME_WIDTH = 80
    const val MOTION_FRAME_HEIGHT = 60
}
