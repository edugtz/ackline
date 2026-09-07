package com.edu.ackline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class SegmentedOption(
    val label: String,
    val selectedContentDescription: String? = null,
)

/**
 * Ackline rounded segmented control (Phase 9 teal visual direction).
 *
 * One shared rounded track; the selected segment is a visibly filled
 * teal-tonal pill, inactive segments stay quiet. Replaces bare Material
 * TabRow underlines. Each segment outer selectable is explicitly 48dp tall
 * (full track height); the visual pill inside stays compact at 42dp so the
 * track hairline remains visible. No overlapping clickables.
 */
@Composable
fun SegmentedFilter(
    options: List<SegmentedOption>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outerShape = RoundedCornerShape(14.dp)
    val innerShape = RoundedCornerShape(11.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, outerShape)
            .padding(horizontal = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            Segment(
                option = option,
                selected = index == selectedIndex,
                shape = innerShape,
                onClick = { onOptionSelected(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RowScope.Segment(
    option: SegmentedOption,
    selected: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val content = if (selected) {
        // Teal-tonal selection reads as accent text on the filled pill,
        // consistent with the V2 reference in both themes.
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(container, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
