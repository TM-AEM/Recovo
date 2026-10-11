package com.tmaem.recovo.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tmaem.recovo.core.designsystem.theme.RecovoSpacing

/**
 * Stateless semantic status/feedback card shared by RecordScreen banners.
 *
 * Reuses Phase 33A theme tokens and the Phase 33B semantic foreground/background
 * pairs. It only supports the layout used by the migrated call sites: a centered
 * column with an optional icon + title row, a body line, and an optional action
 * row. Callers with a different layout (e.g. a horizontal banner or a card with
 * extra controls between the title and body) should not use this component.
 *
 * @param title bold title line.
 * @param body supporting text shown beneath the title.
 * @param containerColor card background (a semantic container role).
 * @param contentColor foreground for the title/body and default icon tint.
 * @param icon optional leading icon in the title row.
 * @param iconSize size of [icon]; ignored when [icon] is null.
 * @param titleToBodySpacing vertical spacing between title row and body.
 * @param bodyToActionSpacing vertical spacing inserted before [action], if any.
 * @param horizontalAlignment alignment of the card's inner column.
 * @param action optional trailing content (typically a button).
 */
@Composable
fun RecovoStatusCard(
    title: String,
    body: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSize: Dp = 24.dp,
    titleToBodySpacing: Dp = 4.dp,
    bodyToActionSpacing: Dp = RecovoSpacing.small,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    action: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(RecovoSpacing.medium),
            horizontalAlignment = horizontalAlignment,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(iconSize),
                    )
                    Spacer(modifier = Modifier.width(RecovoSpacing.small))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            }
            Spacer(modifier = Modifier.height(titleToBodySpacing))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(bodyToActionSpacing))
                action()
            }
        }
    }
}
