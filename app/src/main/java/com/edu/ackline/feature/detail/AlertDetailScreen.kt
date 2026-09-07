package com.edu.ackline.feature.detail

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.edu.ackline.model.AlertLevel
import com.edu.ackline.ui.AcklineTopBar
import com.edu.ackline.ui.SeverityChip
import com.edu.ackline.ui.severityColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AlertDetailScreen(
    notificationId: String,
    onBack: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: AlertDetailViewModel = viewModel(
        key = "alert-detail-$notificationId",
        factory = AlertDetailViewModelFactory(application, notificationId),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcklineTopBar(title = "Detalle de alerta", onBack = onBack) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            when (val current = uiState) {
                is DetailUiState.Loading -> {
                    // Initial loading — do not show error message
                }
                is DetailUiState.NotFound -> {
                    Text(
                        text = "Esta alerta ya no está disponible.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is DetailUiState.Found -> {
                    AlertDetailContent(
                        level = current.alert.level,
                        title = current.alert.title,
                        message = current.alert.message,
                        createdAt = current.alert.createdAt,
                        receivedAt = current.alert.receivedAt,
                        isPending = current.alert.acknowledgedAt == null,
                        onAcknowledge = viewModel::acknowledge,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertDetailContent(
    level: AlertLevel,
    title: String,
    message: String,
    createdAt: Instant,
    receivedAt: Instant,
    isPending: Boolean,
    onAcknowledge: () -> Unit,
) {
    // Main alert panel — alert content dominates; metadata stays quiet.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityChip(level = level)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (isPending) "Pendiente" else "Vista",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
            )

            Spacer(modifier = Modifier.height(16.dp))
            // Severity-toned divider: restrained identity accent for the
            // primary panel without saturating the screen in teal.
            HorizontalDivider(
                color = lerp(
                    MaterialTheme.colorScheme.outlineVariant,
                    severityColor(level),
                    0.55f,
                ),
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 26.sp,
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Secondary metadata area — recessed inset, clearly quieter.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    DetailTimestamp(
                        label = "Creada",
                        instant = createdAt,
                        modifier = Modifier.weight(1f),
                    )
                    DetailTimestamp(
                        label = "Recibida",
                        instant = receivedAt,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // ACK action area — pending cards only; local ACK + eventual remote ACK
    // semantics are unchanged (callback wiring identical to previous version).
    // Complete semantic pair secondaryContainer/onSecondaryContainer for
    // >=4.5:1 normal-text contrast in both themes; effective height >= 48dp.
    if (isPending) {
        Spacer(modifier = Modifier.height(18.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable(
                    role = Role.Button,
                    onClick = onAcknowledge,
                ),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = "Marcar como vista",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 13.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    } else {
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Alerta vista",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun DetailTimestamp(
    label: String,
    instant: Instant,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = formatDetailTime(instant),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val detailTimeFormatter = DateTimeFormatter.ofPattern(
    "d MMMM yyyy · HH:mm",
    Locale.forLanguageTag("es-MX"),
)

private fun formatDetailTime(instant: Instant): String =
    detailTimeFormatter
        .withZone(ZoneId.systemDefault())
        .format(instant)
