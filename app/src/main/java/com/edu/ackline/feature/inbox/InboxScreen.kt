package com.edu.ackline.feature.inbox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.edu.ackline.model.Alert
import com.edu.ackline.model.AlertLevel
import java.time.Instant
import java.time.ZoneId

@Composable
fun InboxScreen(
    onAlertClick: (String) -> Unit,
    onSetupClick: () -> Unit,
) {
    val viewModel: InboxViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(
        initialValue = InboxUiState(),
    )
    val now = Instant.now()
    val zoneId = ZoneId.systemDefault()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            InboxHeader(
                pendingCount = uiState.pendingAlerts.size,
                onSetupClick = onSetupClick,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            InboxFilterBar(
                selectedFilter = uiState.filter,
                pendingCount = uiState.pendingAlerts.size,
                viewedCount = uiState.viewedAlerts.size,
                onFilterSelected = viewModel::selectFilter,
            )

            if (uiState.visibleAlerts.isEmpty()) {
                EmptyInbox(
                    filter = uiState.filter,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = uiState.visibleAlerts,
                        key = { it.notificationId },
                    ) { alert ->
                        AlertCard(
                            alert = alert,
                            now = now,
                            zoneId = zoneId,
                            onClick = { onAlertClick(alert.notificationId) },
                            onAcknowledge = {
                                viewModel.acknowledge(alert.notificationId)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxHeader(
    pendingCount: Int,
    onSetupClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PERSONAL ADMIN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.84f),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Mis alertas",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = pendingCountLabel(pendingCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onSetupClick,
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text("Ajustes")
        }
    }
}

@Composable
private fun InboxFilterBar(
    selectedFilter: InboxFilter,
    pendingCount: Int,
    viewedCount: Int,
    onFilterSelected: (InboxFilter) -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = if (selectedFilter == InboxFilter.PENDING) 0 else 1,
        modifier = Modifier.padding(horizontal = 12.dp),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Tab(
            selected = selectedFilter == InboxFilter.PENDING,
            onClick = { onFilterSelected(InboxFilter.PENDING) },
            text = {
                Text(
                    text = pendingTabLabel(pendingCount),
                    color = if (selectedFilter == InboxFilter.PENDING) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (selectedFilter == InboxFilter.PENDING) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Medium
                    },
                    letterSpacing = 0.2.sp,
                )
            },
        )
        Tab(
            selected = selectedFilter == InboxFilter.VIEWED,
            onClick = { onFilterSelected(InboxFilter.VIEWED) },
            text = {
                Text(
                    text = viewedTabLabel(viewedCount),
                    color = if (selectedFilter == InboxFilter.VIEWED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (selectedFilter == InboxFilter.VIEWED) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Medium
                    },
                    letterSpacing = 0.2.sp,
                )
            },
        )
    }
}

@Composable
private fun AlertCard(
    alert: Alert,
    now: Instant,
    zoneId: ZoneId,
    onClick: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    val isPending = alert.acknowledgedAt == null
    val containerColor = if (isPending) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val titleColor = if (isPending) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val messageColor = if (isPending) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeverityBadge(
                        level = alert.level,
                        isPending = isPending,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formatInboxRelativeTime(
                            createdAt = alert.createdAt,
                            now = now,
                            zoneId = zoneId,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(9.dp))

                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    fontWeight = if (isPending) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = messageColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatInboxDateTime(alert.createdAt, zoneId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isPending) {
                    AcknowledgeAction(onClick = onAcknowledge)
                } else {
                    Text(
                        text = "Vista",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AcknowledgeAction(
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minHeight = 36.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                alpha = 0.85f,
            ),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        CheckGlyph(
            color = LocalContentColor.current,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Marcar vista",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CheckGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.14f
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height * 0.55f),
            end = Offset(size.width * 0.40f, size.height * 0.76f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.40f, size.height * 0.76f),
            end = Offset(size.width * 0.84f, size.height * 0.26f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SeverityBadge(
    level: AlertLevel,
    isPending: Boolean,
) {
    Surface(
        color = severityContainerColor(level, isPending),
        contentColor = severityColor(level, isPending),
        shape = RoundedCornerShape(7.dp),
    ) {
        Text(
            text = severityLabel(level),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun severityColor(
    level: AlertLevel,
    isPending: Boolean,
): Color {
    val alpha = if (isPending) 1f else 0.72f
    return when (level) {
        AlertLevel.REMEMBER -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
        AlertLevel.IMPORTANT -> MaterialTheme.colorScheme.primary.copy(alpha = alpha)
        AlertLevel.URGENT -> MaterialTheme.colorScheme.error.copy(alpha = alpha)
    }
}

@Composable
private fun severityContainerColor(
    level: AlertLevel,
    isPending: Boolean,
): Color {
    val alpha = if (isPending) {
        when (level) {
            AlertLevel.REMEMBER -> 0.72f
            AlertLevel.IMPORTANT -> 0.13f
            AlertLevel.URGENT -> 0.12f
        }
    } else {
        when (level) {
            AlertLevel.REMEMBER -> 0.4f
            AlertLevel.IMPORTANT -> 0.07f
            AlertLevel.URGENT -> 0.06f
        }
    }

    return when (level) {
        AlertLevel.REMEMBER -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
        AlertLevel.IMPORTANT -> MaterialTheme.colorScheme.primary.copy(alpha = alpha)
        AlertLevel.URGENT -> MaterialTheme.colorScheme.error.copy(alpha = alpha)
    }
}

private fun severityLabel(level: AlertLevel) = when (level) {
    AlertLevel.REMEMBER -> "Recordatorio"
    AlertLevel.IMPORTANT -> "Importante"
    AlertLevel.URGENT -> "Urgente"
}

@Composable
private fun EmptyInbox(
    filter: InboxFilter,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (filter == InboxFilter.PENDING) {
                "No hay alertas pendientes"
            } else {
                "Aún no hay alertas vistas"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (filter == InboxFilter.PENDING) {
                "Las alertas nuevas aparecerán aquí."
            } else {
                "Las alertas pendientes aparecerán aquí hasta que las marques como vistas."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun pendingCountLabel(count: Int): String = when (count) {
    1 -> "1 pendiente"
    else -> "$count pendientes"
}

internal fun pendingTabLabel(count: Int): String = "Pendientes ($count)"

internal fun viewedTabLabel(count: Int): String = "Vistas ($count)"
