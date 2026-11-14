package com.hanhyo.shuttle.parent

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NotificationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deniedNotificationDoesNotDisplayOldJourney() {
        val old = normalizeJourney(JSONObject().put("tripId", "15").put("tripStatus", "COMPLETED")
            .put("childStatus", "DROPPED_OFF"), SchoolDirection.Afternoon)
        var retried = false
        compose.setContent { MajungTheme {
            NotificationScreen("", old, "현재 계정으로 조회할 수 없어요.", false, {}, { retried = true })
        } }
        compose.onNodeWithTag("notification-error").assertIsDisplayed()
        compose.onNodeWithTag("home-journey").assertDoesNotExist()
        compose.onNodeWithText("다시 확인").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test fun notificationDisplaysItsOwnChildAndDate() {
        val row = JSONObject().put("tripId", "15").put("tripStatus", "COMPLETED")
            .put("childStatus", "DROPPED_OFF").put("serviceDate", "2026-11-12")
        val data = normalizeJourney(row, SchoolDirection.Afternoon)
        compose.setContent { MajungTheme { NotificationScreen("가상 둘째", data, "", false, {}, {}) } }
        compose.onNodeWithText("가상 둘째 · 운행 소식").assertIsDisplayed()
        compose.onNodeWithText("2026-11-12").assertIsDisplayed()
        compose.onNodeWithText("하차했어요").assertIsDisplayed()
    }

    @Test fun failedConnectionHidesPreviouslyValidEta() {
        val now = System.currentTimeMillis()
        val row = JSONObject().put("tripId", "15").put("tripStatus", "IN_PROGRESS")
            .put("childStatus", "BOARDED").put("etaRemainingSec", 120)
            .put("etaReceivedAt", java.time.Instant.ofEpochMilli(now).toString())
        val failed = journeyConnectionFailure(normalizeJourney(row, SchoolDirection.Afternoon, now))
        compose.setContent { MajungTheme { JourneyCard(failed, SchoolDirection.Afternoon) } }
        compose.onNodeWithText("약 2분 후 도착").assertDoesNotExist()
        compose.onNodeWithText("서버에 연결하지 못했어요").assertIsDisplayed()
    }
}
