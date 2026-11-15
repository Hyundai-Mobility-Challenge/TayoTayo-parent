package com.hanhyo.shuttle.parent

import org.json.JSONArray
import org.json.JSONObject

internal const val DEFAULT_SERVER_URL = "https://api-demo-e677ea0e.up.railway.app"

/** Immutable snapshots. JSON values are compatibility adapters for the existing form UI. */
data class ParentUiState(
    val base: String = DEFAULT_SERVER_URL,
    val loggedIn: Boolean = false,
    val busy: Boolean = false,
    val foreground: Boolean = false,
    val message: String = "",
    val pushStatus: String = "알림 연결을 확인해 주세요.",
    val connections: JSONObject = JSONObject().put("children", JSONArray()).put("events", JSONArray()),
    val tracking: Map<String, JSONObject> = emptyMap(),
    val morningTracking: Map<String, JSONObject> = emptyMap(),
    val afternoonTracking: Map<String, JSONObject> = emptyMap(),
    val directions: Map<String, SchoolDirection> = emptyMap(),
    val childTools: Map<String, JSONObject> = emptyMap(),
    val notificationTarget: NotificationTarget? = null,
    val notificationData: JSONObject? = null,
    val notificationChildName: String = "",
    val notificationError: String = ""
)

sealed interface ParentIntent {
    data class Authenticate(val join: Boolean, val server: String, val name: String, val email: String,
        val password: String, val verificationCode: String) : ParentIntent
    data class AcceptInvitation(val code: String) : ParentIntent
    data object Refresh : ParentIntent
    data object RefreshTracking : ParentIntent
    data class Foreground(val active: Boolean) : ParentIntent
    data class LoadTools(val childId: String, val date: String) : ParentIntent
    data class Attendance(val childId: String, val date: String, val direction: String, val type: String,
        val reason: String, val done: () -> Unit) : ParentIntent
    data class Pickup(val childId: String, val date: String, val direction: String,
        val personId: String?, val done: () -> Unit) : ParentIntent
    data class RegisterPerson(val childId: String, val name: String, val relationship: String) : ParentIntent
    data class Share(val childId: String, val tripId: String, val onLink: (String) -> Unit) : ParentIntent
    data class RevokeShare(val childId: String, val tripId: String) : ParentIntent
    data class OpenNotification(val target: NotificationTarget) : ParentIntent
    data object CloseNotification : ParentIntent
    data object Logout : ParentIntent
}
