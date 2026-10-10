package com.example.feature.record

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.designsystem.theme.RecovoSpacing
import com.example.core.engine.RecordingQuality

/**
 * Presentation-only mapping from the [RecordingQuality] domain enum to localized
 * string resources. Keeps the domain enum free of Android/Compose dependencies.
 */
private data class QualityStrings(
    @StringRes val shortLabel: Int,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    @StringRes val description: Int,
)

private fun RecordingQuality.localizedStrings(): QualityStrings = when (this) {
    RecordingQuality.STANDARD -> QualityStrings(
        shortLabel = R.string.quality_standard_short,
        title = R.string.quality_standard_title,
        subtitle = R.string.quality_standard_subtitle,
        description = R.string.quality_standard_description,
    )
    RecordingQuality.HIGH -> QualityStrings(
        shortLabel = R.string.quality_high_short,
        title = R.string.quality_high_title,
        subtitle = R.string.quality_high_subtitle,
        description = R.string.quality_high_description,
    )
    RecordingQuality.MAXIMUM -> QualityStrings(
        shortLabel = R.string.quality_maximum_short,
        title = R.string.quality_maximum_title,
        subtitle = R.string.quality_maximum_subtitle,
        description = R.string.quality_maximum_description,
    )
}

/**
 * Quality selection UI for Record Screen.
 * Allows choosing Standard, High, or Maximum quality before recording.
 * When recording is active, shows a locked indicator chip.
 */
@Composable
fun RecordingQualityCard(
    selectedQuality: RecordingQuality,
    isRecordingActive: Boolean,
    onSelectQuality: (RecordingQuality) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isRecordingActive) {
        // Locked state display during recording
        val qualityStrings = selectedQuality.localizedStrings()
        val title = stringResource(qualityStrings.title)
        val subtitle = stringResource(qualityStrings.subtitle)
        Surface(
            modifier = modifier.testTag("active_quality_badge"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = RecovoSpacing.medium, vertical = RecovoSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.HighQuality,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(RecovoSpacing.small))
                Text(
                    text = "$title ($subtitle)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        // Interactive preset selector before recording starts
        val selectedDescription = stringResource(selectedQuality.localizedStrings().description)
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("recording_quality_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(RecovoSpacing.medium)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(RecovoSpacing.small))
                    Text(
                        text = stringResource(R.string.quality_card_heading),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(RecovoSpacing.small))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(RecovoSpacing.small)
                ) {
                    RecordingQuality.entries.forEach { quality ->
                        val isSelected = quality == selectedQuality
                        val tagId = "quality_preset_${quality.id}"

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { onSelectQuality(quality) }
                                )
                                .testTag(tagId),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = RecovoSpacing.small, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                    }
                                    Text(
                                        text = stringResource(quality.localizedStrings().shortLabel),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.quality_bitrate_kbps, quality.bitRate / 1000),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(RecovoSpacing.small))

                Text(
                    text = selectedDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
