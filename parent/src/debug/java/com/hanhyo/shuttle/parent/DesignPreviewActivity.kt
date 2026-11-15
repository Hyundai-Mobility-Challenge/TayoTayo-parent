package com.hanhyo.shuttle.parent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Debug-only isolated fixtures: never reads an account or sends a server request. */
class DesignPreviewActivity:ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))
        setContent { MajungTheme {
            var scenario by remember { mutableIntStateOf(0) }
            var calls by remember { mutableStateOf(false) }
            var response by remember { mutableStateOf<String?>(null) }
            var info by remember { mutableStateOf("") }
            val direction=if(scenario==1)SchoolDirection.Morning else SchoolDirection.Afternoon
            val children=remember(direction) { previewChildren().map { it.put("vehicle","${direction.label} 2호차") } }
            val journey=remember(scenario) { previewJourney(scenario) }
            val tools=remember { mutableStateMapOf<String,JSONObject>() }
            Column(Modifier.fillMaxSize()) {
                Surface(color=Majung.Pale) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        TextButton(onClick={scenario=(scenario+1)%3}) {Text("시연 데이터 · ${listOf("하원 운행","등원 운행","하차 완료")[scenario]}",color=Majung.Text)}
                        TextButton(onClick={calls=true;response=null}) {Text("호출 시연",color=Majung.Text)}
                    }
                }
                Box(Modifier.weight(1f)) { GuardianApp(children,mapOf("preview-child" to journey,"preview-sibling" to journey),
                    mapOf("preview-child" to direction,"preview-sibling" to direction),tools,emptyList(),message=info,pushStatus="디자인 시연 · 실제 알림을 발송하지 않아요",
                    actions=GuardianActions(loadTools={id,date->tools[id]=JSONObject().put("serviceDate",date).put("persons",JSONArray("""[{"id":"preview-person","name":"김보호","relationship":"할머니","status":"APPROVED"}]""")).put("requests",JSONArray())},
                        attendance={_,_,_,_,_,done->info="시연: 변경 요청을 접수했어요. 실제 일정은 변경하지 않았어요.";done()},
                        pickup={_,_,_,_,done->info="시연: 마중 보호자를 저장했어요.";done()},
                        registerPerson={_,_,_->info="시연: 기관 승인 대기 상태예요."},invite={info="시연에서는 실제 아이를 연결하지 않아요."},
                        share={_,_->info="시연에서는 실제 공유 링크를 만들지 않아요."},logout={finish()})) }
            }
            if(calls) ModalBottomSheet(onDismissRequest={calls=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Majung.App) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("시연 데이터 · 실제 응답을 보내지 않아요",style=MaterialTheme.typography.bodySmall)
                    CallResponsePanel("김하늘 · 햇살어린이집 · 등원 1호차","은행나무공원 앞 · 호출 시연",response=response,onRespond={response=it})
                }
            }
        } }
    }
}

internal fun previewChildren()=listOf(
    JSONObject("""{"id":"preview-child","name":"김하늘","institution":"햇살어린이집","vehicle":"하원 2호차","className":"햇님반"}"""),
    JSONObject("""{"id":"preview-sibling","name":"김바다","institution":"햇살어린이집","vehicle":"하원 1호차","className":"달님반"}"""))

internal fun previewJourney(scenario:Int=0):JSONObject {
    val now=System.currentTimeMillis()
    // Screenshot-matched fixtures only; these stops are not returned by the guardian API.
    val row=JSONObject().put("tripId","preview-trip").put("tripStatus","IN_PROGRESS")
        .put("childStatus",if(scenario==2)"DROPPED_OFF" else "BOARDED").put("stopName","화랑공원 앞")
    val journey=normalizeJourney(row,if(scenario==1)SchoolDirection.Morning else SchoolDirection.Afternoon)
    journey.put("stale",false).put("source","PREVIEW").put("eta",JSONObject().put("minutes",5).put("receivedAt",now))
        .put("routeAvailable",true)
        .put("progress",JSONArray("""[{"name":"촬영용 출발","visited":true},{"name":"판교도서관 앞","visited":false},{"name":"판교역 앞","visited":false},{"name":"화랑공원 앞","visited":false,"own":true}]"""))
        .put("timeline",JSONObject().put("current",0).put("fraction",0.65).put("paused",false))
    if(scenario==2) {
        journey.put("eta",JSONObject.NULL)
        journey.optJSONArray("progress")?.objects()?.forEach { it.put("visited",true) }
        journey.put("timeline",JSONObject().put("current",3).put("fraction",0).put("paused",true))
    }
    return journey
}
