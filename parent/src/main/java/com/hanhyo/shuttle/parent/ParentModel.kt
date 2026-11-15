package com.hanhyo.shuttle.parent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanhyo.shuttle.net.Api
import com.hanhyo.shuttle.net.ApiFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/** Adapter for the TayoTayo /api/v1 guardian API. */
@HiltViewModel
class ParentModel @Inject constructor(app: Application, private val repository: GuardianRepository,
    private val settings: GuardianSettings) : AndroidViewModel(app) {
    private val prefs = repository.session
    private val shares = JourneyShares(
        read = { prefs.getString(it, null) },
        write = { key, value -> check(prefs.edit().putString(key, value).commit()) },
        request = { path, body, method -> request(path, body, method) })
    private val workMutex = Mutex()
    private val _uiState = MutableStateFlow(ParentUiState(
        base = prefs.getString("base", DEFAULT_SERVER_URL)!!,
        loggedIn = prefs.contains("token")))
    val uiState: StateFlow<ParentUiState> = _uiState.asStateFlow()
    var base: String
        get() = _uiState.value.base
        private set(value) { _uiState.update { it.copy(base = value) } }
    var loggedIn: Boolean
        get() = _uiState.value.loggedIn
        private set(value) { _uiState.update { it.copy(loggedIn = value) } }
    var busy: Boolean
        get() = _uiState.value.busy
        private set(value) { _uiState.update { it.copy(busy = value) } }
    var foreground: Boolean
        get() = _uiState.value.foreground
        private set(value) { _uiState.update { it.copy(foreground = value) } }
    var message: String
        get() = _uiState.value.message
        private set(value) { _uiState.update { it.copy(message = value) } }
    var pushStatus: String
        get() = _uiState.value.pushStatus
        private set(value) { _uiState.update { it.copy(pushStatus = value) } }
    var notificationTarget: NotificationTarget?
        get() = _uiState.value.notificationTarget
        private set(value) { _uiState.update { it.copy(notificationTarget = value) } }
    var notificationData: JSONObject?
        get() = _uiState.value.notificationData
        private set(value) { _uiState.update { it.copy(notificationData = value) } }
    var notificationChildName: String
        get() = _uiState.value.notificationChildName
        private set(value) { _uiState.update { it.copy(notificationChildName = value) } }
    var notificationError: String
        get() = _uiState.value.notificationError
        private set(value) { _uiState.update { it.copy(notificationError = value) } }
    var state: JSONObject
        get() = _uiState.value.connections
        private set(value) { _uiState.update { it.copy(connections = value) } }
    val tracking: Map<String, JSONObject> get() = _uiState.value.tracking
    private fun putTracking(id: String, value: JSONObject) { _uiState.update { it.copy(tracking = it.tracking + (id to value)) } }
    val morningTracking: Map<String, JSONObject> get() = _uiState.value.morningTracking
    private fun putMorningTracking(id: String, value: JSONObject) { _uiState.update { it.copy(morningTracking = it.morningTracking + (id to value)) } }
    val afternoonTracking: Map<String, JSONObject> get() = _uiState.value.afternoonTracking
    private fun putAfternoonTracking(id: String, value: JSONObject) { _uiState.update { it.copy(afternoonTracking = it.afternoonTracking + (id to value)) } }
    val directions: Map<String, SchoolDirection> get() = _uiState.value.directions
    private fun putDirections(id: String, value: SchoolDirection) { _uiState.update { it.copy(directions = it.directions + (id to value)) } }
    val childTools: Map<String, JSONObject> get() = _uiState.value.childTools
    private fun putChildTools(id: String, value: JSONObject) { _uiState.update { it.copy(childTools = it.childTools + (id to value)) } }

    init {
        viewModelScope.launch {
            runCatching {
                val saved = settings.serverUrl.first()
                if (saved != null) { base = saved; prefs.edit().putString("base", saved).apply() }
                else settings.setServerUrl(base)
            }.onFailure { message = "저장된 서버 설정을 확인하지 못했어요. 서버 주소를 다시 확인해 주세요." }
        }
    }

    fun dispatch(intent: ParentIntent) {
        when (intent) {
            is ParentIntent.Authenticate -> authenticate(intent.join, intent.server, intent.name, intent.email, intent.password, intent.verificationCode)
            is ParentIntent.AcceptInvitation -> accept(intent.code)
            ParentIntent.Refresh -> refresh()
            ParentIntent.RefreshTracking -> refreshTracking()
            is ParentIntent.Foreground -> { foreground = intent.active }
            is ParentIntent.LoadTools -> loadChildTools(intent.childId, intent.date)
            is ParentIntent.Attendance -> createAttendanceRequest(intent.childId, intent.date, intent.direction, intent.type, intent.reason, intent.done)
            is ParentIntent.Pickup -> setDailyPickup(intent.childId, intent.date, intent.direction,
                if (intent.personId == null) "GUARDIAN" else "AUTHORIZED_PERSON", intent.personId, intent.done)
            is ParentIntent.RegisterPerson -> addAuthorizedPickupPerson(intent.childId, intent.name, intent.relationship, "", "")
            is ParentIntent.Share -> shareTracking(intent.childId, intent.tripId, intent.onLink)
            is ParentIntent.RevokeShare -> revokeTracking(intent.childId, intent.tripId)
            is ParentIntent.OpenNotification -> openNotification(intent.target)
            ParentIntent.CloseNotification -> closeNotification()
            ParentIntent.Logout -> logout()
        }
    }

    private fun removeCachedChild(id: String) {
        _uiState.update { it.copy(tracking = it.tracking - id, morningTracking = it.morningTracking - id,
            afternoonTracking = it.afternoonTracking - id, directions = it.directions - id, childTools = it.childTools - id) }
    }

    private fun clearChildCaches() {
        _uiState.update { it.copy(tracking = emptyMap(), morningTracking = emptyMap(), afternoonTracking = emptyMap(),
            directions = emptyMap(), childTools = emptyMap(), connections = JSONObject().put("children", JSONArray()).put("events", JSONArray())) }
    }

    fun openNotification(target: NotificationTarget, refresh: Boolean = false) = work {
        notificationTarget = target
        if (!refresh) { notificationData = null; notificationChildName = "" }
        notificationError = ""
        try {
            // Recheck membership on the server, including after login or a revoked invitation.
            val children = request("/api/v1/me/children").optJSONArray("items")?.objects().orEmpty()
            val child = children.firstOrNull { it.optString("id") == target.childId }
            if (child == null) {
                notificationData = null; notificationChildName = ""
                notificationError = "아이 연결이 해제되었거나 이 계정으로 조회할 수 없는 알림이에요."
                return@work
            }
            val detail = request("/api/v1/guardian/trips/${target.tripId}/children/${target.childId}")
            val direction = if (detail.optString("direction") == "PICK_UP") SchoolDirection.Morning else SchoolDirection.Afternoon
            val today = request("/api/v1/guardian/children/${target.childId}/today?direction=${direction.api}")
            // Never display another trip's ETA on a historical notification.
            val row = if (today.optString("tripId") == target.tripId) today else detail
            notificationData = shares.reconcile(target.childId, loadJourney(row, direction, target.childId))
            notificationChildName = child.optString("name")
        } catch (e: Exception) {
            notificationData = null
            notificationError = when ((e as? ApiFailure)?.status) {
                401 -> "로그인 후 알림을 다시 확인해 주세요."
                403 -> "현재 계정으로 이 아이의 운행을 조회할 수 없어요."
                404 -> "해당 운행 기록을 찾을 수 없어요."
                else -> "서버에 연결하지 못했어요. 다시 확인해 주세요."
            }
        }
    }

    fun closeNotification() {
        notificationTarget = null; notificationData = null
        notificationChildName = ""; notificationError = ""
    }

    private fun request(path: String, data: JSONObject? = null, method: String? = null): JSONObject =
        try { repository.request(base, path, data, method) }
        catch (e: ApiFailure) { if (e.status == 401) clearSession(); throw e }

    private fun work(block: suspend () -> Unit): Job = viewModelScope.launch {
        workMutex.withLock {
            busy = true
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "서버 요청에 실패했습니다." }
            finally { busy = false }
        }
    }

    suspend fun dispatchAndAwait(intent: ParentIntent.OpenNotification) {
        openNotification(intent.target).join()
    }

    fun authenticate(join: Boolean, server: String, name: String, email: String, password: String, verificationCode: String = "") = work {
        base = Api.base(server, BuildConfig.DEBUG)
        settings.setServerUrl(base)
        prefs.edit().putString("base", base).apply()
        if (join) {
            if (verificationCode.isBlank()) {
                val sent = repository.publicRequest(base, "/api/v1/auth/email-verifications", data = JSONObject().put("email", email).put("purpose", "SIGN_UP"))
                message = "개발용 인증번호: ${sent.optString("debugCode")}. 번호를 입력한 뒤 다시 가입을 눌러 주세요."
            } else {
                repository.publicRequest(base, "/api/v1/auth/email-verifications/confirm", data = JSONObject().put("email", email).put("purpose", "SIGN_UP").put("code", verificationCode))
                repository.publicRequest(base, "/api/v1/auth/signup", data = JSONObject().put("email", email).put("name", name).put("password", password).put("verificationCode", verificationCode))
                val result = repository.publicRequest(base, "/api/v1/auth/login", data = JSONObject().put("email", email).put("password", password).put("clientType", "GUARDIAN_APP"))
                prefs.edit().putString("token", result.getString("accessToken")).putString("refreshToken", result.getString("refreshToken")).apply()
                loggedIn = true; message = "가입 및 로그인했습니다. 기관 초대 코드를 연결해 주세요."; loadChildren(); loadTracking(); Push.register(getApplication())
            }
            return@work
        }
        val result = repository.publicRequest(base, "/api/v1/auth/login", data = JSONObject()
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

    fun refresh() {
        if (loggedIn) work {
            try { loadChildren(); loadTracking() }
            catch (e: Exception) { markTrackingFailure(); throw e }
            Push.register(getApplication())
        }
    }

    fun loadChildTools(childId: String, serviceDate: String = LocalDate.now().toString()) = work { loadChildToolsNow(childId, serviceDate) }

    private fun loadChildToolsNow(childId: String, serviceDate: String = LocalDate.now().toString()) {
        val requests = request("/api/v1/guardian/children/$childId/attendance-requests?limit=20")
        val persons = request("/api/v1/guardian/children/$childId/authorized-pickup-persons")
        val dropOff = request("/api/v1/guardian/children/$childId/daily-pickups?serviceDate=$serviceDate&direction=DROP_OFF")
        val pickUp = request("/api/v1/guardian/children/$childId/daily-pickups?serviceDate=$serviceDate&direction=PICK_UP")
        putChildTools(childId, JSONObject()
            .put("requests", requests.optJSONArray("items") ?: JSONArray())
            .put("persons", persons.optJSONArray("items") ?: JSONArray())
            .put("serviceDate", serviceDate).put("dropOff", dropOff).put("pickUp", pickUp))
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
        val authorizedIds = items.objects().map { it.optString("id") }.toSet()
        // Remove cached data as soon as the server no longer lists a connection.
        (tracking.keys.toSet() - authorizedIds).forEach { id ->
            removeCachedChild(id)
        }
        state = JSONObject(state.toString()).put("children", JSONArray().apply {
            state.optJSONArray("children")?.objects()?.filter { it.optString("id") in authorizedIds }?.forEach { put(it) }
        }).put("events", JSONArray().apply {
            state.optJSONArray("events")?.objects()?.filter { it.optString("childId") in authorizedIds }?.forEach { put(it) }
        })
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
        notificationTarget?.let { openNotification(it, refresh = true); return }
        work { loadTracking() }
    }

    private fun loadJourney(row: JSONObject, direction: SchoolDirection, childId: String): JSONObject {
        val journey = withDemoEta(normalizeJourney(row, direction), BuildConfig.DEMO_ETA)
        val tripId = row.optString("tripId").takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true } ?: return journey
        // Only follow a trip ID obtained from the guardian-authorized response.
        // A failed optional route lookup must not discard the child's own status.
        return try {
            attachJourneyRoute(journey, request("/api/v1/guardian/trips/$tripId/children/$childId/route"), childId)
        } catch (e: Exception) {
            if (e is ApiFailure && e.status == 401) throw e
            journey
        }
    }

    private fun loadTracking() {
        val children = state.optJSONArray("children") ?: return
        for (i in 0 until children.length()) {
            val childId = children.getJSONObject(i).getString("id")
            try {
            val morning = request("/api/v1/guardian/children/$childId/today?direction=PICK_UP")
            val afternoon = request("/api/v1/guardian/children/$childId/today?direction=DROP_OFF")
            val direction = chooseDirection(morning, afternoon, java.time.LocalTime.now(java.time.ZoneId.of("Asia/Seoul")))
            putMorningTracking(childId, shares.reconcile(childId, loadJourney(morning, SchoolDirection.Morning, childId)))
            putAfternoonTracking(childId, shares.reconcile(childId, loadJourney(afternoon, SchoolDirection.Afternoon, childId)))
            putDirections(childId, direction)
            putTracking(childId, if (direction == SchoolDirection.Morning) morningTracking[childId]!! else afternoonTracking[childId]!!)
            } catch (e: Exception) {
                if (!loggedIn) return
                if (e is ApiFailure && e.status in listOf(403,404)) {
                    removeCachedChild(childId)
                    state = JSONObject(state.toString()).put("children", JSONArray().apply {
                        children.objects().filter { it.optString("id") != childId }.forEach { put(it) }
                    }).put("events", JSONArray().apply {
                        state.optJSONArray("events")?.objects()?.filter { it.optString("childId") != childId }?.forEach { put(it) }
                    })
                    message = "아이 연결 권한이 변경되었어요. 최신 연결을 확인해 주세요."
                } else {
                    putTracking(childId, journeyConnectionFailure(tracking[childId]))
                    putMorningTracking(childId, journeyConnectionFailure(morningTracking[childId]))
                    putAfternoonTracking(childId, journeyConnectionFailure(afternoonTracking[childId]))
                }
            }
        }
    }

    private fun markTrackingFailure() {
        _uiState.update { it.copy(tracking = it.tracking.mapValues { (_, data) -> journeyConnectionFailure(data) }) }
    }


    fun shareTracking(childId: String, tripId: String, onLink: (String) -> Unit) = work {
        val latest = request("/api/v1/guardian/trips/$tripId/children/$childId")
        val result = shares.create(childId, tripId, latest)
        val path = result.getString("url")
        tracking[childId]?.let { putTracking(childId, shares.reconcile(childId, it)) }
        withContext(Dispatchers.Main) { onLink(if (path.startsWith("http")) path else base + path) }
    }
    fun revokeTracking(childId: String, tripId: String) = work {
        shares.revoke(childId, tripId)
        tracking[childId]?.let { putTracking(childId, shares.reconcile(childId, it)) }
        message = "공유 링크를 종료했습니다."
    }
    fun readPushStatus() { pushStatus = Push.status(getApplication()) }
    fun requestInitialNotification(action: () -> Unit) {
        viewModelScope.launch {
            runCatching { android.os.Build.VERSION.SDK_INT >= 33 && settings.takeAutomaticPrompt() }
                .onSuccess { if (it) action() }
                .onFailure { message = "알림 설정을 확인하지 못했어요. 설정 화면에서 다시 확인해 주세요." }
        }
    }
    fun logout() = work {
        Push.logout(getApplication())
        closeNotification()
        loggedIn = false; clearChildCaches()
        message = "로그아웃했습니다."
    }

    private fun clearSession() {
        closeNotification()
        prefs.edit().remove("token").remove("refreshToken").apply()
        loggedIn = false
        clearChildCaches()
    }

    private fun String.toEpochMillis(): Long = epochMillis()
}
