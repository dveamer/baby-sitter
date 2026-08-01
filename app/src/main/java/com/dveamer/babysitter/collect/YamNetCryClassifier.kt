package com.dveamer.babysitter.collect

import android.content.Context
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions

internal class YamNetCryClassifier(context: Context) : AutoCloseable {
    private val classifier = AudioClassifier.createFromOptions(
        context,
        AudioClassifier.AudioClassifierOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET_PATH)
                    .build()
            )
            .setRunningMode(RunningMode.AUDIO_CLIPS)
            .setCategoryAllowlist(YAMNET_DISTRESS_CATEGORIES.map { it.name })
            .setMaxResults(YAMNET_DISTRESS_CATEGORIES.size)
            .build()
    )
    private val audioData = AudioData.create(
        AudioData.AudioDataFormat.builder()
            .setNumOfChannels(CHANNEL_COUNT)
            .setSampleRate(CollectAudioConfig.SAMPLING_RATE.toFloat())
            .build(),
        INPUT_SAMPLE_COUNT
    )

    private var receivedSampleCount = 0L
    private var samplesSinceLastClassification = 0
    private var hasClassified = false

    fun accept(
        samples: ShortArray,
        offset: Int,
        size: Int,
        capturedAtMs: Long
    ): CollectCrySnapshot? {
        if (size <= 0) return null

        audioData.load(samples, offset, size)
        receivedSampleCount += size
        samplesSinceLastClassification += size

        if (receivedSampleCount < INPUT_SAMPLE_COUNT) return null
        if (hasClassified && samplesSinceLastClassification < HOP_SAMPLE_COUNT) return null

        samplesSinceLastClassification = if (hasClassified) {
            (samplesSinceLastClassification - HOP_SAMPLE_COUNT).coerceAtLeast(0)
        } else {
            0
        }
        hasClassified = true

        val distressScore = classifier.classify(audioData)
            .classificationResults()
            .asSequence()
            .flatMap { it.classifications().asSequence() }
            .flatMap { it.categories().asSequence() }
            .filter { category ->
                isYamNetDistressCategory(
                    index = category.index(),
                    name = category.categoryName()
                )
            }
            .maxOfOrNull { it.score() }
            ?: 0f

        return CollectCrySnapshot(
            score = distressScore.coerceIn(0f, 1f),
            capturedAtMs = capturedAtMs
        )
    }

    override fun close() {
        classifier.close()
    }

    private companion object {
        const val MODEL_ASSET_PATH = "yamnet.tflite"
        const val CHANNEL_COUNT = 1
        const val INPUT_SAMPLE_COUNT = 15_600
        const val HOP_SAMPLE_COUNT = INPUT_SAMPLE_COUNT / 2
    }
}

internal data class YamNetDistressCategory(
    val index: Int,
    val name: String
)

internal val YAMNET_DISTRESS_CATEGORIES = listOf(
    YamNetDistressCategory(index = 19, name = "Crying, sobbing"),
    YamNetDistressCategory(index = 20, name = "Baby cry, infant cry"),
    YamNetDistressCategory(index = 21, name = "Whimper")
)

internal fun isYamNetDistressCategory(index: Int, name: String): Boolean {
    return YAMNET_DISTRESS_CATEGORIES.any { category ->
        category.index == index || category.name == name
    }
}
