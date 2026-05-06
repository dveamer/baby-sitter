package com.dveamer.babysitter.sleep

import android.content.pm.ServiceInfo
import com.dveamer.babysitter.collect.CollectInputPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepForegroundServiceTypeResolverTest {

    @Test
    fun resolvesNoTypesWhenCollectInputsAreDisabled() {
        val types = SleepForegroundServiceTypeResolver.resolve(
            CollectInputPolicy(
                cameraInputEnabled = false,
                audioInputEnabled = false
            )
        )

        assertEquals(0, types)
    }

    @Test
    fun resolvesCameraTypeOnlyWhenCameraInputIsEnabled() {
        val types = SleepForegroundServiceTypeResolver.resolve(
            CollectInputPolicy(
                cameraInputEnabled = true,
                audioInputEnabled = false
            )
        )

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA, types)
    }

    @Test
    fun resolvesMicrophoneTypeOnlyWhenAudioInputIsEnabled() {
        val types = SleepForegroundServiceTypeResolver.resolve(
            CollectInputPolicy(
                cameraInputEnabled = false,
                audioInputEnabled = true
            )
        )

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, types)
    }

    @Test
    fun resolvesBothSensitiveTypesWhenBothCollectInputsAreEnabled() {
        val types = SleepForegroundServiceTypeResolver.resolve(
            CollectInputPolicy(
                cameraInputEnabled = true,
                audioInputEnabled = true
            )
        )

        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            types
        )
    }
}
