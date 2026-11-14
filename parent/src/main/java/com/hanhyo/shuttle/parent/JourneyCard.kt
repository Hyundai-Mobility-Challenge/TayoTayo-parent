package com.hanhyo.shuttle.parent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable internal fun JourneyCard(data:JSONObject?,direction:SchoolDirection) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) {now=System.currentTimeMillis();delay(1000)} }
    val state=data?.optString("journeyStatus")
    val complete=state in listOf("COMPLETED","HANDED_OVER")
    val arrived=state=="ARRIVED"
    val ended=state=="TRIP_ENDED"
    val active=data?.optBoolean("available")==true
    val connectionError=data?.optBoolean("connectionError")==true
    val eta=data?.optJSONObject("eta")?.takeIf { now-it.optLong("receivedAt") in 0..JOURNEY_FRESHNESS_MS && !connectionError && !complete&&!arrived&&!ended }
    val stale=data?.optBoolean("stale")==true||now-(data?.optLong("clientReceivedAt")?:0)>30_000
    if(data==null||!active) {
        EmptyNote(if(connectionError) "연결을 확인해 주세요" else if(data==null) "운행 소식을 확인해요" else "오늘의 출발을 기다려요",data?.optString("reason")?:"연결되면 자동으로 갱신됩니다.")
        return
    }
    val title=when {state=="HANDED_OVER"->"보호자 인계 완료";complete->"하차했어요";ended->"운행이 종료됐어요";arrived->"정류장에 도착했어요";connectionError->"서버에 연결하지 못했어요";eta!=null->"약 ${eta.optInt("minutes")}분 후 도착";else->"예상 시간을 확인하고 있어요"}
    Surface(shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,Majung.Border),color=Majung.Card,modifier=Modifier.testTag("home-journey")) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().background(Majung.Pale).padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(when {complete->"오늘의 ${direction.label} 기록";arrived->"도착 · 인계 대기";ended->"기록 확인 필요";else->"${direction.label} 운행 중"},style=MaterialTheme.typography.bodySmall)
                    Text(title,fontSize=23.sp,lineHeight=31.sp,fontWeight=FontWeight.Bold)
                    Text(data.optJSONObject("pickup")?.optString("name").orEmpty(),style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
                }
                DesignAsset("08de1.png",null,Modifier.padding(start=8.dp).size(64.dp))
            }
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("운행 진행",style=MaterialTheme.typography.titleSmall)
                JourneyStops(data,stale||complete||arrived||ended)
                if(!data.optBoolean("routeAvailable",true)) Text("현재는 우리 아이 정류장만 확인할 수 있어요.",style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
                if(!complete&&!arrived&&!ended) {
                    val etaReason=if(connectionError) "휴대폰에서 서버에 연결하지 못했어요. 다시 확인해 주세요." else if(eta==null) data.optString("etaReason").ifBlank { "예상 시간 갱신이 지연되고 있어요." } else ""
                    if(etaReason.isNotBlank()) Text(etaReason,Modifier.testTag("journey-eta-reason"),style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
                }
                Text(when {state=="HANDED_OVER"->"선생님이 보호자 인계를 기록했어요.";complete->"하차 기록이에요. 보호자 인계 여부는 별도로 확인해 주세요.";ended->"차량 운행은 끝났지만 우리 아이의 하차 기록은 아직 확인되지 않았어요.";arrived->"주변을 확인하고 선생님 안내에 따라 인계받아 주세요.";stale->"위치 갱신 대기 · 마지막으로 확인한 정보예요.";else->"예상 도착 시간은 교통 상황에 따라 달라질 수 있어요."},style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
            }
        }
    }
}

@Composable internal fun JourneyStops(data:JSONObject,paused:Boolean) {
    val stops=data.optJSONArray("progress")?.objects().orEmpty()
    val timeline=data.optJSONObject("timeline")
    val current=(timeline?.optInt("current")?:0).coerceIn(0,(stops.size-1).coerceAtLeast(0))
    // A location marker moves only when the server provides actual route progress.
    val position=current+(timeline?.optDouble("fraction",0.0)?:0.0).coerceIn(0.0,1.0).toFloat()
    val animated by animateFloatAsState(position,animationSpec=tween(if(paused)0 else 850),label="route-progress")
    Box(Modifier.fillMaxWidth()) {
        Column {
            stops.forEachIndexed { index,stop ->
                Row(Modifier.fillMaxWidth().heightIn(min=68.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.width(40.dp).height(68.dp),contentAlignment=Alignment.Center) {
                        if(stops.size>1) Box(Modifier.align(if(index==0)Alignment.BottomCenter else if(index==stops.lastIndex)Alignment.TopCenter else Alignment.Center).width(3.dp).height(if(index==0||index==stops.lastIndex)34.dp else 68.dp).background(Majung.Capsule))
                        Surface(Modifier.size(22.dp),color=if(stop.optBoolean("visited"))Majung.Text else Majung.Card,shape=CircleShape,border=BorderStroke(1.dp,Majung.Border)) {
                            Box(contentAlignment=Alignment.Center) {
                                if(stop.optBoolean("visited")) DesignAsset("85340.svg",null,Modifier.size(14.dp))
                                else if(stop.optBoolean("own")||index==stops.lastIndex) DesignAsset("3eda5.svg",null,Modifier.size(18.dp))
                            }
                        }
                    }
                    Column(Modifier.weight(1f).padding(start=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(stop.optString("name"),style=MaterialTheme.typography.titleSmall)
                        Text(if(stop.optBoolean("visited")) "지나왔어요" else if(stop.optBoolean("own")||index==stops.lastIndex) "우리 아이 정류장" else "다음 정류장",style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
                    }
                }
            }
        }
        if(stops.size>1&&!paused&&timeline?.optBoolean("paused")!=true) {
            Box(Modifier.offset(x=6.dp,y=(animated*68+20).dp).size(28.dp).clip(CircleShape).background(Majung.Yellow),contentAlignment=Alignment.Center) {
                DesignAsset("cb96d.svg",null,Modifier.size(16.dp))
            }
        }
    }
}
