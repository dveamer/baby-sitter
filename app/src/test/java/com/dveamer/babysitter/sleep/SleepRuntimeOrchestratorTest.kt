package com.dveamer.babysitter.sleep

import com.dveamer.babysitter.settings.SettingsRepository
import com.dveamer.babysitter.settings.SettingsState
import com.dveamer.babysitter.settings.SettingsUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepRuntimeOrchestratorTest {

    @Test
    fun `sleep off keeps foreground runtime while web camera is enabled`() = runBlocking {
        val repository = FakeSettingsRepository(
            SettingsState(
                sleepEnabled = true,
                webServiceEnabled = true,
                webCameraEnabled = true
            )
        )
        val runtime = RecordingSleepRuntime()
        val orchestrator = SleepRuntimeOrchestrator(repository, runtime)
        orchestrator.start(this)
        awaitActionCount(runtime, 1)
        runtime.actions.clear()

        repository.mutableState.value = repository.mutableState.value.copy(
            sleepEnabled = false,
            version = 1L
        )

        awaitActionCount(runtime, 1)
        assertEquals(listOf("start"), runtime.actions)

        runtime.actions.clear()
        repository.mutableState.value = repository.mutableState.value.copy(
            sleepEnabled = true,
            version = 2L
        )

        awaitActionCount(runtime, 1)
        assertEquals(listOf("start"), runtime.actions)
        orchestrator.stop()
    }

    @Test
    fun `runtime stops when sleep and web camera are both disabled`() = runBlocking {
        val repository = FakeSettingsRepository(SettingsState())
        val runtime = RecordingSleepRuntime()
        val orchestrator = SleepRuntimeOrchestrator(repository, runtime)

        orchestrator.start(this)

        awaitActionCount(runtime, 1)
        assertEquals(listOf("stop"), runtime.actions)
        orchestrator.stop()
    }

    private suspend fun awaitActionCount(runtime: RecordingSleepRuntime, expected: Int) {
        withTimeout(1_000L) {
            while (runtime.actions.size < expected) {
                yield()
            }
        }
    }

    private class FakeSettingsRepository(initial: SettingsState) : SettingsRepository {
        val mutableState = MutableStateFlow(initial)
        override val state: StateFlow<SettingsState> = mutableState

        override suspend fun applyUpdate(update: SettingsUpdate): Boolean = true
    }

    private class RecordingSleepRuntime : SleepRuntime {
        val actions = mutableListOf<String>()

        override suspend fun start() {
            actions += "start"
        }

        override suspend fun stop() {
            actions += "stop"
        }
    }
}
