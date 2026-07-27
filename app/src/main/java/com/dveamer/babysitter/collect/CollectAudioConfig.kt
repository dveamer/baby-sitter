package com.dveamer.babysitter.collect

internal object CollectAudioConfig {
    const val ENCODING_BIT_RATE = 96_000
    const val SAMPLING_RATE = 16_000
    const val AMPLITUDE_PUBLISH_INTERVAL_MS = 500L
    const val AMPLITUDE_STALE_TIMEOUT_MS = AMPLITUDE_PUBLISH_INTERVAL_MS * 4
    const val CRY_RESULT_STALE_TIMEOUT_MS = 2_000L
}
