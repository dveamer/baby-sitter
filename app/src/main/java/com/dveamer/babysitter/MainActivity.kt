package com.dveamer.babysitter

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dveamer.babysitter.ui.SettingsViewModel
import com.dveamer.babysitter.ui.SettingsViewModelFactory
import java.io.File

class MainActivity : ComponentActivity() {

    private val vm: SettingsViewModel by viewModels {
        val container = (application as BabySitterApplication).container
        SettingsViewModelFactory(container.settingsRepository, container.settingsController)
    }

    private val isRecording = mutableStateOf(false)
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFilePath: String? = null
    private var pendingRecordStart = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val recordPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingRecordStart) {
                pendingRecordStart = false
                startRecordingInternal()
            } else {
                pendingRecordStart = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
            val recording by isRecording

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by vm.settingsState.collectAsStateWithLifecycle()
                    SettingsScreen(
                        state = state,
                        isRecording = recording,
                        onSleepToggle = { enabled ->
                            if (enabled) requestMonitoringPermissions(state.cameraMonitoringEnabled)
                            vm.setSleep(enabled)
                        },
                        onCameraToggle = { enabled ->
                            if (enabled) requestMonitoringPermissions(cameraEnabled = true)
                            vm.setCameraMonitoring(enabled)
                        },
                        onMusicToggle = vm::setSoothingMusic,
                        onIotToggle = vm::setSoothingIot,
                        onCrySecChange = vm::setCryThresholdSec,
                        onMoveSecChange = vm::setMovementThresholdSec,
                        onWakeAlertMinChange = vm::setWakeAlertThresholdMin,
                        onStartRecording = ::startRecording,
                        onStopRecording = { stopRecording(vm) },
                        onDeleteTrack = { index ->
                            state.musicPlaylist.getOrNull(index)?.let { uri ->
                                deleteTrack(uri)
                                vm.removeMusicTrackAt(index)
                            }
                        },
                        onTelegramTokenChange = vm::setTelegramBotToken,
                        onTelegramChatIdChange = vm::setTelegramChatId,
                        onIotEndpointChange = vm::setIotEndpoint
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        releaseRecorder(deleteIncompleteFile = true)
        super.onDestroy()
    }

    private fun requestMonitoringPermissions(cameraEnabled: Boolean) {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (cameraEnabled) add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun startRecording() {
        if (isRecording.value) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            pendingRecordStart = true
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        startRecordingInternal()
    }

    private fun startRecordingInternal() {
        val outputDir = File(filesDir, RECORDINGS_DIR).apply { mkdirs() }
        if (!outputDir.exists()) {
            Log.w(TAG, "recordings directory unavailable")
            return
        }

        val outputFile = File(outputDir, "soothing_${System.currentTimeMillis()}.m4a")
        val recorder = MediaRecorder()

        runCatching {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()
        }.onSuccess {
            mediaRecorder = recorder
            recordingFilePath = outputFile.absolutePath
            isRecording.value = true
        }.onFailure { t ->
            Log.w(TAG, "failed to start recording", t)
            runCatching { recorder.release() }
            runCatching { outputFile.delete() }
        }
    }

    private fun stopRecording(viewModel: SettingsViewModel) {
        if (!isRecording.value) return

        val outputPath = recordingFilePath
        val recorder = mediaRecorder
        mediaRecorder = null
        recordingFilePath = null

        var stopped = false
        if (recorder != null) {
            runCatching {
                recorder.stop()
                stopped = true
            }.onFailure { t ->
                Log.w(TAG, "failed to stop recording", t)
            }
            runCatching { recorder.release() }
        }

        isRecording.value = false

        if (outputPath.isNullOrBlank()) return
        val recordedFile = File(outputPath)

        if (stopped && recordedFile.exists() && recordedFile.length() > 0L) {
            viewModel.addMusicTrack(Uri.fromFile(recordedFile).toString())
        } else {
            runCatching { recordedFile.delete() }
        }
    }

    private fun deleteTrack(uriString: String) {
        runCatching {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                uri.path?.let { path ->
                    File(path).delete()
                }
            }
        }.onFailure { t ->
            Log.w(TAG, "failed to delete track file uri=$uriString", t)
        }
    }

    private fun releaseRecorder(deleteIncompleteFile: Boolean) {
        val recorder = mediaRecorder
        mediaRecorder = null

        if (recorder != null) {
            runCatching { recorder.release() }
        }

        if (deleteIncompleteFile) {
            val path = recordingFilePath
            if (!path.isNullOrBlank()) {
                runCatching { File(path).delete() }
            }
        }

        recordingFilePath = null
        isRecording.value = false
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val RECORDINGS_DIR = "soothing-recordings"
    }
}

private val DarkColorScheme = darkColorScheme(
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFF5F5F5)
)

private val LightColorScheme = lightColorScheme(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111)
)

@Composable
private fun SettingsScreen(
    state: com.dveamer.babysitter.settings.SettingsState,
    isRecording: Boolean,
    onSleepToggle: (Boolean) -> Unit,
    onCameraToggle: (Boolean) -> Unit,
    onMusicToggle: (Boolean) -> Unit,
    onIotToggle: (Boolean) -> Unit,
    onCrySecChange: (Int) -> Unit,
    onMoveSecChange: (Int) -> Unit,
    onWakeAlertMinChange: (Int) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDeleteTrack: (Int) -> Unit,
    onTelegramTokenChange: (String) -> Unit,
    onTelegramChatIdChange: (String) -> Unit,
    onIotEndpointChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Sleep Settings", style = MaterialTheme.typography.headlineSmall)

        SwitchRow("Sleep ON/OFF", state.sleepEnabled, onSleepToggle)
        SwitchRow("Camera Monitoring", state.cameraMonitoringEnabled, onCameraToggle)
        SwitchRow("Music Soothing", state.soothingMusicEnabled, onMusicToggle)
        SwitchRow("IoT Soothing", state.soothingIotEnabled, onIotToggle)

        NumberField(
            label = "Cry Threshold (sec)",
            value = state.cryThresholdSec,
            onValueChange = onCrySecChange
        )
        NumberField(
            label = "Movement Threshold (sec)",
            value = state.movementThresholdSec,
            onValueChange = onMoveSecChange
        )
        NumberField(
            label = "Wake Alert Threshold (min)",
            value = state.wakeAlertThresholdMin,
            onValueChange = onWakeAlertMinChange
        )

        Text("Music Playlist (Recordings)")
        Button(onClick = { if (isRecording) onStopRecording() else onStartRecording() }) {
            Text(if (isRecording) "Stop Recording" else "Record New Track")
        }

        if (state.musicPlaylist.isEmpty()) {
            Text("No recorded tracks")
        } else {
            state.musicPlaylist.forEachIndexed { index, uriString ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = trackLabel(uriString, index),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .padding(end = 8.dp)
                    )
                    TextButton(onClick = { onDeleteTrack(index) }) {
                        Text("Delete")
                    }
                }
            }
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.iotEndpoint,
            onValueChange = onIotEndpointChange,
            label = { Text("IoT Endpoint URL") }
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.telegramBotToken,
            onValueChange = onTelegramTokenChange,
            label = { Text("Telegram Bot Token") }
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.telegramChatId,
            onValueChange = onTelegramChatIdChange,
            label = { Text("Telegram Chat ID") }
        )

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Last update: ${state.updatedBy} / v${state.version}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun trackLabel(uriString: String, index: Int): String {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull()
    val segment = uri?.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    return segment ?: "Recording ${index + 1}"
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value.toString(),
        onValueChange = { raw -> raw.toIntOrNull()?.let(onValueChange) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
