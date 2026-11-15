package com.hanhyo.shuttle.parent

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

class JourneyScenarioUiTest {
    @get:Rule val compose=createComposeRule()
    private fun row(child:String="BOARDED")=JSONObject().put("tripId","7").put("tripStatus","IN_PROGRESS").put("childStatus",child).put("stopName","판교역 앞")
    @Test fun demoEtaTransitionsToArrivalAndHandover() {
        val state=mutableStateOf(withDemoEta(normalizeJourney(row(),SchoolDirection.Afternoon),true))
        compose.setContent { MajungTheme { JourneyCard(state.value,SchoolDirection.Afternoon) } }
        compose.onNodeWithText("약 6분 후 도착").assertIsDisplayed()
        compose.onNodeWithText("시연용 예상 시간 · 실제 교통 정보 아님").assertIsDisplayed()
        compose.runOnIdle { state.value=withDemoEta(normalizeJourney(row().put("stopStatus","ARRIVED"),SchoolDirection.Afternoon),true) }
        compose.onNodeWithText("정류장에 도착했어요").assertIsDisplayed()
        compose.onNodeWithText("약 6분 후 도착").assertDoesNotExist()
        compose.runOnIdle { state.value=normalizeJourney(row("HANDED_OVER"),SchoolDirection.Afternoon) }
        compose.onNodeWithText("오늘도 잘 마중했어요").assertIsDisplayed()
        compose.onNodeWithText("보호자 인계 확인 · 하차 기록 대기").assertIsDisplayed()
        compose.runOnIdle { state.value=normalizeJourney(row("DROPPED_OFF").put("handedOverAt","2026-11-15T12:00:00+09:00"),SchoolDirection.Afternoon) }
        compose.onNodeWithText("오늘도 잘 마중했어요").assertIsDisplayed()
        compose.onNodeWithText("보호자 인계 · 하차 완료").assertIsDisplayed()
    }
    @Test fun dropAloneShowsRecordInsteadOfClaimingHandover() {
        val data=normalizeJourney(row("DROPPED_OFF"),SchoolDirection.Afternoon)
        compose.setContent { MajungTheme { JourneyCard(data,SchoolDirection.Afternoon) } }
        compose.onNodeWithText("하차 기록이 확인됐어요").assertIsDisplayed()
        compose.onNodeWithText("오늘도 잘 마중했어요").assertDoesNotExist()
    }
}
