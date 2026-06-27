package org.qosp.notes.data.sync.core

/**
 * Represents the availability status of a sync backend.
 * Used to check if a backend is reachable before performing sync operations.
 */
sealed class AvailabilityStatus {
    /** Backend is available and ready for sync operations */
    object Available : AvailabilityStatus()

    /** Backend is unavailable - includes a reason message for display to the user */
    data class Unavailable(val reason: String) : AvailabilityStatus()
}
