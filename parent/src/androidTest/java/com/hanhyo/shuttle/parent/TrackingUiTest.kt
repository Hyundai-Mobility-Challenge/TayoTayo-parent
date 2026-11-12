package com.hanhyo.shuttle.parent

import android.app.KeyguardManager
import android.os.PowerManager
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Synthetic native UI only. Never changes login, actual child records, permissions or device settings. */
class TrackingUiTest {
 @get:Rule val compose=createComposeRule()
 @Before fun visibleUnlockedDevice() {
  val c=InstrumentationRegistry.getInstrumentation().targetContext
  Assume.assumeTrue(c.getSystemService(PowerManager::class.java).isInteractive)
  Assume.assumeFalse(c.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
 }
 private fun fixture():JSONObject {
  val at=System.currentTimeMillis()
  return JSONObject("""{"available":true,"tripId":"synthetic-trip","journeyStatus":"IN_TRANSIT","phase":"DRIVING","source":"SIMULATED","stale":false,"remainingStops":1,"pickup":{"name":"서울숲 입구"},"location":{},"eta":{"minutes":5,"kind":"SIMULATED_SCHEDULE"},"progress":[{"kind":"STOP","visited":false,"current":true},{"kind":"PICKUP","visited":false,"current":false}],"timeline":{"current":0,"fraction":0.5,"totalSeconds":120,"paused":false,"nextEta":{"seconds":60}},"shares":[{"id":"synthetic-share"}]}""")
   .put("serverTime",at/1000.0).put("clientReceivedAt",at).also {
    it.getJSONObject("location").put("receivedAt",at)
    it.getJSONObject("eta").put("receivedAt",at)
    it.getJSONObject("timeline").getJSONObject("nextEta").put("receivedAt",at)
   }
 }
 private fun screen(data:androidx.compose.runtime.MutableState<JSONObject?>,busy:androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(false),
  share:(String)->Unit={},revoke:(String)->Unit={},foreground:Boolean=true) {
  compose.setContent { MaterialTheme(colorScheme=lightColorScheme(primary=Ink,background=Paper)) {
   Surface(Modifier.fillMaxSize(),color=Paper) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
     GuardianHeader()
     Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
      Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
       GuardianChildHeading("민수","햇살유치원","1호차")
       TrackingContent(data.value,foreground,busy.value,share,revoke)
      }
     }
    }
   }
  } }
 }
 private fun screenshot(name:String) {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val out=File(context.getExternalFilesDir(null),"guardian-design");out.mkdirs()
  File(out,name+".png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
 }
 @Test fun shareAndRevokeUseCurrentTripAndBusyBlocksDuplicateTap() {
  val data=mutableStateOf<JSONObject?>(fixture());val busy=mutableStateOf(false);var shares=0;var revokes=0
  screen(data,busy,{assertEquals("synthetic-trip",it);shares++;busy.value=true},{revokes++})
  compose.onNodeWithText("약 5분").assertExists()
  compose.onNodeWithTag("tracking-share").performScrollTo().performClick()
  compose.onNodeWithTag("tracking-share").assertIsNotEnabled()
  compose.runOnIdle { assertEquals(1,shares);busy.value=false }
  compose.onNodeWithTag("tracking-revoke").performScrollTo().performClick()
  compose.runOnIdle { assertEquals(1,revokes) }
 }
 @Test fun arrivalThenAlightingMovesBusAndPreservesCompletionReceipt() {
  val data=mutableStateOf<JSONObject?>(fixture());screen(data)
  compose.onNodeWithTag("journey-title").assertTextEquals("약 5분")
  screenshot("moving")
  compose.runOnIdle {
   data.value=JSONObject(data.value.toString()).put("journeyStatus","ARRIVED").put("phase","ALIGHTING").put("remainingStops",0)
    .also {
      it.getJSONObject("timeline").put("current",1)
      it.getJSONArray("progress").getJSONObject(0).put("visited",true).put("current",false)
      it.getJSONArray("progress").getJSONObject(1).put("current",true)
    }
  }
  compose.onNodeWithText("픽업 장소에 도착했어요").assertExists()
  compose.onNodeWithTag("journey-bus").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"내 아이 하차 지점 도착"))
  compose.onNodeWithText("약 5분").assertDoesNotExist()
  compose.mainClock.advanceTimeBy(1000);compose.waitForIdle();screenshot("arrived")
  compose.runOnIdle {
   data.value=JSONObject(data.value.toString()).put("available",false).put("journeyStatus","COMPLETED").put("completedAt",System.currentTimeMillis())
    .also{it.remove("location");it.remove("eta");it.remove("timeline")}
  }
  compose.onNodeWithText("오늘도 하원을 마쳤어요").assertExists()
  compose.onNodeWithTag("journey-bus").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"하차 완료 지점"))
  compose.onNodeWithTag("journey-completed").assertExists()
  compose.onNodeWithTag("tracking-share").assertDoesNotExist()
  compose.onNodeWithText("지도 크게 보기").assertDoesNotExist()
  screenshot("completed")
 }
 @Test fun staleEtaHiddenAndBusDoesNotClaimArrival() {
  screen(mutableStateOf(fixture().put("stale",true)))
  compose.onNodeWithText("약 5분").assertDoesNotExist()
  compose.onNodeWithTag("journey-bus").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"진행 갱신 대기"))
  compose.onNodeWithText("픽업 장소에 도착했어요").assertDoesNotExist()
 }
 @Test fun waitingShowsPickupWithoutMapOrSharing() {
  screen(mutableStateOf(JSONObject().put("available",false).put("reason","출발 준비 중").put("pickup",JSONObject().put("name","서울숲 입구"))))
  compose.onNodeWithTag("waiting-pickup").assertTextEquals("서울숲 입구")
  compose.onNodeWithTag("tracking-share").assertDoesNotExist()
  compose.onNodeWithTag("tracking-pickup-map").assertDoesNotExist()
  screenshot("waiting")
 }
 @Test fun zeroEtaDoesNotConfirmArrivalAndNewTripClearsCompletion() {
  val v=fixture();v.getJSONObject("eta").put("minutes",0);v.getJSONObject("timeline").getJSONObject("nextEta").put("seconds",0)
  val data=mutableStateOf<JSONObject?>(v);screen(data)
  compose.onNodeWithText("픽업 장소에 도착했어요").assertDoesNotExist()
  compose.runOnIdle { data.value=JSONObject().put("available",false).put("journeyStatus","COMPLETED").put("tripId","old").put("completedAt",System.currentTimeMillis()).put("pickup",JSONObject().put("name","서울숲 입구")) }
  compose.onNodeWithTag("journey-completed").assertExists()
  compose.runOnIdle {data.value=fixture().put("tripId","new")}
  compose.onNodeWithTag("journey-completed").assertDoesNotExist()
  compose.onNodeWithText("약 5분").assertExists()
 }
 @Test fun confirmedArrivalVisibleWithoutLiveLocationSharing() {
  val v=fixture().put("available",false).put("journeyStatus","ARRIVED").put("phase","ALIGHTING")
  v.remove("location");v.remove("eta");v.remove("timeline")
  screen(mutableStateOf(v))
  compose.onNodeWithText("픽업 장소에 도착했어요").assertExists()
  compose.onNodeWithTag("journey-bus").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"내 아이 하차 지점 도착"))
  compose.onNodeWithTag("tracking-share").assertDoesNotExist()
 }
 @Test fun backgroundDoesNotExposeRouteOrCompletion() {
  screen(mutableStateOf(fixture()),foreground=false)
  compose.onNodeWithTag("journey-bus").assertDoesNotExist()
  compose.onNodeWithTag("tracking-share").assertDoesNotExist()
 }

 @Test fun ownNativeEtaAndDepartureBannerRefreshTogether() {
  val v=fixture().put("source","PLEOS_NAVI").put("startedAt",System.currentTimeMillis())
  v.getJSONObject("eta").put("kind","PLEOS_WAYPOINT_ESTIMATE").put("dwellSeconds",0).put("minutes",6)
  val data=mutableStateOf<JSONObject?>(v);screen(data)
  compose.onNodeWithText("약 6분").assertExists()
  compose.onNodeWithTag("journey-departed").assertExists()
  compose.onNodeWithText("Pleos에서 받은 남은 시간이에요. 새 내비 정보가 오면 갱신돼요.").assertExists()
  compose.runOnIdle {data.value=JSONObject(v.toString()).also {it.getJSONObject("eta").put("minutes",4)}}
  compose.onNodeWithText("약 4분").assertExists();compose.onNodeWithText("약 6분").assertDoesNotExist()
  compose.runOnIdle {data.value=JSONObject(data.value.toString()).put("stale",true)}
  compose.onNodeWithText("약 4분").assertDoesNotExist()
 }

 @Test fun registeredStopCoordinatesShowNativeTimingProvenance() {
  val v=fixture().put("source","PLEOS_NAVI")
  v.getJSONObject("eta").put("kind","PLEOS_REGISTERED_STOP_ESTIMATE").put("minutes",6)
  screen(mutableStateOf(v))
  compose.onNodeWithText("약 6분").assertExists()
  compose.onNodeWithText("Pleos 예상 시간 · 등록한 하차 지점 기준").assertExists()
 }
}
