package com.edu.ackline.feature.detail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.edu.ackline.AcklineApplication
import com.edu.ackline.ack.LocalAcknowledgmentManager
import com.edu.ackline.data.AlertRepository
import com.edu.ackline.model.Alert
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Found(val alert: Alert) : DetailUiState
    data object NotFound : DetailUiState
}

class AlertDetailViewModel(
    application: Application,
    private val notificationId: String,
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AlertDetailViewModel"
    }

    private val acklineApplication = application as AcklineApplication
    private val repository: AlertRepository = acklineApplication.alertRepository
    private val localAcknowledgmentManager: LocalAcknowledgmentManager =
        acklineApplication.localAcknowledgmentManager
    private val detailScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val uiState: StateFlow<DetailUiState> = repository.observeById(notificationId)
        .map { alert ->
            if (alert != null) DetailUiState.Found(alert) else DetailUiState.NotFound
        }
        .stateIn(
            scope = detailScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = DetailUiState.Loading,
        )

    fun acknowledge() {
        detailScope.launch {
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
        detailScope.cancel()
        super.onCleared()
    }
}

class AlertDetailViewModelFactory(
    private val application: Application,
    private val notificationId: String,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(AlertDetailViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return AlertDetailViewModel(application, notificationId) as T
    }
}
