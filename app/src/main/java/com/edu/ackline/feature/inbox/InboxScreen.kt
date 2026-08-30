package com.edu.ackline.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun InboxScreen(
    onAlertClick: (String) -> Unit,
    onSetupClick: () -> Unit,
) {
    val viewModel: InboxViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(
        initialValue = InboxUiState(),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PERSONAL ADMIN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "Inbox",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                TextButton(onClick = onSetupClick) {
                    Text("Setup")
                }
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            Text(
                text = pendingCountLabel(uiState.pendingAlerts.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            InboxFilterBar(
                selectedFilter = uiState.filter,
                onFilterSelected = viewModel::selectFilter,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (uiState.visibleAlerts.isEmpty()) {
                EmptyInbox(
                    filter = uiState.filter,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                ) {
                    items(
                        items = uiState.visibleAlerts,
                        key = { it.notificationId },
                    ) { alert ->
                        AlertRow(
                            alert = alert,
                            onClick = { onAlertClick(alert.notificationId) },
                            onAcknowledge = { viewModel.acknowledge(alert.notificationId) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 24.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxFilterBar(
    selectedFilter: InboxFilter,
    onFilterSelected: (InboxFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        InboxFilterTab(
            label = "Pendientes",
            selected = selectedFilter == InboxFilter.PENDING,
            onClick = { onFilterSelected(InboxFilter.PENDING) },
            modifier = Modifier.weight(1f),
        )
        InboxFilterTab(
            label = "Vistas",
            selected = selectedFilter == InboxFilter.VIEWED,
            onClick = { onFilterSelected(InboxFilter.VIEWED) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InboxFilterTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                ),
        )
    }
}

@Composable
private fun AlertRow(
    alert: Alert,
    onClick: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
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
                SeverityDot(level = alert.level)
                Spacer(modifier = Modifier.size(9.dp))
                Text(
                    text = severityLabel(alert.level),
                    style = MaterialTheme.typography.labelSmall,
                    color = severityColor(alert.level),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (alert.acknowledgedAt == null) "Pendiente" else "Vista",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = alert.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatAlertTime(alert.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        if (alert.acknowledgedAt == null) {
            TextButton(
                onClick = onAcknowledge,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Visto")
            }
        }
    }
}

@Composable
private fun SeverityDot(level: AlertLevel) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(severityColor(level)),
    )
}

@Composable
private fun severityColor(level: AlertLevel) = when (level) {
    AlertLevel.REMEMBER -> MaterialTheme.colorScheme.outline
    AlertLevel.IMPORTANT -> MaterialTheme.colorScheme.primary
    AlertLevel.URGENT -> MaterialTheme.colorScheme.error
}

private fun severityLabel(level: AlertLevel) = when (level) {
    AlertLevel.REMEMBER -> "RECORDATORIO"
    AlertLevel.IMPORTANT -> "IMPORTANTE"
    AlertLevel.URGENT -> "URGENTE"
}

@Composable
private fun EmptyInbox(
    filter: InboxFilter,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp),
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
                "Las alertas pendientes aparecerán aquí hasta que se vean."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun pendingCountLabel(count: Int): String = when (count) {
    1 -> "1 pendiente"
    else -> "$count pendientes"
}

private val alertTimeFormatter = DateTimeFormatter.ofPattern(
    "d MMM · HH:mm",
    Locale.forLanguageTag("es-MX"),
)

private fun formatAlertTime(instant: Instant): String =
    alertTimeFormatter
        .withZone(ZoneId.systemDefault())
        .format(instant)
