package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.MeroPersonaStyle
import com.example.model.MeroPreferences
import com.example.model.MeroVoiceProfile
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.FilmyGold
import com.example.ui.theme.HairlineCyan
import com.example.ui.theme.HairlineGold
import com.example.ui.theme.IceWhite
import com.example.ui.theme.MistSlate
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsSheet(
    preferences: MeroPreferences,
    onSelectVoice: (MeroVoiceProfile) -> Unit,
    onSelectPersona: (MeroPersonaStyle) -> Unit,
    onToggleHandsFree: (Boolean) -> Unit,
    onToggleAutoSilence: (Boolean) -> Unit,
    onToggleSpokenReplies: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface,
        contentColor = IceWhite,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.RecordVoiceOver,
                    contentDescription = null,
                    tint = FilmyGold,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Mero Voice Matrix",
                        style = MaterialTheme.typography.titleLarge,
                        color = IceWhite
                    )
                    Text(
                        text = "FEMALE TIMBRE & CINEMATIC DELIVERY",
                        style = MaterialTheme.typography.labelSmall,
                        color = ArcCyan
                    )
                }
            }

            HorizontalDivider(color = HairlineCyan)

            Text(
                text = "FEMALE VOICE TIMBRE",
                style = MaterialTheme.typography.labelLarge,
                color = MistSlate
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MeroVoiceProfile.entries.forEach { profile ->
                    val selected = profile == preferences.voiceProfile
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) ObsidianElevated else ObsidianVoid.copy(alpha = 0.6f),
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) FilmyGold else HairlineCyan
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectVoice(profile) }
                            .testTag("voice_option_${profile.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = IceWhite
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (selected) FilmyGold.copy(alpha = 0.2f) else ArcCyan.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = profile.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) FilmyGold else ArcCyan,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = profile.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MistSlate
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected voice",
                                    tint = FilmyGold
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "PERSONA & FILMY STYLE",
                style = MaterialTheme.typography.labelLarge,
                color = MistSlate
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MeroPersonaStyle.entries.forEach { style ->
                    val selected = style == preferences.personaStyle
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectPersona(style) },
                        label = { Text(style.title) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ObsidianVoid,
                            labelColor = MistSlate,
                            selectedContainerColor = FilmyGold.copy(alpha = 0.18f),
                            selectedLabelColor = FilmyGold,
                            selectedLeadingIconColor = FilmyGold
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = HairlineCyan,
                            selectedBorderColor = HairlineGold
                        ),
                        modifier = Modifier.testTag("persona_chip_${style.name.lowercase()}")
                    )
                }
            }

            HorizontalDivider(color = HairlineCyan)

            PreferenceSwitchRow(
                title = "Spoken Voice Replies",
                subtitle = "Synthesize Mero's cinematic female audio via Gemini TTS",
                checked = preferences.spokenReplies,
                onCheckedChange = onToggleSpokenReplies,
                testTag = "switch_spoken_replies"
            )

            PreferenceSwitchRow(
                title = "Auto-Send on Pause (VAD)",
                subtitle = "Automatically send your voice once you finish speaking",
                checked = preferences.autoSilenceStop,
                onCheckedChange = onToggleAutoSilence,
                testTag = "switch_auto_silence"
            )

            PreferenceSwitchRow(
                title = "Hands-Free Jarvis Loop",
                subtitle = "Automatically listen again after Mero finishes speaking",
                checked = preferences.handsFreeLoop,
                onCheckedChange = onToggleHandsFree,
                testTag = "switch_hands_free_loop"
            )
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = IceWhite
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MistSlate
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianVoid,
                checkedTrackColor = ArcCyan,
                uncheckedThumbColor = MistSlate,
                uncheckedTrackColor = ObsidianElevated
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
