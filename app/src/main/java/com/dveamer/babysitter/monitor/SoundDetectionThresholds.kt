package com.dveamer.babysitter.monitor

internal data class SoundDetectionThresholds(
    val amplitude: Double,
    val yamNetCryConfidence: Float
) {
    companion object {
        fun fromUserThreshold(value: Int): SoundDetectionThresholds {
            val normalized = value.coerceIn(MIN_USER_THRESHOLD, MAX_USER_THRESHOLD)
            return SoundDetectionThresholds(
                amplitude = (normalized * AMPLITUDE_SCALE)
                    .coerceIn(MIN_AMPLITUDE_THRESHOLD, MAX_AMPLITUDE_THRESHOLD),
                yamNetCryConfidence = normalized
                    .coerceAtMost(MAX_YAMNET_USER_THRESHOLD) /
                    MAX_YAMNET_USER_THRESHOLD.toFloat()
            )
        }

        private const val MIN_USER_THRESHOLD = 50
        private const val MAX_USER_THRESHOLD = 8_000
        private const val AMPLITUDE_SCALE = 0.75
        private const val MIN_AMPLITUDE_THRESHOLD = 120.0
        private const val MAX_AMPLITUDE_THRESHOLD = 2_500.0
        private const val MAX_YAMNET_USER_THRESHOLD = 1_000
    }
}
