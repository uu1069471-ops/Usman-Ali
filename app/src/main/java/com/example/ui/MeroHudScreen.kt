package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.MeroMessage
import com.example.model.MeroState
import com.example.ui.components.ArcCoreVisualizer
import com.example.ui.components.VoiceSettingsSheet
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.ArcCyanBright
import com.example.ui.theme.FilmyGold
import com.example.ui.theme.HairlineCyan
import com.example.ui.theme.HairlineGold
import com.example.ui.theme.IceWhite
import com.example.ui.theme.MistSlate
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianGlass
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PulseCrimson
import com.example.ui.theme.PulseEmerald

@Composable
fun MeroHudScreen(
    viewModel: MeroViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var commandText by remember { mutableStateOf("") }
    var showVoiceSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
        if (granted) {
            viewModel.startListening()
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    val handleCoreTap = {
        if (uiState.meroState == MeroState.STANDBY || uiState.meroState == MeroState.ERROR) {
            if (uiState.hasMicPermission) {
                viewModel.onArcCoreTapped()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            viewModel.onArcCoreTapped()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // Atmospheric HUD backdrop artwork
        Image(
            painter = painterResource(id = R.drawable.img_mero_hud_backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.28f)
        )

        // Deep obsidian gradient scrim for crisp legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianVoid.copy(alpha = 0.88f),
                            ObsidianVoid.copy(alpha = 0.45f),
                            ObsidianVoid.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        // Centered responsive HUD container (supports phones, foldables, tablets)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .widthIn(max = 640.dp)
                .align(Alignment.Center)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopTelemetryHeader(
                uiState = uiState,
                onOpenVoiceMatrix = { showVoiceSheet = true },
                onToggleHandsFree = { viewModel.setHandsFreeLoop(!uiState.preferences.handsFreeLoop) },
                onClearSession = { viewModel.clearSession() }
            )

            // Mic permission reminder banner if denied
            AnimatedVisibility(visible = !uiState.hasMicPermission) {
                PermissionNoticeCard(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }

            // Transparent error or status notice banner
            AnimatedVisibility(
                visible = uiState.errorMessage != null || uiState.statusNotice != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val isError = uiState.errorMessage != null
                val bannerText = uiState.errorMessage ?: uiState.statusNotice.orEmpty()
                StatusBanner(
                    message = bannerText,
                    isError = isError,
                    onDismiss = { viewModel.dismissError() }
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                val compactHeight = maxHeight < 460.dp
                val coreSize = when {
                    compactHeight -> 156.dp
                    uiState.messages.isEmpty() -> 232.dp
                    else -> 182.dp
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(6.dp))

                    ArcCoreVisualizer(
                        meroState = uiState.meroState,
                        amplitude = uiState.amplitude,
                        spectrum = uiState.spectrum,
                        recordingDurationMs = uiState.recordingDurationMs,
                        coreSize = coreSize,
                        onTap = handleCoreTap
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Telemetry Ribbon
                    TelemetryRibbon(uiState = uiState)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Neural Conversation Stream or Starter Briefing
                    if (uiState.messages.isEmpty()) {
                        MeroStarterBriefing(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            onStarterTapped = { prompt ->
                                viewModel.sendTextCommand(prompt)
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            itemsIndexed(
                                items = uiState.messages,
                                key = { _, msg -> msg.id }
                            ) { index, message ->
                                MessageCard(
                                    message = message,
                                    isPlaying = uiState.playingMessageId == message.id &&
                                        uiState.meroState == MeroState.SPEAKING,
                                    index = index,
                                    onReplayClick = { viewModel.replayMessage(message) }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Command Dock
            CommandInputDock(
                text = commandText,
                onTextChange = { commandText = it },
                meroState = uiState.meroState,
                onSendText = {
                    val toSend = commandText
                    commandText = ""
                    viewModel.sendTextCommand(toSend)
                },
                onMicClick = handleCoreTap
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showVoiceSheet) {
        VoiceSettingsSheet(
            preferences = uiState.preferences,
            onSelectVoice = viewModel::setVoiceProfile,
            onSelectPersona = viewModel::setPersonaStyle,
            onToggleHandsFree = viewModel::setHandsFreeLoop,
            onToggleAutoSilence = viewModel::setAutoSilenceStop,
            onToggleSpokenReplies = viewModel::setSpokenReplies,
            onDismiss = { showVoiceSheet = false }
        )
    }
}

@Composable
private fun TopTelemetryHeader(
    uiState: MeroUiState,
    onOpenVoiceMatrix: () -> Unit,
    onToggleHandsFree: () -> Unit,
    onClearSession: () -> Unit
) {
    val stateColor = when (uiState.meroState) {
        MeroState.STANDBY -> ArcCyan
        MeroState.LISTENING -> PulseCrimson
        MeroState.THINKING -> ArcCyanBright
        MeroState.SPEAKING -> FilmyGold
        MeroState.ERROR -> PulseCrimson
    }

    val stateLabel = when (uiState.meroState) {
        MeroState.STANDBY -> stringResource(R.string.status_standby)
        MeroState.LISTENING -> stringResource(R.string.status_listening)
        MeroState.THINKING -> stringResource(R.string.status_thinking)
        MeroState.SPEAKING -> stringResource(R.string.status_speaking)
        MeroState.ERROR -> stringResource(R.string.status_error)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MERO",
                        style = MaterialTheme.typography.headlineMedium,
                        color = IceWhite
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = stateColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, stateColor.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(stateColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = stateColor
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.mero_tagline),
                    style = MaterialTheme.typography.labelSmall,
                    color = MistSlate
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianGlass,
                border = BorderStroke(1.dp, HairlineGold),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onOpenVoiceMatrix)
                    .testTag("voice_matrix_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Open Mero Voice Matrix",
                        tint = FilmyGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = uiState.preferences.voiceProfile.voiceName.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = FilmyGold
                    )
                }
            }

            IconButton(
                onClick = onToggleHandsFree,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("hands_free_toggle")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AllInclusive,
                    contentDescription = "Toggle continuous hands-free Jarvis loop",
                    tint = if (uiState.preferences.handsFreeLoop) PulseEmerald else MistSlate
                )
            }

            if (uiState.messages.isNotEmpty()) {
                IconButton(
                    onClick = onClearSession,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("clear_session_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = stringResource(R.string.clear_session),
                        tint = MistSlate
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryRibbon(uiState: MeroUiState) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianGlass,
        border = BorderStroke(1.dp, HairlineCyan),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VOICE: ${uiState.preferences.voiceProfile.badge}",
                style = MaterialTheme.typography.labelSmall,
                color = FilmyGold
            )
            Text(
                text = uiState.preferences.personaStyle.title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = ArcCyan
            )
            Text(
                text = if (uiState.preferences.handsFreeLoop) "LOOP: ON" else "LOOP: MANUAL",
                style = MaterialTheme.typography.labelSmall,
                color = if (uiState.preferences.handsFreeLoop) PulseEmerald else MistSlate
            )
        }
    }
}

@Composable
private fun PermissionNoticeCard(onRequestPermission: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianElevated,
        border = BorderStroke(1.dp, HairlineGold),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.MicOff,
                contentDescription = null,
                tint = FilmyGold,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.mic_permission_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = IceWhite
                )
                Text(
                    text = stringResource(R.string.mic_permission_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MistSlate
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FilmyGold,
                    contentColor = ObsidianVoid
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("enable_mic_button")
            ) {
                Text(
                    text = stringResource(R.string.mic_permission_grant),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    val accent = if (isError) PulseCrimson else FilmyGold
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = IceWhite,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss notice",
                    tint = accent
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeroStarterBriefing(
    modifier: Modifier = Modifier,
    onStarterTapped: (String) -> Unit
) {
    val starters = listOf(
        "Introduce yourself, Mero",
        "Give me a filmy movie-style pep talk",
        "Brief me like Jarvis on today's focus",
        "Talk to me in dramatic Hinglish"
    )

    Column(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ObsidianGlass,
            border = BorderStroke(1.dp, HairlineCyan),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = FilmyGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "READY FOR VOICE LINK",
                        style = MaterialTheme.typography.labelLarge,
                        color = FilmyGold
                    )
                }
                Text(
                    text = "Tap the glowing core above to speak directly to Mero, or launch a scene below to hear her cinematic female voice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MistSlate
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    starters.forEachIndexed { idx, prompt ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianElevated,
                            border = BorderStroke(1.dp, HairlineCyan),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable { onStarterTapped(prompt) }
                                .testTag("starter_chip_$idx")
                        ) {
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.bodyMedium,
                                color = IceWhite,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: MeroMessage,
    isPlaying: Boolean,
    index: Int,
    onReplayClick: () -> Unit
) {
    val isUser = message.isUser
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isUser) ObsidianElevated.copy(alpha = 0.85f) else ObsidianSurface.copy(alpha = 0.92f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isUser) HairlineCyan else if (isPlaying) FilmyGold else HairlineGold
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isUser) "YOU" else "MERO",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isUser) ArcCyan else FilmyGold
                    )
                    if (!isUser && message.latencyMs != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format("%.1fs", message.latencyMs / 1000f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MistSlate
                        )
                    }
                }

                if (!isUser) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isPlaying) FilmyGold.copy(alpha = 0.22f) else ObsidianElevated,
                        border = BorderStroke(1.dp, if (isPlaying) FilmyGold else HairlineGold),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable(onClick = onReplayClick)
                            .testTag("replay_button_$index")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Stop playback" else "Replay Mero voice",
                                tint = FilmyGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPlaying) "STOP" else "VOICE",
                                style = MaterialTheme.typography.labelSmall,
                                color = FilmyGold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = IceWhite
            )
        }
    }
}

@Composable
private fun CommandInputDock(
    text: String,
    onTextChange: (String) -> Unit,
    meroState: MeroState,
    onSendText: () -> Unit,
    onMicClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = ObsidianGlass,
        border = BorderStroke(1.dp, HairlineCyan),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.input_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MistSlate
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (text.isNotBlank()) onSendText()
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = IceWhite,
                    unfocusedTextColor = IceWhite,
                    cursorColor = ArcCyan
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input")
            )

            if (text.isNotBlank()) {
                IconButton(
                    onClick = onSendText,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("send_text_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send command to Mero",
                        tint = ArcCyan
                    )
                }
            } else {
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dock_mic_button")
                ) {
                    Icon(
                        imageVector = when (meroState) {
                            MeroState.LISTENING -> Icons.Rounded.Stop
                            MeroState.SPEAKING -> Icons.Rounded.GraphicEq
                            else -> Icons.Rounded.Mic
                        },
                        contentDescription = "Toggle voice input",
                        tint = if (meroState == MeroState.LISTENING) PulseCrimson else FilmyGold
                    )
                }
            }
        }
    }
}
