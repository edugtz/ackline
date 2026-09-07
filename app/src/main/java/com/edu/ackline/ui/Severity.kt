package com.edu.ackline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edu.ackline.model.AlertLevel

// Shared severity language for Inbox + Detail (Phase 9 teal visual direction).
// Severity is always communicated as label + color duotone, never color alone.
//
// Families (distinct but restrained, in both themes):
//   Recordatorio -> neutral slate (onSurfaceVariant / outlineVariant)
//   Importante   -> blue-green tertiary family, deliberately NOT the mint
//                    primary used by global actions (no identical look)
//   Urgente      -> muted coral error family

internal enum class SeverityEmphasis { Full, Muted }

@Composable
internal fun severityColor(
    level: AlertLevel,
    emphasis: SeverityEmphasis = SeverityEmphasis.Full,
): Color {
    val full = when (level) {
        AlertLevel.REMEMBER -> MaterialTheme.colorScheme.onSurfaceVariant
        AlertLevel.IMPORTANT -> MaterialTheme.colorScheme.tertiary
        AlertLevel.URGENT -> MaterialTheme.colorScheme.error
    }
    return when (emphasis) {
        SeverityEmphasis.Full -> full
        // Viewed items keep the same hue identity but recede in weight.
        SeverityEmphasis.Muted -> lerp(
            MaterialTheme.colorScheme.surfaceContainer,
            full,
            0.62f,
        )
    }
}

@Composable
internal fun severityContainerColor(
    level: AlertLevel,
    emphasis: SeverityEmphasis = SeverityEmphasis.Full,
): Color {
    val full = when (level) {
        AlertLevel.REMEMBER -> MaterialTheme.colorScheme.outlineVariant
        AlertLevel.IMPORTANT -> MaterialTheme.colorScheme.tertiaryContainer
        AlertLevel.URGENT -> MaterialTheme.colorScheme.errorContainer
    }
    return when (emphasis) {
        SeverityEmphasis.Full -> full
        SeverityEmphasis.Muted -> lerp(
            MaterialTheme.colorScheme.surfaceContainer,
            full,
            0.45f,
        )
    }
}

internal fun severityLabel(level: AlertLevel): String = when (level) {
    AlertLevel.REMEMBER -> "Recordatorio"
    AlertLevel.IMPORTANT -> "Importante"
    AlertLevel.URGENT -> "Urgente"
}

/**
 * Compact severity chip: leading dot + label + tonal container.
 * Matches the reference badge treatment (label + color, never color alone).
 */
@Composable
internal fun SeverityChip(
    level: AlertLevel,
    modifier: Modifier = Modifier,
    emphasis: SeverityEmphasis = SeverityEmphasis.Full,
) {
    val contentColorValue = severityColor(level, emphasis)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = severityContainerColor(level, emphasis),
        contentColor = contentColorValue,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
            Canvas(modifier = Modifier.size(6.dp)) {
                drawCircle(color = contentColorValue)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = severityLabel(level),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
            )
        }
    }
}
