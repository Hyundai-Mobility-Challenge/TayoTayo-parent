package com.hanhyo.shuttle.parent

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hanhyo.shuttle.net.Api
import org.json.JSONObject
import org.junit.*
import org.junit.runner.RunWith

/** Explicit local demo login test. Leaves the demo account signed in for manual FCM verification. */
@RunWith(AndroidJUnit4::class)
class ParentDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun guardian_logs_in_accepts_invite_and_registers_push_without_usb_endpoint() {
        val server = InstrumentationRegistry.getArguments().getString("serverBase")
        Assume.assumeTrue("Requires explicit local serverBase", !server.isNullOrEmpty())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Never replace a pre-existing account. Repeat tests require explicit logout in the app.
        Assume.assumeFalse("An existing guardian is already signed in", Push.prefs(context).contains("token"))
        val admin = Api.request(server!!, "/api/login", data = JSONObject().put("email", "admin@demo.example").put("password", "ShuttleDemo123!")).getString("token")
        val child = Api.request(server, "/api/admin/state", admin).getJSONArray("children").getJSONObject(0).getString("id")
        val invitation = Api.request(server, "/api/admin/invite", admin, JSONObject().put("childId", child).put("email", "parent@demo.example")).getString("code")
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag("parent-server").performScrollTo().performTextReplacement(server)
            compose.onNodeWithTag("parent-email").performScrollTo().performTextReplacement("parent@demo.example")
            compose.onNodeWithTag("parent-password").performScrollTo().performTextReplacement("ShuttleDemo123!")
            closeSoftKeyboard()
            compose.onNodeWithTag("parent-auth").performScrollTo().performClick()
            compose.waitUntil(20000) { compose.onAllNodesWithTag("parent-code").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("parent-code").performTextReplacement(invitation)
            closeSoftKeyboard()
            compose.onNodeWithTag("parent-accept").performScrollTo().performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText("민수", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("민수").performScrollTo().assertIsDisplayed()
            compose.waitUntil(30000) { Push.prefs(context).getString("pushStatus", "") == "푸시 등록 완료" }
            compose.onNodeWithText("알림·설정").performClick()
            compose.onNodeWithTag("parent-push-status").performScrollTo().assertTextEquals("푸시 등록 완료")
        }
    }
}
