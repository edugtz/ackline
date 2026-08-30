package com.edu.ackline.feature.detail

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("Volver")
                }
                Text(
                    text = "Detalle",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            when (val current = uiState) {
                is DetailUiState.Loading -> {
                    // Initial loading — do not show error message
                }
                is DetailUiState.NotFound -> {
                    Text(
                        text = "Esta alerta ya no está disponible.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                is DetailUiState.Found -> {
                    val currentAlert = current.alert
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SeverityMark(level = currentAlert.level)
                        Text(
                            text = severityLabel(currentAlert.level),
                            style = MaterialTheme.typography.labelMedium,
                            color = severityColor(currentAlert.level),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = if (currentAlert.acknowledgedAt == null) "Pendiente" else "Vista",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = currentAlert.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = currentAlert.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    DetailTimestamp(label = "Creada", instant = currentAlert.createdAt)
                    Spacer(modifier = Modifier.height(14.dp))
                    DetailTimestamp(label = "Recibida", instant = currentAlert.receivedAt)

                    if (currentAlert.acknowledgedAt == null) {
                        Spacer(modifier = Modifier.height(28.dp))
                        TextButton(onClick = viewModel::acknowledge) {
                            Text("Marcar como visto")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeverityMark(level: AlertLevel) {
    Box(
        modifier = Modifier
            .size(width = 4.dp, height = 18.dp)
            .background(severityColor(level)),
    )
}

@Composable
private fun DetailTimestamp(label: String, instant: Instant) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatDetailTime(instant),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
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

private val detailTimeFormatter = DateTimeFormatter.ofPattern(
    "d MMMM yyyy · HH:mm",
    Locale.forLanguageTag("es-MX"),
)

private fun formatDetailTime(instant: Instant): String =
    detailTimeFormatter
        .withZone(ZoneId.systemDefault())
        .format(instant)
