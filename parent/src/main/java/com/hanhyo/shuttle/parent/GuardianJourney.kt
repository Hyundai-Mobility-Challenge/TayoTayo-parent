package com.hanhyo.shuttle.parent

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

internal val Ink = Color(0xFF252A32)
internal val Green = Color(0xFFFFCC00)
internal val Muted = Color(0xFF858B94)
internal val Mint = Color(0xFFFFF8D6)
internal val Paper = Color(0xFFF5F6F8)
internal fun guardianClock(at: Long) = SimpleDateFormat("a h:mm",Locale.KOREA).apply { timeZone=TimeZone.getTimeZone("Asia/Seoul") }.format(Date(at))

@Composable internal fun StatusPill(text: String, complete: Boolean = false) {
    Surface(color=if(complete) Color(0xFFEEF4FF) else Green, shape=RoundedCornerShape(50)) {
        Text(text,Modifier.padding(horizontal=11.dp,vertical=6.dp),color=if(complete) Color(0xFF4D72B7) else Ink,
            style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold)
    }
}

@Composable internal fun GuardianHeader(settings: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("통학메이트",fontSize=15.sp,fontWeight=FontWeight.Bold,color=Ink)
            Text("오늘의 하원",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Ink)
        }
        if(settings!=null) TextButton(onClick=settings) { Text("알림·설정",color=Muted) }
    }
}

@Composable internal fun GuardianChildHeading(name:String,institution:String,vehicle:String) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).background(Mint,RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center) {
            Text(name.removePrefix("가상 ").take(1),fontSize=21.sp,fontWeight=FontWeight.Bold,color=Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(name.removePrefix("가상 ")+"의 하원",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Ink)
            Text(listOf(institution,vehicle).filter{it.isNotBlank()}.joinToString(" · "),fontSize=12.sp,color=Muted)
        }
    }
}

