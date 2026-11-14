package com.hanhyo.shuttle.parent

import android.util.Log
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.hanhyo.shuttle.net.Api
import com.hanhyo.shuttle.net.ApiFailure
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import java.time.LocalDate
import java.time.ZoneId
import java.time.OffsetDateTime
import java.util.UUID

/** Opt-in local integration test. Creates isolated synthetic records; never uses existing users/children. */
class LiveGuardianIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val base = "http://10.0.2.2:8080"
    private fun obj(vararg values: Pair<String, Any>) = JSONObject().apply { values.forEach { put(it.first, it.second) } }
    private fun api(path: String, token: String = "", body: JSONObject? = null, method: String? = null) =
        Api.request(base, path, token, body, method)
    private fun stage(name: String, block: () -> Unit) {
        try { block(); Log.i("LiveGuardianTest", "PASS $name") }
        catch (e: Exception) { throw AssertionError("Stage failed: $name (${e.javaClass.simpleName}: ${e.message})", e) }
    }
    private fun account(email: String, password: String, type: String): JSONObject {
        val code = api("/api/v1/auth/email-verifications", body = obj("email" to email, "purpose" to "SIGN_UP")).getString("debugCode")
        api("/api/v1/auth/email-verifications/confirm", body = obj("email" to email, "purpose" to "SIGN_UP", "code" to code))
        api("/api/v1/auth/signup", body = obj("email" to email, "password" to password, "name" to "자동 검증 보호자", "verificationCode" to code))
        return api("/api/v1/auth/login", body = obj("email" to email, "password" to password, "clientType" to type))
    }
    private fun denied(block: () -> Unit) {
        try { block(); fail("Unauthorized request unexpectedly succeeded") }
        catch (e: ApiFailure) { assertTrue("Expected authorization/not-found rejection, got ${e.status}", e.status in listOf(401,403,404)) }
    }

    @Test fun implementedGuardianApiFlow() {
        assumeTrue("Opt in with -Pandroid.testInstrumentationRunnerArguments.liveServer=true",
            InstrumentationRegistry.getArguments().getString("liveServer") == "true")
        stage("database-health") { assertEquals("UP", api("/health/database").getString("status")) }
        val suffix = UUID.randomUUID().toString().take(8)
        val password = UUID.randomUUID().toString() + "Aa1!"
        val email = "guardian-$suffix@example.invalid"
        var admin = ""; var guardian = ""; var refresh = ""
        stage("signup-login") {
            admin = account("admin-$suffix@example.invalid", password, "ADMIN_WEB").getString("accessToken")
            val login = account(email, password, "GUARDIAN_APP")
            guardian = login.getString("accessToken"); refresh = login.getString("refreshToken")
            assertEquals(0, api("/api/v1/me/children", guardian).getJSONArray("items").length())
        }
        val org = api("/api/v1/orgs", admin, obj("name" to "자동검증기관-$suffix", "orgType" to "KINDERGARTEN")).getLong("id")
        val child = api("/api/v1/orgs/$org/children", admin, obj("name" to "검증아이-$suffix", "className" to "테스트반")).getLong("id")
        val otherChild = api("/api/v1/orgs/$org/children", admin, obj("name" to "미연결검증아이-$suffix")).getLong("id")
        stage("invitation-child-list") {
            val invitation = api("/api/v1/orgs/$org/children/$child/guardian-invitations", admin, obj("email" to email, "relationship" to "MOTHER"))
            api("/api/v1/guardian-invitations/accept", guardian, obj("code" to invitation.getString("invitationCode")))
            val children = api("/api/v1/me/children", guardian).getJSONArray("items")
            assertEquals(1, children.length()); assertEquals(child, children.getJSONObject(0).getLong("id"))
        }
        val today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString()
        stage("no-trip-and-access-control") {
            assertEquals("NO_TRIP_TODAY", api("/api/v1/guardian/children/$child/today?direction=DROP_OFF", guardian).getString("status"))
            denied { api("/api/v1/guardian/children/$otherChild/today?direction=DROP_OFF", guardian) }
        }
        stage("attendance-request") {
            api("/api/v1/guardian/children/$child/attendance-requests", guardian,
                obj("serviceDate" to LocalDate.parse(today).plusDays(1).toString(), "direction" to "DROP_OFF", "requestType" to "SELF_PICKUP", "reasonText" to "자동 검증용"))
            assertEquals("SELF_PICKUP", api("/api/v1/guardian/children/$child/attendance-requests?limit=20", guardian).getJSONArray("items").getJSONObject(0).getString("requestType"))
        }
        stage("pickup-registration-review-assignment") {
            val person = api("/api/v1/guardian/children/$child/authorized-pickup-persons", guardian, obj("name" to "검증대리인", "relationship" to "FAMILY")).getLong("id")
            assertEquals("PENDING", api("/api/v1/guardian/children/$child/authorized-pickup-persons", guardian).getJSONArray("items").getJSONObject(0).getString("status"))
            api("/api/v1/orgs/$org/authorized-pickup-persons/$person/review", admin, obj("decision" to "APPROVED"))
            api("/api/v1/guardian/children/$child/daily-pickups", guardian, obj("serviceDate" to today, "direction" to "DROP_OFF", "personType" to "AUTHORIZED_PERSON", "pickupPersonId" to person), "PUT")
            assertEquals(person, api("/api/v1/guardian/children/$child/daily-pickups?serviceDate=$today&direction=DROP_OFF", guardian).getLong("pickupPersonId"))
            api("/api/v1/guardian/children/$child/daily-pickups", guardian, obj("serviceDate" to today, "direction" to "PICK_UP", "personType" to "GUARDIAN"), "PUT")
            assertFalse(api("/api/v1/guardian/children/$child/daily-pickups?serviceDate=$today&direction=PICK_UP", guardian).isNull("pickupGuardianUserId"))
        }
        val vehicle = api("/api/v1/orgs/$org/vehicles", admin, obj("name" to "검증차량-$suffix", "plateNumber" to "TEST-$suffix", "seatCapacity" to 20)).getLong("id")
        val stop = api("/api/v1/orgs/$org/stops", admin, obj("name" to "검증정류장", "latitude" to 37.5665, "longitude" to 126.978)).getLong("id")
        val route = api("/api/v1/orgs/$org/routes", admin, obj("vehicleId" to vehicle, "name" to "검증하원노선", "direction" to "DROP_OFF", "departureTime" to "15:00:00")).getLong("id")
        api("/api/v1/orgs/$org/routes/$route/stops", admin, obj("stops" to JSONArray().put(obj("stopId" to stop, "sequence" to 1))), "PUT")
        api("/api/v1/orgs/$org/children/$child/route-assignments", admin, obj("routeId" to route, "vehicleId" to vehicle, "stopId" to stop, "weekdays" to 127, "effectiveFrom" to today), "PUT")
        val pairCode = api("/api/v1/orgs/$org/vehicles/$vehicle/pairing-codes", admin, JSONObject()).getString("code")
        val credential = api("/api/v1/vehicle/pair", body = obj("code" to pairCode, "installId" to UUID.randomUUID().toString(), "appVersion" to "integration-test", "pleosSdkVersion" to "test")).getString("vehicleCredential")
        val trip = api("/api/v1/orgs/$org/trips", admin, obj("clientTripId" to UUID.randomUUID().toString(), "vehicleId" to vehicle, "routeId" to route, "serviceDate" to today, "direction" to "DROP_OFF"))
        val tripId = trip.getLong("id"); val tripStop = trip.getJSONArray("stops").getJSONObject(0).getLong("id")
        val tripChild = trip.getJSONArray("children").getJSONObject(0).getLong("tripChildId")
        fun vehicleRequest(path: String, body: JSONObject = JSONObject()) = Api.request(base, path, credential, body, credentialHeader = "X-Vehicle-Credential")
        stage("trip-start-location-eta") {
            vehicleRequest("/api/v1/trips/$tripId/start")
            vehicleRequest("/api/v1/vehicle/trips/$tripId/locations", obj("samples" to JSONArray().put(obj("recordedAt" to OffsetDateTime.now().toString(), "latitude" to 37.5665, "longitude" to 126.978))))
            vehicleRequest("/api/v1/vehicle/trips/$tripId/eta-snapshots", obj("routeRevisionNo" to 1, "sourceTime" to OffsetDateTime.now().toString(), "validationStatus" to "VALID", "entries" to JSONArray().put(obj("bundleIndex" to 0, "pointType" to "STOP", "tripStopId" to tripStop, "remainingSec" to 120, "remainingM" to 600))))
            val row = api("/api/v1/guardian/children/$child/today?direction=DROP_OFF", guardian)
            assertEquals(tripId, row.getLong("tripId")); assertEquals("IN_PROGRESS", row.getString("tripStatus"))
            val normalized = normalizeJourney(row, SchoolDirection.Afternoon)
            assertEquals("Service date returned by live server", today, normalized.getString("serviceDate"))
            assertFalse("ETA rejected: serverTime=${row.optString("etaReceivedAt")}, now=${java.time.Instant.now()}, status=${row.optString("tripStatus")}, seconds=${row.optInt("etaRemainingSec")}", normalized.isNull("eta"))
            assertEquals(120, normalized.getJSONObject("eta").getInt("seconds"))
            assertEquals(37.5665, normalized.getJSONObject("location").getDouble("latitude"), 0.00001)
            assertTrue(journeyConnectionFailure(normalized).isNull("eta"))
        }
        stage("share-create-read-revoke") {
            val share = api("/api/v1/guardian/children/$child/trips/$tripId/share-links", guardian, JSONObject())
            val path = share.getString("url")
            assertEquals(child, api(path).getLong("childId"))
            api("/api/v1/guardian/share-links/${share.getLong("id")}/revoke", guardian, method = "POST")
            denied { api(path) }
        }
        stage("arrival-dropoff-history-notification-detail") {
            vehicleRequest("/api/v1/trips/$tripId/stops/$tripStop/arrive")
            assertEquals("ARRIVED", normalizeJourney(api("/api/v1/guardian/children/$child/today?direction=DROP_OFF", guardian), SchoolDirection.Afternoon).getString("journeyStatus"))
            for (event in listOf("CHILD_BOARDED", "CHILD_DROPPED_OFF")) vehicleRequest("/api/v1/trips/$tripId/events", obj("clientEventId" to UUID.randomUUID().toString(), "eventType" to event, "occurredAt" to OffsetDateTime.now().toString(), "tripChildId" to tripChild, "tripStopId" to tripStop))
            val row = api("/api/v1/guardian/children/$child/today?direction=DROP_OFF", guardian)
            assertEquals("COMPLETED", normalizeJourney(row, SchoolDirection.Afternoon).getString("journeyStatus"))
            assertTrue(normalizeJourney(row, SchoolDirection.Afternoon).isNull("eta"))
            assertEquals("DROPPED_OFF", api("/api/v1/guardian/trips/$tripId/children/$child", guardian).getString("childStatus"))
            assertEquals(tripId, api("/api/v1/guardian/children/$child/trips?direction=DROP_OFF&limit=10", guardian).getJSONArray("items").getJSONObject(0).getLong("id"))
            denied { api("/api/v1/guardian/trips/$tripId/children/$otherChild", guardian) }
            vehicleRequest("/api/v1/trips/$tripId/stops/$tripStop/complete")
            vehicleRequest("/api/v1/trips/$tripId/complete")
        }
        stage("refresh-token") {
            val renewed = api("/api/v1/auth/refresh", body = obj("refreshToken" to refresh))
            guardian = renewed.getString("accessToken")
            assertEquals(1, api("/api/v1/me/children", guardian).getJSONArray("items").length())
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Resume after a failed run only when the current identity is one of this test's synthetic accounts.
        Push.prefs(context).getString("token", null)?.let { token ->
            val user = runCatching { api("/api/v1/me", token).getJSONObject("user") }.getOrNull()
            if (user?.optString("email")?.matches(Regex("guardian-[0-9a-f]{8}@example\\.invalid")) == true &&
                user.optString("name") == "자동 검증 보호자") Push.logout(context)
        }
        if (!Push.prefs(context).contains("token")) {
            if (android.os.Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
            try {
            stage("app-login-home-and-notification-navigation") {
                ActivityScenario.launch(MainActivity::class.java).use {
                    compose.onNodeWithText("개발 서버 설정").performScrollTo().performClick()
                    compose.onNodeWithTag("parent-server").performTextReplacement(base)
                    compose.onNodeWithText("적용").performClick()
                    compose.onNodeWithTag("parent-email").performScrollTo().performTextReplacement(email)
                    compose.onNodeWithTag("parent-password").performScrollTo().performTextReplacement(password)
                    closeSoftKeyboard()
                    compose.onNodeWithTag("parent-auth").performScrollTo().performClick()
                    compose.waitUntil(20_000) { runCatching { compose.onAllNodesWithText("검증아이-$suffix").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
                    compose.onNodeWithText("검증아이-$suffix").assertIsDisplayed()
                    compose.onNodeWithText("하차했어요").performScrollTo().assertIsDisplayed()
                }
                val intent = Intent(context, MainActivity::class.java).putExtra("childId", child.toString()).putExtra("tripId", tripId.toString())
                ActivityScenario.launch<MainActivity>(intent).use {
                    compose.waitUntil(20_000) { runCatching { compose.onAllNodesWithText("검증아이-$suffix · 운행 소식").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
                    compose.onNodeWithText("검증아이-$suffix · 운행 소식").assertIsDisplayed()
                    compose.waitUntil(20_000) { runCatching {
                        compose.onNodeWithText(today).performScrollTo().assertIsDisplayed(); true
                    }.getOrDefault(false) }
                    compose.onNodeWithText("하차했어요").performScrollTo().assertIsDisplayed()
                }
            }
            } finally {
                // End only the synthetic account's session, including on UI assertion failure.
                if (Push.prefs(context).contains("token")) Push.logout(context)
            }
            assertFalse(Push.prefs(context).contains("token"))
        } else Log.i("LiveGuardianTest", "SKIP app-login: preserve existing user session")
    }
}
