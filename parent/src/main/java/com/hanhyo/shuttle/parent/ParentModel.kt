package com.hanhyo.shuttle.parent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.hanhyo.shuttle.net.Api
import com.hanhyo.shuttle.net.ApiFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Adapter for the TayoTayo /api/v1 guardian API. */
class ParentModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Push.prefs(app)
    var base: String = prefs.getString("base", "http://192.168.33.128:8080")!!; private set
    var loggedIn: Boolean by mutableStateOf(prefs.contains("token")); private set
    var busy by mutableStateOf(false); private set
    var foreground by mutableStateOf(false)
    var message by mutableStateOf(""); private set
    var pushStatus by mutableStateOf(prefs.getString("pushStatus", "알림 연결을 확인해 주세요.")!!); private set
    var state by mutableStateOf(JSONObject().put("children", JSONArray()).put("events", JSONArray())); private set
    val tracking = mutableStateMapOf<String, JSONObject>()
    val childTools = mutableStateMapOf<String, JSONObject>()

    private fun request(path: String, data: JSONObject? = null, method: String? = null): JSONObject {
        val token = prefs.getString("token", "") ?: ""
        return try { Api.request(base, path, token, data, method) }
        catch (e: ApiFailure) {
            if (e.status != 401) throw e
            val refresh = prefs.getString("refreshToken", "") ?: ""
            if (refresh.isBlank()) { clearSession(); throw e }
            val renewed = try { Api.request(base, "/api/v1/auth/refresh", data = JSONObject().put("refreshToken", refresh)) }
                catch (refreshError: Exception) { clearSession(); throw refreshError }
            prefs.edit().putString("token", renewed.getString("accessToken")).putString("refreshToken", renewed.getString("refreshToken")).commit()
            Api.request(base, path, renewed.getString("accessToken"), data, method)
        }
    }

    private fun work(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: Exception) { message = e.message ?: "서버 요청에 실패했습니다." }
            finally { busy = false }
        }
    }

    fun authenticate(join: Boolean, server: String, name: String, email: String, password: String, verificationCode: String = "") = work {
        base = Api.base(server, true)
        prefs.edit().putString("base", base).apply()
        if (join) {
            if (verificationCode.isBlank()) {
                val sent = Api.request(base, "/api/v1/auth/email-verifications", data = JSONObject().put("email", email).put("purpose", "SIGN_UP"))
                message = "개발용 인증번호: ${sent.optString("debugCode")}. 번호를 입력한 뒤 다시 가입을 눌러 주세요."
            } else {
                Api.request(base, "/api/v1/auth/email-verifications/confirm", data = JSONObject().put("email", email).put("purpose", "SIGN_UP").put("code", verificationCode))
                Api.request(base, "/api/v1/auth/signup", data = JSONObject().put("email", email).put("name", name).put("password", password).put("verificationCode", verificationCode))
                val result = Api.request(base, "/api/v1/auth/login", data = JSONObject().put("email", email).put("password", password).put("clientType", "GUARDIAN_APP"))
                prefs.edit().putString("token", result.getString("accessToken")).putString("refreshToken", result.getString("refreshToken")).apply()
                loggedIn = true; message = "가입 및 로그인했습니다. 기관 초대 코드를 연결해 주세요."; loadChildren(); loadTracking(); Push.register(getApplication())
            }
            return@work
        }
        val result = Api.request(base, "/api/v1/auth/login", data = JSONObject()
            .put("email", email).put("password", password).put("clientType", "GUARDIAN_APP").put("deviceLabel", "Android guardian app"))
        prefs.edit().putString("token", result.getString("accessToken"))
            .putString("refreshToken", result.getString("refreshToken")).apply()
        loggedIn = true
        message = "로그인했습니다. 기관 초대 코드를 연결해 주세요."
        loadChildren()
        loadTracking()
        Push.register(getApplication())
    }

    fun accept(code: String) = work {
        request("/api/v1/guardian-invitations/accept", JSONObject().put("code", code.trim()))
        message = "어린이를 연결했습니다."
        loadChildren()
        loadTracking()
    }

    fun refresh() = work { loadChildren() }

    fun loadChildTools(childId: String) = work { loadChildToolsNow(childId) }

    private fun loadChildToolsNow(childId: String, serviceDate: String = LocalDate.now().toString()) {
        val requests = request("/api/v1/guardian/children/$childId/attendance-requests?limit=20")
        val persons = request("/api/v1/guardian/children/$childId/authorized-pickup-persons")
        val dropOff = request("/api/v1/guardian/children/$childId/daily-pickups?serviceDate=$serviceDate&direction=DROP_OFF")
        val pickUp = request("/api/v1/guardian/children/$childId/daily-pickups?serviceDate=$serviceDate&direction=PICK_UP")
        childTools[childId] = JSONObject()
            .put("requests", requests.optJSONArray("items") ?: JSONArray())
            .put("persons", persons.optJSONArray("items") ?: JSONArray())
            .put("serviceDate", serviceDate).put("dropOff", dropOff).put("pickUp", pickUp)
    }

    fun createAttendanceRequest(childId: String, serviceDate: String, direction: String, requestType: String, reason: String) = work {
        val body = JSONObject().put("serviceDate", serviceDate).put("direction", direction).put("requestType", requestType)
        if (reason.isNotBlank()) body.put("reasonText", reason.trim())
        request("/api/v1/guardian/children/$childId/attendance-requests", body)
        message = "출결·하원 변경 요청을 접수했습니다."
        loadChildToolsNow(childId, serviceDate)
    }

    fun addAuthorizedPickupPerson(childId: String, name: String, relationship: String, validFrom: String, validTo: String) = work {
        val body = JSONObject().put("name", name.trim()).put("relationship", relationship.trim())
        if (validFrom.isNotBlank()) body.put("validFrom", validFrom)
        if (validTo.isNotBlank()) body.put("validTo", validTo)
        request("/api/v1/guardian/children/$childId/authorized-pickup-persons", body)
        message = "대리 픽업 등록을 신청했습니다. 기관 승인 후 지정할 수 있습니다."
        loadChildToolsNow(childId)
    }

    fun setDailyPickup(childId: String, serviceDate: String, direction: String, personType: String, pickupPersonId: String? = null) = work {
        val body = JSONObject().put("serviceDate", serviceDate).put("direction", direction).put("personType", personType)
        if (personType == "AUTHORIZED_PERSON" && pickupPersonId != null) body.put("pickupPersonId", pickupPersonId.toLong())
        request("/api/v1/guardian/children/$childId/daily-pickups", body, "PUT")
        message = "${if (direction == "DROP_OFF") "등원" else "하원"} 픽업 담당자를 저장했습니다."
        loadChildToolsNow(childId, serviceDate)
    }
    private fun loadChildren() {
        val result = request("/api/v1/me/children")
        val items = result.optJSONArray("items") ?: JSONArray()
        val children = JSONArray()
        val events = mutableListOf<JSONObject>()
        val seenChildIds = mutableSetOf<String>()
        for (i in 0 until items.length()) {
            val c = items.getJSONObject(i)
            val childId = c.optString("id")
            if (!seenChildIds.add(childId)) continue
            children.put(JSONObject().put("id", childId).put("name", c.optString("name"))
                .put("className", c.optString("className")).put("institution", c.optString("orgName", "기관"))
                .put("relationship", c.optString("relationship")).put("vehicle", "운행 정보는 오늘 기록에서 확인"))
            val history = request("/api/v1/guardian/children/$childId/trips?direction=DROP_OFF&limit=10").optJSONArray("items") ?: JSONArray()
            for (j in 0 until history.length()) {
                val trip = history.getJSONObject(j)
                val status = trip.optString("childStatus", trip.optString("tripStatus"))
                val body = when (status) { "BOARDED" -> "셔틀 탑승이 기록되었습니다."; "HANDED_OVER" -> "보호자에게 인계가 기록되었습니다."; "DROPPED_OFF" -> "하차가 완료되었습니다."; "NOT_BOARDED" -> "미탑승이 기록되었습니다."; else -> "오늘 하원 운행 기록이 업데이트되었습니다." }
                val stamp = (trip.optString("droppedOffAt").ifBlank { trip.optString("boardedAt") }).toEpochMillis()
                events.add(JSONObject().put("body", body).put("recorded", stamp).put("tripId", trip.optString("id")))
            }
        }
        state = JSONObject().put("children", children).put("events", JSONArray().apply { events.sortedByDescending { it.optLong("recorded") }.forEach(::put) })
    }

    fun refreshTracking() {
        if (!loggedIn || busy) return
        work { loadTracking() }
    }

    private fun loadTracking() {
        val children = state.optJSONArray("children") ?: return
        for (i in 0 until children.length()) {
            val childId = children.getJSONObject(i).getString("id")
            val row = request("/api/v1/guardian/children/$childId/today?direction=DROP_OFF")
            tracking[childId] = normalizeToday(row)
        }
    }

    private fun normalizeToday(row: JSONObject): JSONObject {
        val status = row.optString("tripStatus", row.optString("status", "NO_TRIP_TODAY"))
        val tripId = row.optString("tripId", "")
        val childStatus = row.optString("childStatus", "EXPECTED")
        val complete = childStatus == "DROPPED_OFF" || status == "COMPLETED"
        val active = tripId.isNotBlank() && status in listOf("IN_PROGRESS", "RETURNING", "COMPLETED")
        val now = System.currentTimeMillis()
        val lat = row.optDouble("latitude", Double.NaN)
        val lon = row.optDouble("longitude", Double.NaN)
        val locationAt = row.optString("locationUpdatedAt").toEpochMillis()
        val location = if (lat.isFinite() && lon.isFinite() && locationAt > 0) JSONObject().put("latitude", lat).put("longitude", lon)
            .put("receivedAt", locationAt / 1000.0) else JSONObject.NULL
        val etaSeconds = row.optInt("etaRemainingSec", -1)
        val etaAt = row.optString("etaReceivedAt").toEpochMillis().takeIf { it > 0 } ?: locationAt
        val eta = if (etaSeconds >= 0 && etaAt > 0) JSONObject().put("seconds", etaSeconds)
            .put("minutes", kotlin.math.ceil(etaSeconds / 60.0).toInt()).put("receivedAt", etaAt / 1000.0).put("kind", "SERVER_REMAINING") else JSONObject.NULL
        val arrived = row.optString("stopStatus") in listOf("ARRIVED", "COMPLETED")
        return JSONObject().put("tripId", tripId).put("available", active).put("journeyStatus", if (complete) "COMPLETED" else "IN_PROGRESS")
            .put("phase", status).put("serverTime", now / 1000.0).put("clientReceivedAt", now)
            .put("location", location).put("source", "SERVER").put("stale", location == JSONObject.NULL || now - locationAt > 30_000)
            .put("startedAt", row.optString("startedAt").toEpochMillis())
            .put("completedAt", row.optString("droppedOffAt").toEpochMillis())
            .put("pickup", JSONObject().put("name", row.optString("stopName", "등록된 하차 지점")))
            .put("progress", JSONArray().put(JSONObject().put("name", row.optString("stopName", "등록된 하차 지점")).put("visited", arrived)))
            .put("timeline", JSONObject().put("current", 0).put("paused", eta == JSONObject.NULL)
                .put("fraction", 0).put("totalSeconds", etaSeconds.coerceAtLeast(0)).put("ownEta", eta).put("nextEta", eta))
            .put("eta", eta).put("remainingStops", 0).put("shares", JSONArray())
            .put("reason", if (tripId.isBlank()) "오늘 예정된 운행이 없습니다." else "현재 서버는 기본 운행 상태를 제공합니다.")
    }

    fun shareTracking(childId: String, tripId: String, onLink: (String) -> Unit) = work {
        val result = request("/api/v1/guardian/children/$childId/trips/$tripId/share-links", JSONObject())
        prefs.edit().putString("share:$childId:$tripId", result.optString("id")).apply()
        val path = result.optString("url")
        onLink(if (path.startsWith("http")) path else base + path)
    }
    fun revokeTracking(childId: String, tripId: String) = work {
        val linkId = prefs.getString("share:$childId:$tripId", null) ?: return@work
        request("/api/v1/guardian/share-links/$linkId/revoke", method = "POST")
        prefs.edit().remove("share:$childId:$tripId").apply()
        refreshTracking()
    }
    fun readPushStatus() { pushStatus = prefs.getString("pushStatus", "알림 연결을 확인해 주세요.")!! }
    fun requestInitialNotification(action: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("askedNotification", false)) {
            prefs.edit().putBoolean("askedNotification", true).apply(); action()
        }
    }
    fun logout() = work {
        Api.request(base, "/api/v1/auth/logout", data = JSONObject().put("refreshToken", prefs.getString("refreshToken", "")))
        prefs.edit().clear().putString("base", base).apply()
        loggedIn = false; tracking.clear(); childTools.clear(); state = JSONObject().put("children", JSONArray()).put("events", JSONArray())
        message = "로그아웃했습니다."
    }

    private fun clearSession() {
        prefs.edit().remove("token").remove("refreshToken").apply()
        loggedIn = false
        tracking.clear()
        childTools.clear()
        state = JSONObject().put("children", JSONArray()).put("events", JSONArray())
    }

    private fun String.toEpochMillis(): Long = runCatching { java.time.OffsetDateTime.parse(this).toInstant().toEpochMilli() }
        .recoverCatching { java.time.LocalDateTime.parse(this).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrDefault(0L)
}
