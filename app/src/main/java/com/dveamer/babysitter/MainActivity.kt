package com.dveamer.babysitter

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dveamer.babysitter.ui.SettingsViewModel
import com.dveamer.babysitter.ui.SettingsViewModelFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private val vm: SettingsViewModel by viewModels {
        val container = (application as BabySitterApplication).container
        SettingsViewModelFactory(container.settingsRepository, container.settingsController)
    }

    private val isRecording = mutableStateOf(false)
    private val currentScreen = mutableStateOf(Screen.HOME)
    private val playingTrackUri = mutableStateOf<String?>(null)
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
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
                val state by vm.settingsState.collectAsStateWithLifecycle()
                val navigateTo: (Screen) -> Unit = { next ->
                    if (currentScreen.value == Screen.RECORDINGS && next != Screen.RECORDINGS) {
                        stopPlayback()
                    }
                    if (next == Screen.SETTINGS && currentScreen.value != Screen.SETTINGS) {
                        vm.setSleep(false)
                    }
                    currentScreen.value = next
                }
                Scaffold(
                    topBar = {
                        AppTopBar(
                            currentScreen = currentScreen.value
                        )
                    },
                    bottomBar = {
                        AppBottomBar(
                            currentScreen = currentScreen.value,
                            onSelectScreen = navigateTo
                        )
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (currentScreen.value) {
                            Screen.HOME -> HomeScreen(
                                sleepEnabled = state.sleepEnabled,
                                onOpenRecordings = { navigateTo(Screen.RECORDINGS) },
                                onSleepToggle = { enabled ->
                                    if (enabled) {
                                        requestMonitoringPermissions(state.cameraMonitoringEnabled)
                                    }
                                    vm.setSleep(enabled)
                                }
                            )

                            Screen.SETTINGS -> SettingsScreen(
                                state = state,
                                onSoundToggle = vm::setSoundMonitoring,
                                onCameraToggle = { enabled ->
                                    if (enabled) requestMonitoringPermissions(cameraEnabled = true)
                                    vm.setCameraMonitoring(enabled)
                                },
                                onMusicToggle = vm::setSoothingMusic,
                                onWakeAlertMinChange = vm::setWakeAlertThresholdMin,
                                onOpenRecordings = { navigateTo(Screen.RECORDINGS) },
                                onTelegramTokenChange = vm::setTelegramBotToken,
                                onTelegramChatIdChange = vm::setTelegramChatId
                            )

                            Screen.RECORDINGS -> RecordingManagementScreen(
                                state = state,
                                isRecording = recording,
                                playingUri = playingTrackUri.value,
                                onStartRecording = ::startRecording,
                                onStopRecording = { stopRecording(vm) },
                                onTogglePlay = ::togglePlayback,
                                onDeleteTrack = { index ->
                                    state.musicPlaylist.getOrNull(index)?.let { uri ->
                                        if (playingTrackUri.value == uri) {
                                            stopPlayback()
                                        }
                                        deleteTrack(uri)
                                        vm.removeMusicTrackAt(index)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        releaseRecorder(deleteIncompleteFile = true)
        stopPlayback()
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

        val outputFile = File(outputDir, buildRecordingFileName())
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

    private fun togglePlayback(uriString: String) {
        if (playingTrackUri.value == uriString) {
            stopPlayback()
            return
        }
        stopPlayback()

        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        val player = MediaPlayer()
        runCatching {
            player.setDataSource(this, uri)
            player.setOnCompletionListener {
                stopPlayback()
            }
            player.prepare()
            player.start()
        }.onSuccess {
            mediaPlayer = player
            playingTrackUri.value = uriString
        }.onFailure { t ->
            Log.w(TAG, "failed to play track uri=$uriString", t)
            runCatching { player.release() }
            mediaPlayer = null
            playingTrackUri.value = null
        }
    }

    private fun stopPlayback() {
        val player = mediaPlayer
        mediaPlayer = null
        playingTrackUri.value = null
        if (player != null) {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
    }

    private fun buildRecordingFileName(): String {
        val now = LocalDateTime.now()
        val timestamp = now.format(RECORDING_FILE_FORMATTER)
        return "$timestamp.m4a"
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
        private val RECORDING_FILE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
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
    onSoundToggle: (Boolean) -> Unit,
    onCameraToggle: (Boolean) -> Unit,
    onMusicToggle: (Boolean) -> Unit,
    onWakeAlertMinChange: (Int) -> Unit,
    onOpenRecordings: () -> Unit,
    onTelegramTokenChange: (String) -> Unit,
    onTelegramChatIdChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Monitoring", style = MaterialTheme.typography.headlineSmall)

        SwitchRow("Sound", state.soundMonitoringEnabled, onSoundToggle)
        SwitchRow("Motion", state.cameraMonitoringEnabled, onCameraToggle)

        Text(
            "Take Action",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        SwitchRow("Play Music", state.soothingMusicEnabled, onMusicToggle)

        Button(
            onClick = onOpenRecordings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manage Recording")
        }
        Text(
            "Wake Alert",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        NumberField(
            label = "Treshold (min)",
            value = state.wakeAlertThresholdMin,
            onValueChange = onWakeAlertMinChange
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

    }
}

@Composable
private fun RecordingManagementScreen(
    state: com.dveamer.babysitter.settings.SettingsState,
    isRecording: Boolean,
    playingUri: String?,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onTogglePlay: (String) -> Unit,
    onDeleteTrack: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Recordings", style = MaterialTheme.typography.headlineSmall)

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
                                .fillMaxWidth(0.5f)
                                .padding(end = 8.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = { onTogglePlay(uriString) }) {
                                Text(if (playingUri == uriString) "Stop" else "Play")
                            }
                            TextButton(onClick = { onDeleteTrack(index) }) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    sleepEnabled: Boolean,
    onOpenRecordings: () -> Unit,
    onSleepToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.home_no_recordings_guide),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpenRecordings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.manage_recordings))
        }
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = { onSleepToggle(!sleepEnabled) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = CircleShape
        ) {
            Text(if (sleepEnabled) "Sleep OFF" else "Sleep ON")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    currentScreen: Screen
) {
    TopAppBar(
        title = {
            val title = when (currentScreen) {
                Screen.HOME -> "Home"
                Screen.SETTINGS -> "Settings"
                Screen.RECORDINGS -> "Recordings"
            }
            Text(title)
        }
    )
}

@Composable
private fun AppBottomBar(
    currentScreen: Screen,
    onSelectScreen: (Screen) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentScreen == Screen.HOME,
            onClick = { onSelectScreen(Screen.HOME) },
            icon = {},
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = currentScreen == Screen.SETTINGS,
            onClick = { onSelectScreen(Screen.SETTINGS) },
            icon = {},
            label = { Text("Settings") }
        )
    }
}

private fun trackLabel(uriString: String, index: Int): String {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull()
    val segment = uri?.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    if (segment == "lkoliks-lullaby-baby-sleep-music-331777.mp3") {
        return "lkoliks-lullaby-baby-sleep-music"
    }
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
private enum class Screen {
    HOME,
    SETTINGS,
    RECORDINGS
}
