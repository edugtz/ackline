package com.edu.ackline.feature.inbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.edu.ackline.AcklineApplication
import com.edu.ackline.ack.LocalAcknowledgmentManager
import com.edu.ackline.data.AlertRepository
import com.edu.ackline.model.Alert
import java.time.Instant
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class InboxFilter {
    PENDING,
    VIEWED,
}

data class InboxUiState(
    val pendingAlerts: List<Alert> = emptyList(),
    val viewedAlerts: List<Alert> = emptyList(),
    val filter: InboxFilter = InboxFilter.PENDING,
) {
    val visibleAlerts: List<Alert>
        get() = when (filter) {
            InboxFilter.PENDING -> pendingAlerts
            InboxFilter.VIEWED -> viewedAlerts
        }
}

class InboxViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "InboxViewModel"
    }

    private val repository: AlertRepository =
        (application as AcklineApplication).alertRepository
    private val localAcknowledgmentManager: LocalAcknowledgmentManager =
        (application as AcklineApplication).localAcknowledgmentManager
    private val acknowledgmentScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val selectedFilter = MutableStateFlow(InboxFilter.PENDING)
    val filter: StateFlow<InboxFilter> = selectedFilter.asStateFlow()

    val uiState: Flow<InboxUiState> = combine(
        repository.observePending(),
        repository.observeViewed(),
        selectedFilter,
    ) { pendingAlerts, viewedAlerts, filter ->
        InboxUiState(
            pendingAlerts = pendingAlerts,
            viewedAlerts = viewedAlerts,
            filter = filter,
        )
    }

    fun selectFilter(filter: InboxFilter) {
        selectedFilter.value = filter
    }

    fun acknowledge(notificationId: String) {
        acknowledgmentScope.launch {
            try {
                localAcknowledgmentManager.acknowledge(
                    notificationId = notificationId,
                    acknowledgedAt = Instant.now(),
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to acknowledge alert", exception)
            }
        }
    }

    override fun onCleared() {
        acknowledgmentScope.cancel()
        super.onCleared()
    }
}
