package com.edu.ackline.feature.inbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.edu.ackline.model.Alert
import com.edu.ackline.model.AlertLevel
import com.edu.ackline.ui.SegmentedFilter
import com.edu.ackline.ui.SegmentedOption
import com.edu.ackline.ui.SeverityChip
import com.edu.ackline.ui.SeverityEmphasis
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
                viewedCount = uiState.viewedAlerts.size,
                selectedFilter = uiState.filter,
                onFilterSelected = viewModel::selectFilter,
                onSetupClick = onSetupClick,
            )
        },
    ) { contentPadding ->
        if (uiState.visibleAlerts.isEmpty()) {
            EmptyInbox(
                filter = uiState.filter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 24.dp,
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

@Composable
private fun InboxHeader(
    pendingCount: Int,
    viewedCount: Int,
    selectedFilter: InboxFilter,
    onFilterSelected: (InboxFilter) -> Unit,
    onSetupClick: () -> Unit,
) {
    // Layered header band: sits visibly above the screen background and
    // carries the title hierarchy + the segmented filter as one composed unit.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PERSONAL ADMIN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Mis alertas",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    )
                    Text(
                        text = pendingCountLabel(pendingCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Compact outlined "Ajustes" pill. The visual stays small;
                // minimumInteractiveComponentSize guarantees a >= 48dp
                // effective touch target (accessibility M2 fix).
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(
                            onClickLabel = "Abrir configuración",
                            role = Role.Button,
                            onClick = onSetupClick,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Text(
                            text = "Ajustes",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 7.dp,
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SegmentedFilter(
                options = listOf(
                    SegmentedOption(label = pendingTabLabel(pendingCount)),
                    SegmentedOption(label = viewedTabLabel(viewedCount)),
                ),
                selectedIndex = if (selectedFilter == InboxFilter.PENDING) 0 else 1,
                onOptionSelected = { index ->
                    onFilterSelected(
                        if (index == 0) InboxFilter.PENDING else InboxFilter.VIEWED,
                    )
                },
            )
        }
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
    val emphasis = if (isPending) SeverityEmphasis.Full else SeverityEmphasis.Muted

    // Pending cards are visibly raised over viewed/quiet cards.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isPending) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
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
                    SeverityChip(
                        level = alert.level,
                        emphasis = emphasis,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formatInboxRelativeTime(
                            createdAt = alert.createdAt,
                            now = now,
                            zoneId = zoneId,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isPending) FontWeight.SemiBold else FontWeight.Medium,
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatInboxDateTime(alert.createdAt, zoneId),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isPending) {
                    VistoPill(onClick = onAcknowledge)
                } else {
                    Text(
                        text = "Vista",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Compact acknowledgment pill — the card-level "Visto" action.
 * Same callback semantics as before; only the visual weight changed.
 * Quiet surface-toned pill with a complete high-contrast semantic ink
 * (surfaceContainerHigh/onSurface) for >=4.5:1 normal-text contrast in
 * both themes; hairline border keeps it quiet and clearly actionable
 * without competing with the selected segmented control.
 * Visual geometry stays compact while the hit area is enforced to
 * >= 48dp via minimumInteractiveComponentSize.
 */
@Composable
private fun VistoPill(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .semantics {
                contentDescription = "Marcar esta alerta como vista"
            }
            .clickable(
                onClickLabel = "Marcar como vista",
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Text(
                text = "Visto",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun EmptyInbox(
    filter: InboxFilter,
    modifier: Modifier = Modifier,
) {
    // Restrained centered composition: small teal-tonal check accent drawn
    // with existing Compose primitives (no new assets) + concise copy.
    // Vertically scrollable so extreme font scale / short viewports cannot
    // silently clip content; centered when content fits.
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val checkInk = MaterialTheme.colorScheme.onSecondaryContainer
                Canvas(modifier = Modifier.size(20.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.20f, size.height * 0.55f)
                        lineTo(size.width * 0.42f, size.height * 0.76f)
                        lineTo(size.width * 0.80f, size.height * 0.28f)
                    }
                    drawPath(
                        path = path,
                        color = checkInk,
                        style = Stroke(
                            width = size.minDimension * 0.10f,
                            cap = StrokeCap.Round,
                        ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (filter == InboxFilter.PENDING) {
                "Todo al día"
            } else {
                "Aún no hay alertas vistas"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (filter == InboxFilter.PENDING) {
                "No tienes alertas pendientes."
            } else {
                "Las alertas que marques como vistas aparecerán aquí."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun pendingCountLabel(count: Int): String = when (count) {
    1 -> "1 pendiente"
    else -> "$count pendientes"
}

internal fun pendingTabLabel(count: Int): String = "Pendientes ($count)"

internal fun viewedTabLabel(count: Int): String = "Vistas ($count)"
