package com.dveamer.babysitter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dveamer.babysitter.billing.MemoryDownloadPurchaseUiState
import com.dveamer.babysitter.settings.AWAKE_TRIGGER_DELAY_STEP_SEC
import com.dveamer.babysitter.settings.MAX_AWAKE_TRIGGER_DELAY_SEC
import com.dveamer.babysitter.settings.MIN_AWAKE_TRIGGER_DELAY_SEC
import com.dveamer.babysitter.settings.MotionSensitivity
import com.dveamer.babysitter.settings.SoundSensitivity
import com.dveamer.babysitter.settings.ThemeMode
import com.dveamer.babysitter.sleep.SleepRuntimeStatusStore
import com.dveamer.babysitter.tutorial.TutorialPlanner
import com.dveamer.babysitter.tutorial.useBrightTutorialTheme
import com.dveamer.babysitter.ui.SettingsViewModel
import com.dveamer.babysitter.ui.SettingsViewModelFactory
import com.dveamer.babysitter.ui.AppTutorialOverlay
import com.dveamer.babysitter.ui.TutorialTargetKey
import com.dveamer.babysitter.ui.captureTutorialBounds
import com.dveamer.babysitter.web.LocalSettingsHttpServer
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileInputStream
import java.net.NetworkInterface
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import java.util.Collections
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appContainer: AppContainer
        get() = (application as BabySitterApplication).container

    private val vm: SettingsViewModel by viewModels {
        SettingsViewModelFactory(
            appContainer.settingsRepository,
            appContainer.settingsController,
            appContainer.tutorialRepository
        )
    }

    private val isRecording = mutableStateOf(false)
    private val currentScreen = mutableStateOf(Screen.HOME)
    private val playingTrackUri = mutableStateOf<String?>(null)
    private val showQrDialog = mutableStateOf(false)
    private val qrCodeUrl = mutableStateOf("")
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordingFilePath: String? = null
    private var pendingRecordStart = false
    private var pendingWebCameraEnable = false

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

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (pendingWebCameraEnable) {
                vm.setWebCamera(granted)
            }
            pendingWebCameraEnable = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val recording by isRecording
            val qrVisible by showQrDialog
            val qrUrl by qrCodeUrl
            val tutorialState by vm.tutorialState.collectAsStateWithLifecycle()
            val state by vm.settingsState.collectAsStateWithLifecycle()
            val colorScheme = when {
                state.themeMode == ThemeMode.LIGHT && tutorialState.useBrightTutorialTheme() -> {
                    TutorialLightColorScheme
                }

                state.themeMode == ThemeMode.LIGHT -> LightColorScheme
                else -> DarkColorScheme
            }

            MaterialTheme(colorScheme = colorScheme) {
                val runtimeStatus by SleepRuntimeStatusStore.state.collectAsStateWithLifecycle()
                val purchaseState by appContainer.memoryDownloadPurchaseManager.uiState
                    .collectAsStateWithLifecycle()
                val screen by currentScreen
                val tutorialScope = rememberCoroutineScope()
                val settingsScrollState = rememberScrollState()
                val monitoringEnabled = state.soundMonitoringEnabled || state.cameraMonitoringEnabled
                val hasM4aRecording = state.musicPlaylist.any(::isM4aRecordingUri)
                val tutorialTargetBounds = remember { mutableStateMapOf<TutorialTargetKey, androidx.compose.ui.geometry.Rect>() }
                val activeTutorialCandidate = TutorialPlanner.resolveStep(
                    tutorialState = tutorialState,
                    isSettingsScreen = screen == Screen.SETTINGS
                )
                var tutorialGateOpen by remember { mutableStateOf(true) }
                val activeTutorial = activeTutorialCandidate?.takeIf { tutorialGateOpen }
                var previousScreen by remember { mutableStateOf(screen) }
                LaunchedEffect(
                    state.soundMonitoringEnabled,
                    state.cameraMonitoringEnabled,
                    state.sleepEnabled
                ) {
                    if (!monitoringEnabled && state.sleepEnabled) {
                        vm.setSleep(false)
                    }
                }
                LaunchedEffect(screen, tutorialState.hasVisitedSettings, tutorialState.firstSettingsVisitFinished) {
                    if (screen == Screen.SETTINGS && !tutorialState.hasVisitedSettings) {
                        vm.markSettingsVisited()
                    }
                    if (
                        previousScreen == Screen.SETTINGS &&
                        screen != Screen.SETTINGS &&
                        tutorialState.hasVisitedSettings &&
                        !tutorialState.firstSettingsVisitFinished
                    ) {
                        vm.finishFirstSettingsVisit()
                    }
                    previousScreen = screen
                }
                LaunchedEffect(state.soundMonitoringEnabled, tutorialState.soundEverEnabled) {
                    if (state.soundMonitoringEnabled && !tutorialState.soundEverEnabled) {
                        vm.markSoundEnabled()
                    }
                }
                LaunchedEffect(state.cameraMonitoringEnabled, tutorialState.motionEverEnabled) {
                    if (state.cameraMonitoringEnabled && !tutorialState.motionEverEnabled) {
                        vm.markMotionEnabled()
                    }
                }
                LaunchedEffect(
                    screen,
                    tutorialState.soundEverEnabled,
                    tutorialState.motionEverEnabled,
                    tutorialState.soundMotionCoachDismissed,
                    tutorialState.remoteCoachReady,
                    tutorialState.remoteCoachDismissed
                ) {
                    val soundMotionCompleted =
                        tutorialState.soundEverEnabled && tutorialState.motionEverEnabled
                    if (!soundMotionCompleted) return@LaunchedEffect

                    if (!tutorialState.soundMotionCoachDismissed) {
                        vm.dismissSoundMotionTutorial()
                    }
                    if (
                        screen == Screen.SETTINGS &&
                        !tutorialState.remoteCoachReady &&
                        !tutorialState.remoteCoachDismissed
                    ) {
                        delay(TUTORIAL_STEP_DELAY_MS)
                        settingsScrollState.animateScrollTo(settingsScrollState.maxValue)
                        vm.markRemoteTutorialReady()
                    }
                }
                LaunchedEffect(state.webServiceEnabled, tutorialState.remoteCoachDismissed) {
                    if (state.webServiceEnabled && !tutorialState.remoteCoachDismissed) {
                        vm.dismissRemoteTutorial()
                    }
                }
                LaunchedEffect(
                    screen,
                    tutorialState.remoteCoachDismissed,
                    tutorialState.celebrationReady,
                    tutorialState.celebrationDismissed
                ) {
                    if (
                        screen == Screen.SETTINGS &&
                        tutorialState.remoteCoachDismissed &&
                        !tutorialState.celebrationReady &&
                        !tutorialState.celebrationDismissed
                    ) {
                        delay(TUTORIAL_FINAL_DELAY_MS)
                        settingsScrollState.animateScrollTo(settingsScrollState.maxValue)
                        vm.markCelebrationTutorialReady()
                        return@LaunchedEffect
                    }

                    if (
                        screen == Screen.SETTINGS &&
                        tutorialState.celebrationReady &&
                        !tutorialState.celebrationDismissed
                    ) {
                        settingsScrollState.animateScrollTo(settingsScrollState.maxValue)
                    }
                }
                val navigateTo: (Screen) -> Unit = { next ->
                    if (currentScreen.value == Screen.RECORDINGS && next != Screen.RECORDINGS) {
                        stopPlayback()
                    }
                    if (next == Screen.SETTINGS && currentScreen.value != Screen.SETTINGS) {
                        vm.setSleep(false)
                    }
                    currentScreen.value = next
                }
                val delayTutorialTransition: (() -> Unit) -> () -> Unit = { action ->
                    {
                        tutorialGateOpen = false
                        action()
                        tutorialScope.launch {
                            delay(TUTORIAL_STEP_DELAY_MS)
                            tutorialGateOpen = true
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        bottomBar = {
                            AppBottomBar(
                                currentScreen = screen,
                                onSelectScreen = navigateTo,
                                settingsItemModifier = Modifier.captureTutorialBounds(
                                    TutorialTargetKey.SETTINGS_TAB
                                ) { key, rect ->
                                    tutorialTargetBounds[key] = rect
                                }
                            )
                        }
                    ) { innerPadding ->
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            when (screen) {
                                Screen.HOME -> HomeScreen(
                                    sleepEnabled = state.sleepEnabled,
                                    sleepToggleEnabled = monitoringEnabled,
                                    hasM4aRecording = hasM4aRecording,
                                    soundMonitoringEnabled = state.soundMonitoringEnabled,
                                    motionMonitoringEnabled = state.cameraMonitoringEnabled,
                                    soothingMusicEnabled = state.soothingMusicEnabled,
                                    hasPlaylist = state.musicPlaylist.isNotEmpty(),
                                    monitoringActive = runtimeStatus.monitoringActive,
                                    lullabyActive = runtimeStatus.lullabyActive,
                                    onOpenRecordings = { navigateTo(Screen.RECORDINGS) },
                                    onSleepToggle = { enabled ->
                                        if (!monitoringEnabled) {
                                            vm.setSleep(false)
                                        } else {
                                            if (enabled) {
                                                requestMonitoringPermissions(state.cameraMonitoringEnabled)
                                            }
                                            vm.setSleep(enabled)
                                        }
                                    }
                                )

                                Screen.SETTINGS -> SettingsScreen(
                                    state = state,
                                    scrollState = settingsScrollState,
                                    purchaseState = purchaseState,
                                    onWebServiceToggle = { enabled ->
                                        if (!enabled) {
                                            vm.setWebService(false)
                                            vm.setWebCamera(false)
                                        } else {
                                            vm.setWebService(true)
                                        }
                                    },
                                    onWebCameraToggle = { enabled ->
                                        if (!enabled) {
                                            pendingWebCameraEnable = false
                                            vm.setWebCamera(false)
                                        } else {
                                            val granted = ContextCompat.checkSelfPermission(
                                                this@MainActivity,
                                                Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                vm.setWebCamera(true)
                                            } else {
                                                pendingWebCameraEnable = true
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        }
                                    },
                                    onSoundToggle = { enabled ->
                                        vm.setSoundMonitoring(enabled)
                                        if (!enabled && !state.cameraMonitoringEnabled && state.sleepEnabled) {
                                            vm.setSleep(false)
                                        }
                                    },
                                    onSoundSensitivityChange = { sensitivity ->
                                        vm.setSoundSensitivity(sensitivity)
                                        val preset = when (sensitivity) {
                                            SoundSensitivity.HIGH -> 250
                                            SoundSensitivity.MEDIUM -> 500
                                            SoundSensitivity.LOW -> 750
                                        }
                                        vm.setCryThresholdSec(preset)
                                    },
                                    onSoundThresholdChange = vm::setCryThresholdSec,
                                    onCameraToggle = { enabled ->
                                        if (enabled) requestMonitoringPermissions(cameraEnabled = true)
                                        vm.setCameraMonitoring(enabled)
                                        if (!enabled && !state.soundMonitoringEnabled && state.sleepEnabled) {
                                            vm.setSleep(false)
                                        }
                                    },
                                    onMotionSensitivityChange = { sensitivity ->
                                        vm.setMotionSensitivity(sensitivity)
                                        val preset = when (sensitivity) {
                                            MotionSensitivity.HIGH -> 14
                                            MotionSensitivity.MEDIUM -> 20
                                            MotionSensitivity.LOW -> 28
                                        }
                                        vm.setMovementThresholdSec(preset)
                                    },
                                    onMotionThresholdChange = vm::setMovementThresholdSec,
                                    onAwakeTriggerDelayChange = vm::setAwakeTriggerDelaySec,
                                    onMusicToggle = vm::setSoothingMusic,
                                    onShowQrCode = ::showQrCodePopup,
                                    onOpenRecordings = { navigateTo(Screen.RECORDINGS) },
                                    selectedThemeMode = state.themeMode,
                                    onThemeModeChange = vm::setThemeMode,
                                    onOpenHomepage = { openExternalUrl(HOMEPAGE_URL) },
                                    onOpenDeveloperPage = { openExternalUrl(DEVELOPER_PAGE_URL) },
                                    onPurchaseMemoryDownloadPass = { productId ->
                                        appContainer.memoryDownloadPurchaseManager.launchPurchase(
                                            activity = this@MainActivity,
                                            productId = productId
                                        )
                                    },
                                    soundRowModifier = Modifier.captureTutorialBounds(
                                        TutorialTargetKey.SOUND_ROW
                                    ) { key, rect ->
                                        tutorialTargetBounds[key] = rect
                                    },
                                    motionRowModifier = Modifier.captureTutorialBounds(
                                        TutorialTargetKey.MOTION_ROW
                                    ) { key, rect ->
                                        tutorialTargetBounds[key] = rect
                                    },
                                    webServiceModifier = Modifier.captureTutorialBounds(
                                        TutorialTargetKey.WEB_SERVICE_ROW
                                    ) { key, rect ->
                                        tutorialTargetBounds[key] = rect
                                    }
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
                    if (activeTutorial != null && !qrVisible) {
                        AppTutorialOverlay(
                            step = activeTutorial,
                            targetBounds = tutorialTargetBounds,
                            onDismissWelcome = delayTutorialTransition(vm::dismissWelcomeTutorial),
                            onOpenSettings = delayTutorialTransition { navigateTo(Screen.SETTINGS) },
                            onDismissSoundMotion = delayTutorialTransition(vm::dismissSoundMotionTutorial),
                            onDismissRemote = delayTutorialTransition(vm::dismissRemoteTutorial),
                            onDismissCelebration = vm::completeCelebrationTutorial
                        )
                    }
                    if (qrVisible) {
                        QrCodeDialog(
                            qrContent = qrUrl,
                            onDismiss = { showQrDialog.value = false }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appContainer.memoryDownloadPurchaseManager.refresh()
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
            if (uri.scheme == "file") {
                val filePath = uri.path ?: throw IllegalStateException("file uri path is null: $uri")
                val file = File(filePath)
                if (!file.exists() || file.length() <= 0L) {
                    throw IllegalStateException("recording file invalid: ${file.absolutePath}")
                }
                FileInputStream(file).use { input ->
                    player.setDataSource(input.fd)
                }
            } else {
                player.setDataSource(this, uri)
            }
            player.setOnCompletionListener {
                stopPlayback()
            }
            player.prepare()
            player.start()
        }.onSuccess {
            mediaPlayer = player
            playingTrackUri.value = uriString
        }.onFailure { t ->
            val fileInfo = runCatching {
                val parsed = Uri.parse(uriString)
                val path = parsed.path
                if (parsed.scheme == "file" && path != null) {
                    val file = File(path)
                    " exists=${file.exists()} len=${file.length()} path=${file.absolutePath}"
                } else {
                    ""
                }
            }.getOrDefault("")
            Log.w(TAG, "failed to play track uri=$uriString$fileInfo", t)
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

    private fun showQrCodePopup() {
        val ip = resolvePrivateIpv4Address()
        val content = if (ip != null) {
            "http://$ip:${LocalSettingsHttpServer.PORT}/index.html"
        } else {
            ""
        }
        qrCodeUrl.value = content
        showQrDialog.value = true
    }

    private fun openExternalUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        runCatching { startActivity(intent) }
            .onFailure { t -> Log.w(TAG, "failed to open url=$url", t) }
    }

    private fun resolvePrivateIpv4Address(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching null
            Collections.list(interfaces)
                .asSequence()
                .flatMap { networkInterface -> Collections.list(networkInterface.inetAddresses).asSequence() }
                .firstOrNull { address ->
                    !address.isLoopbackAddress &&
                        !address.isLinkLocalAddress &&
                        address.hostAddress?.contains(':') == false &&
                        address.isSiteLocalAddress
                }
                ?.hostAddress
        }.getOrNull()
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
        private const val TUTORIAL_STEP_DELAY_MS = 3_000L
        private const val TUTORIAL_FINAL_DELAY_MS = 10_000L
        private const val HOMEPAGE_URL = "https://babysitter.dveaemer.com/index.html"
        private const val DEVELOPER_PAGE_URL =
            "https://play.google.com/store/apps/developer?id=dveamer"
        private val RECORDING_FILE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF0A9C2),
    onPrimary = Color(0xFF462131),
    primaryContainer = Color(0xFF5B3144),
    onPrimaryContainer = Color(0xFFFFDCE8),
    secondary = Color(0xFFF3C48F),
    onSecondary = Color(0xFF4B3114),
    secondaryContainer = Color(0xFF62431F),
    onSecondaryContainer = Color(0xFFFFE7CA),
    tertiary = Color(0xFF9FD8D2),
    onTertiary = Color(0xFF173B39),
    tertiaryContainer = Color(0xFF234D4A),
    onTertiaryContainer = Color(0xFFD3F4EF),
    background = Color(0xFF18121C),
    surface = Color(0xFF211823),
    surfaceVariant = Color(0xFF3A2B38),
    onBackground = Color(0xFFF8EDF2),
    onSurface = Color(0xFFF8EDF2),
    onSurfaceVariant = Color(0xFFE5C9D6),
    outline = Color(0xFF90707E)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFE89AAF),
    onPrimary = Color(0xFF4A2430),
    primaryContainer = Color(0xFFFFDCE5),
    onPrimaryContainer = Color(0xFF5C3540),
    secondary = Color(0xFFF4C98A),
    onSecondary = Color(0xFF4C3312),
    secondaryContainer = Color(0xFFFFE7C6),
    onSecondaryContainer = Color(0xFF64461E),
    tertiary = Color(0xFF9FD8CB),
    onTertiary = Color(0xFF183B33),
    tertiaryContainer = Color(0xFFD8F2EB),
    onTertiaryContainer = Color(0xFF2B5248),
    background = Color(0xFFFFF6F8),
    surface = Color(0xFFFFFBFC),
    surfaceVariant = Color(0xFFFBE8EE),
    onBackground = Color(0xFF3B2A2F),
    onSurface = Color(0xFF3B2A2F),
    onSurfaceVariant = Color(0xFF6E5660),
    outline = Color(0xFFD5B1BD)
)

private val TutorialLightColorScheme = lightColorScheme(
    background = Color(0xFFFFF7EF),
    surface = Color(0xFFFFFBF6),
    primary = Color(0xFFE07A9A),
    secondary = Color(0xFFF2B968),
    tertiary = Color(0xFF7FC5BF),
    onBackground = Color(0xFF2D1918),
    onSurface = Color(0xFF2D1918)
)

@Composable
private fun SettingsScreen(
    state: com.dveamer.babysitter.settings.SettingsState,
    scrollState: androidx.compose.foundation.ScrollState,
    purchaseState: MemoryDownloadPurchaseUiState,
    onWebServiceToggle: (Boolean) -> Unit,
    onWebCameraToggle: (Boolean) -> Unit,
    onSoundToggle: (Boolean) -> Unit,
    onSoundSensitivityChange: (SoundSensitivity) -> Unit,
    onSoundThresholdChange: (Int) -> Unit,
    onCameraToggle: (Boolean) -> Unit,
    onMotionSensitivityChange: (MotionSensitivity) -> Unit,
    onMotionThresholdChange: (Int) -> Unit,
    onAwakeTriggerDelayChange: (Int) -> Unit,
    onMusicToggle: (Boolean) -> Unit,
    onShowQrCode: () -> Unit,
    onOpenRecordings: () -> Unit,
    selectedThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenHomepage: () -> Unit,
    onOpenDeveloperPage: () -> Unit,
    onPurchaseMemoryDownloadPass: (String) -> Unit,
    soundRowModifier: Modifier = Modifier,
    motionRowModifier: Modifier = Modifier,
    webServiceModifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Monitoring", style = MaterialTheme.typography.headlineSmall)

        SwitchRow("Sound", state.soundMonitoringEnabled, onSoundToggle, modifier = soundRowModifier)
        if (state.soundMonitoringEnabled) {
            SoundSensitivitySelector(
                onSelect = onSoundSensitivityChange,
                thresholdValue = state.cryThresholdSec,
                onThresholdValueChange = onSoundThresholdChange
            )
        }
        SwitchRow("Motion", state.cameraMonitoringEnabled, onCameraToggle, modifier = motionRowModifier)
        if (state.cameraMonitoringEnabled) {
            MotionSensitivitySelector(
                onSelect = onMotionSensitivityChange,
                thresholdValue = state.movementThresholdSec,
                onThresholdValueChange = onMotionThresholdChange
            )
        }

        Text(
            "Take Action",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        AwakeTriggerDelaySelector(
            thresholdValue = state.awakeTriggerDelaySec,
            onThresholdValueChange = onAwakeTriggerDelayChange
        )
        SwitchRow("Play Lullaby", state.soothingMusicEnabled, onMusicToggle)

        if (state.soothingMusicEnabled) {
            Button(
                onClick = onOpenRecordings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Recording")
            }
        }

        SwitchRow(
            "Web Service (Remote)",
            state.webServiceEnabled,
            onWebServiceToggle,
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = webServiceModifier
        )
        if (state.webServiceEnabled) {
            SwitchRow("Camera", state.webCameraEnabled, onWebCameraToggle)
            Button(
                onClick = onShowQrCode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("QR Code")
            }

            Text(
                "Memory Download",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            MemoryDownloadPassCard(
                state = purchaseState,
                onPurchase = onPurchaseMemoryDownloadPass
            )
        }

        Text(
            "App",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text("Theme")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeModeButton(
                label = "White Mode",
                selected = selectedThemeMode == ThemeMode.LIGHT,
                onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemeModeButton(
                label = "Dark Mode",
                selected = selectedThemeMode == ThemeMode.DARK,
                onClick = { onThemeModeChange(ThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
        }
        Button(
            onClick = onOpenHomepage,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Homepage")
        }
        Button(
            onClick = onOpenDeveloperPage,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("More Apps")
        }
    }
}

@Composable
private fun ThemeModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
        )
    ) {
        Text(label)
    }
}

@Composable
private fun MemoryDownloadPassCard(
    state: MemoryDownloadPurchaseUiState,
    onPurchase: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Increase Memory downloads from 1/day to 10/day for one month.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = memoryDownloadPassStatusMessage(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.products.forEach { product ->
                Button(
                    onClick = { onPurchase(product.productId) },
                    enabled = product.canPurchase,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(memoryDownloadPassButtonLabel(state, product))
                }
            }
        }
    }
}

private fun memoryDownloadPassStatusMessage(state: MemoryDownloadPurchaseUiState): String {
    return when {
        state.entitlementActive &&
            state.activeProductType == com.android.billingclient.api.BillingClient.ProductType.SUBS -> {
            "Google Play subscription is active. Memory downloads remain limited to 10 per day while the subscription stays active."
        }
        state.entitlementActive && state.activeUntilEpochMs != null -> {
            "Active until ${formatBillingDateTime(state.activeUntilEpochMs)}. " +
                "Memory downloads are now limited to 10 per day."
        }
        state.pendingPurchase -> {
            val productName = state.pendingProductName?.let { "$it " }.orEmpty()
            "${productName}purchase is pending in Google Play. The 10/day limit is applied after payment completes."
        }
        state.loading -> "Loading Google Play purchase status..."
        state.hasPurchaseOptions -> {
            "Available in Google Play. Current daily download limit: ${state.currentDailyLimit}."
        }
        else -> {
            "Google Play product is not available yet. Check the Play Console product ID and rollout."
        }
    }
}

private fun memoryDownloadPassButtonLabel(
    state: MemoryDownloadPurchaseUiState,
    product: com.dveamer.babysitter.billing.MemoryDownloadProductUiState
): String {
    return when {
        state.entitlementActive && state.activeProductId == product.productId -> "Active"
        state.entitlementActive -> "Already active"
        state.pendingPurchase -> "Pending"
        state.loading -> "Loading..."
        !product.available -> "Unavailable"
        product.purchaseInProgress -> "Opening Google Play..."
        !product.priceText.isNullOrBlank() &&
            product.productType == com.android.billingclient.api.BillingClient.ProductType.SUBS -> {
            "Subscribe ${product.priceText}"
        }
        !product.priceText.isNullOrBlank() -> "Buy ${product.priceText}"
        product.productType == com.android.billingclient.api.BillingClient.ProductType.SUBS -> {
            "Subscribe ${product.productName}"
        }
        else -> "Buy ${product.productName}"
    }
}

private fun formatBillingDateTime(epochMs: Long): String {
    return Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

@Composable
private fun QrCodeDialog(
    qrContent: String,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(qrContent) {
        if (qrContent.isBlank()) null else createQrCodeBitmap(qrContent, 900)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text("QR Code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Web service QR code",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(qrContent, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Unable to resolve local Wi-Fi IP on this device.")
                }
            }
        }
    )
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
    sleepToggleEnabled: Boolean,
    hasM4aRecording: Boolean,
    soundMonitoringEnabled: Boolean,
    motionMonitoringEnabled: Boolean,
    soothingMusicEnabled: Boolean,
    hasPlaylist: Boolean,
    monitoringActive: Boolean,
    lullabyActive: Boolean,
    onOpenRecordings: () -> Unit,
    onSleepToggle: (Boolean) -> Unit
) {
    val monitoringReady = soundMonitoringEnabled || motionMonitoringEnabled
    val monitoringStatus = when {
        !monitoringReady -> "not ready"
        sleepEnabled && monitoringActive -> "active"
        else -> "ready"
    }
    val lullabyReady = soothingMusicEnabled && hasPlaylist
    val lullabyStatus = when {
        lullabyActive -> "active"
        !lullabyReady -> "not ready"
        else -> "ready"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (!hasM4aRecording) {
            Text(
                text = keepWordsUnbroken(stringResource(R.string.home_no_recordings_guide)),
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
        }

        Button(
            onClick = { onSleepToggle(!sleepEnabled) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            enabled = sleepToggleEnabled,
            shape = CircleShape
        ) {
            Text(if (sleepEnabled) "Sleep OFF" else "Sleep ON")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "• Monitoring : $monitoringStatus",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "• Lullaby : $lullabyStatus",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun AppBottomBar(
    currentScreen: Screen,
    onSelectScreen: (Screen) -> Unit,
    settingsItemModifier: Modifier = Modifier
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentScreen == Screen.HOME,
            onClick = { onSelectScreen(Screen.HOME) },
            icon = {},
            label = { Text("Home") }
        )
        NavigationBarItem(
            modifier = settingsItemModifier,
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

private fun isM4aRecordingUri(uriString: String): Boolean {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
    val name = uri.lastPathSegment?.substringAfterLast('/') ?: return false
    return name.substringAfterLast('.', "").equals("m4a", ignoreCase = true)
}

private fun keepWordsUnbroken(text: String): String {
    val wordJoiner = "\u2060"
    return text.split(" ").joinToString(" ") { word ->
        word.toCharArray().joinToString(wordJoiner)
    }
}

private fun createQrCodeBitmap(content: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(
                x,
                y,
                if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
            )
        }
    }
    return bitmap
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = textStyle)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MotionSensitivitySelector(
    onSelect: (MotionSensitivity) -> Unit,
    thresholdValue: Int,
    onThresholdValueChange: (Int) -> Unit
) {
    val selectedPreset = when (thresholdValue) {
        14 -> MotionSensitivity.HIGH
        20 -> MotionSensitivity.MEDIUM
        28 -> MotionSensitivity.LOW
        else -> null
    }
    Text("Sensitivity ($thresholdValue)")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            MotionSensitivity.HIGH to "High",
            MotionSensitivity.MEDIUM to "Medium",
            MotionSensitivity.LOW to "Low"
        ).forEach { (value, label) ->
            Button(
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedPreset == value) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (selectedPreset == value) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            ) {
                Text(label)
            }
        }
    }
    Slider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        value = thresholdValue.toFloat().coerceIn(5f, 100f),
        onValueChange = { onThresholdValueChange(it.toInt().coerceIn(5, 100)) },
        valueRange = 5f..100f
    )
}

@Composable
private fun AwakeTriggerDelaySelector(
    thresholdValue: Int,
    onThresholdValueChange: (Int) -> Unit
) {
    Text("After (${thresholdValue} sec)")
    Slider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        value = thresholdValue.toFloat().coerceIn(
            MIN_AWAKE_TRIGGER_DELAY_SEC.toFloat(),
            MAX_AWAKE_TRIGGER_DELAY_SEC.toFloat()
        ),
        onValueChange = {
            val snapped = (
                (it / AWAKE_TRIGGER_DELAY_STEP_SEC.toFloat()).roundToInt() *
                    AWAKE_TRIGGER_DELAY_STEP_SEC
                ).coerceIn(
                    MIN_AWAKE_TRIGGER_DELAY_SEC,
                    MAX_AWAKE_TRIGGER_DELAY_SEC
                )
            onThresholdValueChange(snapped)
        },
        valueRange = MIN_AWAKE_TRIGGER_DELAY_SEC.toFloat()..MAX_AWAKE_TRIGGER_DELAY_SEC.toFloat(),
        steps = ((MAX_AWAKE_TRIGGER_DELAY_SEC - MIN_AWAKE_TRIGGER_DELAY_SEC) / AWAKE_TRIGGER_DELAY_STEP_SEC) - 1
    )
}

@Composable
private fun SoundSensitivitySelector(
    onSelect: (SoundSensitivity) -> Unit,
    thresholdValue: Int,
    onThresholdValueChange: (Int) -> Unit
) {
    val selectedPreset = when (thresholdValue) {
        250 -> SoundSensitivity.HIGH
        500 -> SoundSensitivity.MEDIUM
        750 -> SoundSensitivity.LOW
        else -> null
    }
    Text("Sensitivity ($thresholdValue)")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            SoundSensitivity.HIGH to "High",
            SoundSensitivity.MEDIUM to "Medium",
            SoundSensitivity.LOW to "Low"
        ).forEach { (value, label) ->
            Button(
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedPreset == value) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (selectedPreset == value) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            ) {
                Text(label)
            }
        }
    }
    Slider(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        value = thresholdValue.toFloat().coerceIn(10f, 1000f),
        onValueChange = { onThresholdValueChange(it.toInt().coerceIn(10, 1_000)) },
        valueRange = 10f..1000f
    )
}

private enum class Screen {
    HOME,
    SETTINGS,
    RECORDINGS
}
