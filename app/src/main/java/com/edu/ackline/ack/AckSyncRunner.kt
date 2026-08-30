package com.edu.ackline.ack

class AckSyncRunner(
    private val store: AckSyncStore,
    private val remoteClient: AckRemoteClient,
    private val syncedAtEpochMillis: () -> Long = System::currentTimeMillis,
) {

    fun run(): AckSyncRunResult {
        var shouldRetry = false

        store.findPendingAcknowledgments().forEach { pendingAcknowledgment ->
            val ackToken = pendingAcknowledgment.ackToken?.takeIf { it.isNotBlank() }
            if (ackToken == null) {
                store.markAckError(
                    notificationId = pendingAcknowledgment.notificationId,
                    errorCategory = AckErrorCategory.MISSING_ACK_TOKEN,
                )
                return@forEach
            }

            when (val remoteResult =
                remoteClient.acknowledge(pendingAcknowledgment.notificationId, ackToken)
            ) {
                AckRemoteResult.Success -> {
                    store.markAckSynced(
                        notificationId = pendingAcknowledgment.notificationId,
                        syncedAtEpochMillis = syncedAtEpochMillis(),
                    )
                }

                AckRemoteResult.NotConfigured -> Unit

                AckRemoteResult.TransientFailure -> {
                    shouldRetry = true
                }

                is AckRemoteResult.PermanentFailure -> {
                    store.markAckError(
                        notificationId = pendingAcknowledgment.notificationId,
                        errorCategory = AckErrorCategory.sanitize(
                            remoteResult.category,
                        ),
                    )
                }
            }
        }

        return AckSyncRunResult(shouldRetry = shouldRetry)
    }
}

data class AckSyncRunResult(
    val shouldRetry: Boolean,
)
