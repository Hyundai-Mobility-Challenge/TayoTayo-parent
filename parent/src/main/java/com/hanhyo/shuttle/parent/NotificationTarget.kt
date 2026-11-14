package com.hanhyo.shuttle.parent

/** IDs are only routing hints; the authenticated guardian API must authorize every read. */
data class NotificationTarget(val childId: String, val tripId: String) {
    companion object {
        fun from(childId: String?, tripId: String?): NotificationTarget? {
            fun valid(value: String?) = value?.toLongOrNull()?.let { it > 0 } == true
            return if (valid(childId) && valid(tripId)) NotificationTarget(childId!!, tripId!!) else null
        }
    }
}
