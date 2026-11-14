package com.hanhyo.shuttle.parent

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Isolated design interactions: no account, push token or remote writes. */
class MajungUiTest {
    @get:Rule val compose=createComposeRule()
    private fun render(actions:GuardianActions=GuardianActions(),data:JSONObject=previewJourney(),direction:SchoolDirection=SchoolDirection.Afternoon) {
        val tools=JSONObject().put("serviceDate",LocalDate.now().toString()).put("requests",JSONArray()).put("persons",JSONArray())
        compose.setContent { MajungTheme { GuardianApp(previewChildren(),mapOf("preview-child" to data),mapOf("preview-child" to direction),mapOf("preview-child" to tools),emptyList(),actions=actions) } }
    }
    private fun screenshot(name:String) {
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val output=File(context.getExternalFilesDir(null),"majung-validation").apply {mkdirs()}
        val roots=compose.onAllNodes(isRoot())
        val target=if(name=="04-attendance") compose.onNodeWithTag("guardian-sheet") else roots[0]
        val bitmap=target.captureToImage().asAndroidBitmap()
        File(output,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun handoverWaitsForSeparateDropoffRecord() {
        render(data=previewJourney().put("journeyStatus","HANDED_OVER"))
        compose.onNodeWithText("보호자 인계 완료").assertExists()
        compose.onNodeWithText("인계 기록 · 하차 대기").assertExists()
        compose.onNodeWithText("하차했어요").assertDoesNotExist()
    }
    @Test fun failedAutomaticShareCleanupCanBeRetriedAfterDropoff() {
        var requested = false
        render(actions=GuardianActions(revoke={_,_->requested=true}), data=previewJourney()
            .put("journeyStatus","COMPLETED").put("shareCleanupError",true)
            .put("shares",JSONArray().put(JSONObject().put("id","1"))))
        compose.onNodeWithTag("share-cleanup-pending").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("home-revoke-share").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(requested) }
        compose.onNodeWithText("통학 상황 공유").assertDoesNotExist()
    }
    @Test fun tabsCalendarAndSelectedChildStayConnected() {
        render()
        compose.onNodeWithTag("tab-Home").assertIsSelected()
        screenshot("01-home")
        compose.onNodeWithTag("tab-Schedule").performClick()
        compose.onNodeWithTag("schedule-calendar").assertIsDisplayed()
        compose.onNodeWithTag("tab-Schedule").assertIsSelected()
        screenshot("02-schedule")
        compose.onNodeWithTag("tab-Child").performClick()
        compose.onNodeWithText("마중 보호자 관리").assertIsDisplayed()
        screenshot("03-child")
        compose.onNodeWithTag("child-selector").performClick()
        compose.onNodeWithText("김바다").performClick()
        compose.onNodeWithTag("tab-Home").performClick()
        compose.onNodeWithText("김바다").assertIsDisplayed()
    }
    @Test fun attendanceSendsSelectedDateDirectionAndClosesOnlyOnSuccess() {
        var request:List<String>?=null
        var done:(()->Unit)?=null
        render(GuardianActions(attendance={id,date,dir,type,note,callback->request=listOf(id,date,dir,type,note);done=callback}))
        compose.onNodeWithTag("tab-Schedule").performClick()
        compose.onNodeWithTag("schedule-PICK_UP").performScrollTo().performClick()
        compose.onNodeWithTag("save-attendance").assertIsNotEnabled()
        compose.onNodeWithTag("request-ABSENT").performClick()
        screenshot("04-attendance")
        compose.onNodeWithTag("save-attendance").performClick()
        compose.runOnIdle {assertEquals(listOf("preview-child",LocalDate.now().toString(),"PICK_UP","ABSENT",""),request)}
        compose.onNodeWithTag("save-attendance").assertExists()
        compose.runOnIdle {done!!.invoke()}
        compose.onNodeWithTag("save-attendance").assertDoesNotExist()
    }
    @Test fun pastCalendarDateIsReadOnly() {
        render()
        compose.onNodeWithTag("tab-Schedule").performClick()
        val past=LocalDate.now().minusDays(1)
        if(past.month!=LocalDate.now().month) compose.onNodeWithContentDescription("이전 달").performClick()
        compose.onNodeWithTag("date-$past").performClick()
        compose.onNodeWithTag("schedule-DROP_OFF").performScrollTo().performClick()
        compose.onNodeWithTag("save-attendance").assertDoesNotExist()
        compose.onNodeWithText("지난 날짜의 변경 요청을 확인해 주세요.").assertIsDisplayed()
    }
    @Test fun noCallTransportCannotPretendToSendResponse() {
        compose.setContent { MajungTheme { androidx.compose.foundation.layout.Column {CallResponsePanel("테스트 아이","테스트 정류장")} } }
        compose.onNodeWithTag("call-coming").assertIsNotEnabled()
        compose.onNodeWithTag("call-not-riding").assertIsNotEnabled()
    }
    @Test fun responseButtonsUseDifferentValuesAndDisableAfterAcknowledgement() {
        var response by mutableStateOf<String?>(null)
        compose.setContent { MajungTheme { androidx.compose.foundation.layout.Column {CallResponsePanel("김하늘 · 햇살어린이집","은행나무공원 앞",response=response,onRespond={response=it})} } }
        screenshot("06-call")
        compose.onNodeWithTag("call-coming").performClick()
        compose.runOnIdle {assertEquals("COMING",response)}
        compose.onNodeWithTag("call-not-riding").assertIsNotEnabled()
    }
    @Test fun morningUsesSharedWaitingDesign() {
        render(data=previewJourney(1),direction=SchoolDirection.Morning)
        compose.onNodeWithText("오늘의 등원").assertIsDisplayed()
        compose.onNodeWithText("오늘의 출발을 기다려요").assertIsDisplayed()
        screenshot("05-morning")
    }
    @Test fun tripEndDoesNotRenderHandoverSuccess() {
        render(data=normalizeJourney(JSONObject().put("tripId","test").put("tripStatus","COMPLETED").put("childStatus","BOARDED"),SchoolDirection.Afternoon))
        compose.onNodeWithText("운행이 종료됐어요").assertIsDisplayed()
        compose.onNodeWithText("보호자 인계 완료").assertDoesNotExist()
    }
}
