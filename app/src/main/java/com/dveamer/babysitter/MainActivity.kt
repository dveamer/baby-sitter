package com.dveamer.babysitter

import android.Manifest
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dveamer.babysitter.ui.SettingsViewModel
import com.dveamer.babysitter.ui.SettingsViewModelFactory

class MainActivity : ComponentActivity() {

    private val vm: SettingsViewModel by viewModels {
        val container = (application as BabySitterApplication).container
        SettingsViewModelFactory(container.settingsRepository, container.settingsController)
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by vm.settingsState.collectAsStateWithLifecycle()
                    SettingsScreen(
                        state = state,
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
                        onPlaylistChange = vm::setMusicPlaylistFromText,
                        onTelegramTokenChange = vm::setTelegramBotToken,
                        onTelegramChatIdChange = vm::setTelegramChatId,
                        onIotEndpointChange = vm::setIotEndpoint
                    )
                }
            }
        }
    }

    private fun requestMonitoringPermissions(cameraEnabled: Boolean) {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (cameraEnabled) add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
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
    onSleepToggle: (Boolean) -> Unit,
    onCameraToggle: (Boolean) -> Unit,
    onMusicToggle: (Boolean) -> Unit,
    onIotToggle: (Boolean) -> Unit,
    onCrySecChange: (Int) -> Unit,
    onMoveSecChange: (Int) -> Unit,
    onWakeAlertMinChange: (Int) -> Unit,
    onPlaylistChange: (String) -> Unit,
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

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.musicPlaylist.joinToString("\n"),
            onValueChange = onPlaylistChange,
            label = { Text("Music Playlist URIs (line/comma separated)") },
            minLines = 3
        )

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
