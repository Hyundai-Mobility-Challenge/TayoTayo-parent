package com.hanhyo.shuttle.parent

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable fun TrackingCard(vm:ParentModel,childId:String) {
    val context=LocalContext.current
    TrackingContent(vm.tracking[childId],vm.foreground,vm.busy,
        onShare={tripId->vm.shareTracking(childId,tripId){link->
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain";putExtra(Intent.EXTRA_TEXT,"오늘의 픽업 상황을 확인해 주세요. 인계는 기관 확인이 필요합니다.\n$link")
            },"오늘 픽업 상황 공유"))
        }},onRevoke={tripId->vm.revokeTracking(childId,tripId)})
}

@Composable internal fun TrackingContent(data:JSONObject?,foreground:Boolean,busy:Boolean,
    onShare:(String)->Unit,onRevoke:(String)->Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(foreground) { while(foreground){now=System.currentTimeMillis();delay(1000)} }
    val completed=foreground&&data?.optString("journeyStatus")=="COMPLETED"
    val arrived=foreground&&data?.optString("journeyStatus")=="ARRIVED"
    val active=foreground&&data?.optBoolean("available")==true
    val elapsed=((now-(data?.optLong("clientReceivedAt",now)?:now)).coerceAtLeast(0))/1000.0
    val fix=data?.optJSONObject("location")
    val age=fix?.let{data.optDouble("serverTime")+elapsed-it.optDouble("receivedAt")/1000}
    val stale=active&&(data?.optBoolean("stale")==true||elapsed>30||age==null||age>30)
    val observed=data?.optJSONObject("timeline")?.optJSONObject("ownEta")?:data?.optJSONObject("eta")
    val eta=observed?.takeIf{active&&!stale&&!arrived&&data!!.optDouble("serverTime")+elapsed-it.optDouble("receivedAt")/1000<=30}
    val simulated=active&&data?.optString("source")=="SIMULATED"
    val status=when { data==null||!foreground->"연결 확인";completed->"하차 완료";arrived->"도착 · 인계 대기";stale->"갱신 대기";active->"이동 중";else->"운행 대기" }
    val title=when {completed->"오늘도 하원을 마쳤어요";arrived->"픽업 장소에 도착했어요";active&&eta!=null->"약 ${eta.optInt("minutes")}분";active->"도착 시간을 확인하고 있어요";data==null||!foreground->"운행 소식을 불러오고 있어요";else->"출발 소식을 기다리고 있어요"}
    Spacer(Modifier.height(4.dp))
    Surface(Modifier.fillMaxWidth().testTag("journey-summary"),shape=RoundedCornerShape(14.dp),color=if(completed)Color(0xFFF0F5FF) else Mint) {
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                StatusPill(if(completed) "완료" else if(arrived) "도착" else if(active&&!stale) "ON" else "대기",completed)
                Text(if(simulated) "$status · 시연" else status,fontSize=13.sp,color=Ink,fontWeight=FontWeight.SemiBold)
            }
            Text(title,fontSize=if(eta!=null)27.sp else 20.sp,fontWeight=FontWeight.Bold,color=Ink,
                modifier=Modifier.testTag("journey-title"))
            val subtitle=when {
                completed->"${guardianClock(data!!.optLong("completedAt",now))} · 선생님이 하차를 기록했어요."
                arrived->"선생님의 확인에 따라 아이를 인계받아 주세요."
                stale->"연결을 확인하고 있어요. 마지막 진행 정보입니다."
                eta!=null->"우리 아이 하차 지점까지 남은 예상 시간"
                active->"차량이 하차 지점으로 이동하고 있어요."
                else->data?.optString("reason")?.takeIf{it.isNotBlank()}?:"연결되면 자동으로 갱신됩니다."
            }
            Text(subtitle,fontSize=13.sp,color=Muted)
        }
    }
    if(active&&!arrived&&!completed&&data?.isNull("startedAt")==false) {
        Text("${guardianClock(data.optLong("startedAt"))} 하원을 시작했어요",fontSize=12.sp,color=Muted,modifier=Modifier.testTag("journey-departed"))
    }
    if(eta?.optString("kind")=="PLEOS_REGISTERED_STOP_ESTIMATE") Text("Pleos 예상 시간 · 등록한 하차 지점 기준",fontSize=11.sp,color=Muted)
    if(eta?.optString("kind") in listOf("PLEOS_WAYPOINT_ESTIMATE","PLEOS_REGISTERED_STOP_ESTIMATE")) Text("Pleos에서 받은 남은 시간이에요. 새 내비 정보가 오면 갱신돼요.",fontSize=11.sp,color=Muted)
    Spacer(Modifier.height(12.dp))
    if(completed||arrived||active) {
        key(data!!.optString("tripId")) { GuardianStopLine(data,elapsed,stale,completed,arrived) }
        if(completed) {
            Text("오늘의 위치 공유는 종료됐어요. 완료 기록은 이 화면에서 확인할 수 있어요.",fontSize=12.sp,color=Muted,
                modifier=Modifier.testTag("journey-completed"))
        } else {
            Text(if(simulated) "시연용 진행과 예상 시간이에요." else "이동 중 버스 표시는 예상 시간 기준이며, 도착은 선생님의 확인으로 표시돼요.",fontSize=12.sp,color=Muted)
            if(simulated&&eta?.optString("kind")=="SIMULATED_SCHEDULE") Text("이후 구간 90초 · 정차 20초를 가정한 시연입니다.",fontSize=11.sp,color=Muted)
            Spacer(Modifier.height(10.dp))
            if(active) {
            val trip=data!!.getString("tripId")
            Button(onClick={onShare(trip)},enabled=!busy&&!data.isNull("remainingStops"),
                modifier=Modifier.fillMaxWidth().heightIn(min=50.dp).testTag("tracking-share"),shape=RoundedCornerShape(15.dp),
                colors=ButtonDefaults.buttonColors(containerColor=Green,contentColor=Ink)) { Text("픽업 상황 공유",fontSize=15.sp,fontWeight=FontWeight.SemiBold) }
            Text("앱 없이 볼 수 있어요 · 아이 인계는 기관 확인이 필요해요",fontSize=11.sp,color=Muted)
            if((data.optJSONArray("shares")?.length()?:0)>0) TextButton(onClick={onRevoke(trip)},enabled=!busy,
                modifier=Modifier.testTag("tracking-revoke")) { Text("공유 링크 모두 종료",color=Muted) }
            } else Text("선생님이 하차를 기록하면 완료 상태로 바뀌어요.",fontSize=12.sp,color=Muted)
        }
    } else if(foreground&&data?.optJSONObject("pickup")!=null) {
        Text("우리 아이 하차 지점",fontSize=12.sp,color=Muted)
        Text(data.getJSONObject("pickup").optString("name"),fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=Ink,
            modifier=Modifier.testTag("waiting-pickup"))
        Text("차량에서 탑승과 출발을 기록하면 여기에 진행 상황이 나타나요.",fontSize=12.sp,color=Muted)
    }
}
