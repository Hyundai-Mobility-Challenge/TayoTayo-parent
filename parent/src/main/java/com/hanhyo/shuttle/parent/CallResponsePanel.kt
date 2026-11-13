package com.hanhyo.shuttle.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** UI contract only. A production sender must be supplied after the call API is agreed. */
@Composable internal fun CallResponsePanel(childLabel:String,stopLabel:String,busy:Boolean=false,expired:Boolean=false,
    response:String?=null,onRespond:((String)->Unit)?=null) {
    Text("보호자 호출",style=MaterialTheme.typography.titleLarge)
    Text(childLabel,style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        Box(Modifier.size(96.dp,72.dp)) {
            DesignAsset("d9efa.svg",null,Modifier.offset(24.8.dp,9.6.dp).size(46.4.dp))
            DesignAsset("2c437.png",null,Modifier.offset(27.2.dp,12.dp).size(41.6.dp))
            DesignAsset("87a27.svg",null,Modifier.offset(23.2.dp,59.2.dp).size(50.4.dp,2.88.dp))
        }
    }
    Text(when {expired->"종료된 호출이에요";response!=null->"응답을 전달했어요";else->"차량이 기다리고 있어요"},style=MaterialTheme.typography.titleMedium)
    Text(stopLabel,color=Majung.Secondary)
    val enabled=onRespond!=null&&!busy&&!expired&&response==null
    NoteButton("곧 나가요",enabled,Modifier.testTag("call-coming")) {onRespond?.invoke("COMING")}
    NoteButton("오늘 안 타요",enabled,Modifier.testTag("call-not-riding"),secondary=true) {onRespond?.invoke("NOT_RIDING")}
    Text(if(onRespond==null) "호출 응답 연결을 준비 중이에요. 지금은 기관에 직접 연락해 주세요."
        else "‘오늘 안 타요’는 선생님에게 전달하는 응답이에요.\n미탑승 확정과 하원 일정 변경은 별도예요.",style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
}
