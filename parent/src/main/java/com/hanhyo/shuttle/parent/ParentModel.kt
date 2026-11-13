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
    val morningTracking = mutableStateMapOf<String, JSONObject>()
    val afternoonTracking = mutableStateMapOf<String, JSONObject>()
    val directions = mutableStateMapOf<String, SchoolDirection>()
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

    fun refresh() { if (loggedIn) work { loadChildren(); loadTracking() } }

    fun loadChildTools(childId: String, serviceDate: String = LocalDate.now().toString()) = work { loadChildToolsNow(childId, serviceDate) }

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

    fun createAttendanceRequest(childId: String, serviceDate: String, direction: String, requestType: String, reason: String, onSaved: () -> Unit = {}) = work {
        require(!LocalDate.parse(serviceDate).isBefore(LocalDate.now())) { "지난 날짜는 변경할 수 없습니다." }
        val body = JSONObject().put("serviceDate", serviceDate).put("direction", direction).put("requestType", requestType)
        if (reason.isNotBlank()) body.put("reasonText", reason.trim())
        request("/api/v1/guardian/children/$childId/attendance-requests", body)
        message = "출결·하원 변경 요청을 접수했습니다."
        withContext(Dispatchers.Main) { onSaved() }
        runCatching { loadChildToolsNow(childId, serviceDate) }.onFailure {
            message = "변경 요청은 접수됐습니다. 최신 목록은 연결 후 다시 확인해 주세요."
        }
    }

    fun addAuthorizedPickupPerson(childId: String, name: String, relationship: String, validFrom: String, validTo: String) = work {
        val body = JSONObject().put("name", name.trim()).put("relationship", relationship.trim())
        if (validFrom.isNotBlank()) body.put("validFrom", validFrom)
        if (validTo.isNotBlank()) body.put("validTo", validTo)
        request("/api/v1/guardian/children/$childId/authorized-pickup-persons", body)
        message = "대리 픽업 등록을 신청했습니다. 기관 승인 후 지정할 수 있습니다."
        loadChildToolsNow(childId)
    }

    fun setDailyPickup(childId: String, serviceDate: String, direction: String, personType: String, pickupPersonId: String? = null, onSaved: () -> Unit = {}) = work {
        require(!LocalDate.parse(serviceDate).isBefore(LocalDate.now())) { "지난 날짜는 변경할 수 없습니다." }
        val body = JSONObject().put("serviceDate", serviceDate).put("direction", direction).put("personType", personType)
        if (personType == "AUTHORIZED_PERSON" && pickupPersonId != null) body.put("pickupPersonId", pickupPersonId.toLong())
        request("/api/v1/guardian/children/$childId/daily-pickups", body, "PUT")
        message = "${if (direction == "PICK_UP") "등원" else "하원"} 픽업 담당자를 저장했습니다."
        withContext(Dispatchers.Main) { onSaved() }
        runCatching { loadChildToolsNow(childId, serviceDate) }.onFailure {
            message = "마중 보호자는 저장됐습니다. 최신 목록은 연결 후 다시 확인해 주세요."
        }
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
                .put("relationship", c.optString("relationship")).put("vehicle", c.optString("vehicleName")))
            val history = request("/api/v1/guardian/children/$childId/trips?direction=DROP_OFF&limit=10").optJSONArray("items") ?: JSONArray()
            for (j in 0 until history.length()) {
                val trip = history.getJSONObject(j)
                val status = trip.optString("childStatus", trip.optString("tripStatus"))
                val body = when (status) { "BOARDED" -> "셔틀 탑승이 기록되었습니다."; "HANDED_OVER" -> "보호자에게 인계가 기록되었습니다."; "DROPPED_OFF" -> "하차가 완료되었습니다."; "NOT_BOARDED" -> "미탑승이 기록되었습니다."; else -> "오늘 하원 운행 기록이 업데이트되었습니다." }
                val stamp = (trip.optString("droppedOffAt").ifBlank { trip.optString("boardedAt") }).toEpochMillis()
                events.add(JSONObject().put("body", "${c.optString("name")} · $body").put("childId",childId).put("recorded", stamp).put("tripId", trip.optString("id")))
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
            val morning = request("/api/v1/guardian/children/$childId/today?direction=PICK_UP")
            val afternoon = request("/api/v1/guardian/children/$childId/today?direction=DROP_OFF")
            val direction = chooseDirection(morning, afternoon, java.time.LocalTime.now(java.time.ZoneId.of("Asia/Seoul")))
            morningTracking[childId] = normalizeJourney(morning, SchoolDirection.Morning)
            afternoonTracking[childId] = normalizeJourney(afternoon, SchoolDirection.Afternoon)
            directions[childId] = direction
            tracking[childId] = if (direction == SchoolDirection.Morning) morningTracking[childId]!! else afternoonTracking[childId]!!
            val shareId=prefs.getString("share:$childId:${tracking[childId]!!.optString("tripId")}",null)
            if(shareId!=null) tracking[childId]!!.put("shares",JSONArray().put(JSONObject().put("id",shareId)))
        }
    }


    fun shareTracking(childId: String, tripId: String, onLink: (String) -> Unit) = work {
        val result = request("/api/v1/guardian/children/$childId/trips/$tripId/share-links", JSONObject())
        prefs.edit().putString("share:$childId:$tripId", result.optString("id")).apply()
        val path = result.optString("url")
        tracking[childId]?.let { tracking[childId] = JSONObject(it.toString()).put("shares", JSONArray().put(JSONObject().put("id", result.optString("id")))) }
        withContext(Dispatchers.Main) { onLink(if (path.startsWith("http")) path else base + path) }
    }
    fun revokeTracking(childId: String, tripId: String) = work {
        val linkId = prefs.getString("share:$childId:$tripId", null) ?: return@work
        request("/api/v1/guardian/share-links/$linkId/revoke", method = "POST")
        prefs.edit().remove("share:$childId:$tripId").apply()
        tracking[childId]?.let { tracking[childId] = JSONObject(it.toString()).put("shares", JSONArray()) }
        message = "공유 링크를 종료했습니다."
    }
    fun readPushStatus() { pushStatus = prefs.getString("pushStatus", "알림 연결을 확인해 주세요.")!! }
    fun requestInitialNotification(action: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("askedNotification", false)) {
            prefs.edit().putBoolean("askedNotification", true).apply(); action()
        }
    }
    fun logout() = work {
        Push.logout(getApplication())
        loggedIn = false; tracking.clear(); childTools.clear(); state = JSONObject().put("children", JSONArray()).put("events", JSONArray())
        message = "로그아웃했습니다."
        morningTracking.clear(); afternoonTracking.clear(); directions.clear()
    }

    private fun clearSession() {
        prefs.edit().remove("token").remove("refreshToken").apply()
        loggedIn = false
        tracking.clear()
        childTools.clear()
        morningTracking.clear(); afternoonTracking.clear(); directions.clear()
        state = JSONObject().put("children", JSONArray()).put("events", JSONArray())
    }

    private fun String.toEpochMillis(): Long = epochMillis()
}
