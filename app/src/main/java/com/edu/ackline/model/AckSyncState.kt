package com.edu.ackline.model

enum class AckSyncState(val storageValue: String) {
    NONE("none"),
    PENDING("pending");

    companion object {
        fun fromStorageValue(value: String): AckSyncState =
            entries.firstOrNull { it.storageValue == value }
                ?: error("Unknown acknowledgment sync state: $value")
    }
}