/** Native schematic; no WebView, map SDK, tiles, coordinates or external requests. */
@Composable internal fun GuardianStopLine(data:JSONObject, elapsedSeconds:Double, stale:Boolean, completed:Boolean, arrived:Boolean) {
    val steps=data.optJSONArray("progress")
    val count=(steps?.length()?:1).coerceAtLeast(1)
    val timeline=data.optJSONObject("timeline")
    val current=(timeline?.optInt("current",0)?:0).coerceIn(0,count-1)
    val next=timeline?.optJSONObject("nextEta")
    val navAge=next?.let { data.optDouble("serverTime",0.0)+elapsedSeconds-it.optDouble("receivedAt",0.0)/1000 }
    val paused=stale || timeline?.optBoolean("paused",true)!=false || next==null || (navAge?:Double.MAX_VALUE)>30
    val seconds=if(paused) null else (next!!.optDouble("seconds",0.0)-elapsedSeconds).coerceAtLeast(0.0)
    val total=timeline?.optDouble("totalSeconds",0.0)?:0.0
    val observed=(timeline?.optDouble("fraction",0.0)?:0.0).let { if(it.isFinite())it.coerceIn(0.0,1.0) else 0.0 }
    val fraction=if(seconds!=null&&total>0) (1-seconds/total).coerceIn(0.0,.95) else observed.coerceAtMost(.95)
    val position=when {
        completed || arrived -> count.toFloat()
        data.optString("phase")=="ALIGHTING" -> (current+1).toFloat()
        else -> (current+fraction).toFloat()
    }.coerceIn(0f,count.toFloat())
    val animated by animateFloatAsState(position, tween(if(paused&&!arrived&&!completed)0 else 850,easing=FastOutSlowInEasing),label="bus-progress")
    val activeColor=if(paused&&!arrived&&!completed) Muted else Green
    val rowHeight=92
    Column(Modifier.fillMaxWidth().testTag("tracking-stop-timeline")) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("오늘의 하차 순서",fontSize=16.sp,fontWeight=FontWeight.Bold,color=Ink)
            Text(if(completed) "도착 완료" else if(arrived) "픽업 장소 도착" else "${count}곳",fontSize=12.sp,color=Muted)
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(((count+1)*rowHeight).dp)) {
            Canvas(Modifier.width(88.dp).fillMaxHeight()) {
                val x=60.dp.toPx();val top=24.dp.toPx();val step=rowHeight.dp.toPx()
                drawLine(Color(0xFFE3E5E8),Offset(x,top),Offset(x,top+count*step),3.dp.toPx(),
                    pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),5.dp.toPx())))
                drawLine(activeColor,Offset(x,top),Offset(x,top+animated*step),4.dp.toPx())
            }
            Column(Modifier.fillMaxWidth()) {
                repeat(count+1) { row ->
                    val own=row==count
                    val index=row-1
                    val step=if(index>=0)steps?.optJSONObject(index) else null
                    val visited=completed||row==0||step?.optBoolean("visited")==true
                    val atStop=own&&(completed||arrived)||row==current+1&&data.optString("phase")=="ALIGHTING"
                    Row(Modifier.fillMaxWidth().height(rowHeight.dp),verticalAlignment=Alignment.Top) {
                        Text(when {own&&completed->guardianClock(data.optLong("completedAt")).replace(" ","\n");visited->"완료";atStop->"도착";else->"예정"},
                            Modifier.width(40.dp).padding(top=16.dp),fontSize=10.sp,lineHeight=13.sp,color=Muted)
                        Box(Modifier.width(40.dp).padding(top=10.dp),contentAlignment=Alignment.Center) {
                            Surface(Modifier.size(28.dp),shape=RoundedCornerShape(50),color=if(visited||atStop)Green else Color.White,
                                border=BorderStroke(2.dp,if(visited||atStop)Green else Color(0xFFE0E3E7))) {
                                Box(contentAlignment=Alignment.Center) {Text(if(row==0) "•" else row.toString(),fontSize=13.sp,
                                    fontWeight=FontWeight.Bold,color=if(visited||atStop)Ink else Color(0xFFADB2B9))}
                            }
                        }
                        Column(Modifier.weight(1f).padding(start=12.dp,top=8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Text(if(row==0) "출발지" else if(own) data.optJSONObject("pickup")?.optString("name")?:"내 아이 하차 지점" else "정류장 $row",
                                fontSize=16.sp,fontWeight=FontWeight.Bold,color=if(visited||atStop||index==current)Ink else Color(0xFF8B919A))
                            when {
                                row==0 -> Text("오늘의 하원 출발",fontSize=12.sp,color=Muted)
                                own&&completed -> StatusPill("하차 완료",true)
                                own&&arrived -> StatusPill("도착 · 인계 대기")
                                visited -> StatusPill("방문 완료",true)
                                index==current&&data.optString("phase")=="ALIGHTING" -> StatusPill("정차 · 인계 중")
                                else -> Text(when {
                                    index==current&&seconds!=null&&seconds>0 -> "도착 예정 · 약 ${kotlin.math.ceil(seconds/60).toInt()}분"
                                    index==current&&seconds==0.0 -> "도착 확인 대기"
                                    own -> "우리 아이 하차 지점"
                                    index==current -> "현재 향하는 정류장"
                                    else -> "방문 예정"
                                },fontSize=12.sp,fontWeight=if(index==current)FontWeight.SemiBold else FontWeight.Normal,
                                    color=if(index==current)Color(0xFFA77B00) else Muted)
                            }
                            if(row<count) HorizontalDivider(Modifier.padding(top=7.dp),color=Color(0xFFF0F1F3))
                        }
                    }
                }
            }
            BusBadge(activeColor,Modifier.offset(x=40.dp,y=(4+animated*rowHeight).dp).size(40.dp)
                .testTag("journey-bus").semantics { contentDescription="하원 차량";stateDescription=when {completed->"하차 완료 지점";arrived->"내 아이 하차 지점 도착";paused->"진행 갱신 대기";else->"정류장 구간 진행 ${"%.2f".format(Locale.US,position)}"} })
        }
    }
}

@Composable private fun BusBadge(color:Color,modifier:Modifier) {
    Surface(modifier,shape=RoundedCornerShape(50),color=Color.White,border=BorderStroke(2.dp,color),shadowElevation=1.dp) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            drawRoundRect(color,Offset(size.width*.1f,0f),Size(size.width*.8f,size.height*.85f),CornerRadius(4.dp.toPx()))
            drawRoundRect(Color.White,Offset(size.width*.23f,size.height*.14f),Size(size.width*.54f,size.height*.34f),CornerRadius(1.dp.toPx()))
            drawCircle(Color.White,2.dp.toPx(),Offset(size.width*.3f,size.height*.65f))
            drawCircle(Color.White,2.dp.toPx(),Offset(size.width*.7f,size.height*.65f))
            drawLine(color,Offset(size.width*.25f,size.height*.8f),Offset(size.width*.25f,size.height),3.dp.toPx())
            drawLine(color,Offset(size.width*.75f,size.height*.8f),Offset(size.width*.75f,size.height),3.dp.toPx())
        }
    }
}
