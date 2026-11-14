package com.hanhyo.shuttle.parent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

@Composable internal fun ScheduleCalendar(selected:LocalDate,choose:(LocalDate)->Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(selected).toString()) }
    val month=YearMonth.parse(monthText)
    Surface(color=Majung.Card,shape=RoundedCornerShape(20.dp),modifier=Modifier.testTag("schedule-calendar")) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                IconButton(onClick={monthText=month.minusMonths(1).toString()}) {DesignAsset("6fae5.svg","이전 달",Modifier.size(20.dp).rotate(180f))}
                Text("${month.year}년 ${month.monthValue}월",style=MaterialTheme.typography.titleMedium)
                IconButton(onClick={monthText=month.plusMonths(1).toString()}) {DesignAsset("6fae5.svg","다음 달",Modifier.size(20.dp))}
            }
            Row { listOf("일","월","화","수","목","금","토").forEach { Text(it,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=Majung.Secondary,textAlign=androidx.compose.ui.text.style.TextAlign.Center) } }
            val offset=month.atDay(1).dayOfWeek.value%7
            val weeks=(offset+month.lengthOfMonth()+6)/7
            repeat(weeks) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val day=week*7+column-offset+1
                        if(day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(44.dp))
                        else {
                            val date=month.atDay(day);val active=date==selected;val today=date==LocalDate.now()
                            Box(Modifier.weight(1f).height(44.dp).clip(CircleShape).selectable(active,onClick={choose(date)},role=Role.RadioButton)
                                .testTag("date-$date").semantics { contentDescription="${date.monthValue}월 ${day}일${if(today) ", 오늘" else ""}" },contentAlignment=Alignment.Center) {
                                Surface(shape=CircleShape,color=if(active)Majung.Yellow else Majung.Card,border=if(today&&!active) BorderStroke(1.dp,Majung.Border) else null) {
                                    Box(Modifier.size(36.dp),contentAlignment=Alignment.Center) {Text(day.toString(),fontWeight=if(active)FontWeight.Bold else FontWeight.Normal)}
                                }
                            }
                        }
                    }
                }
            }
            TextButton(onClick={monthText=YearMonth.now().toString();choose(LocalDate.now())},modifier=Modifier.align(Alignment.End)) {Text("오늘",color=Majung.Text)}
        }
    }
}

@Composable internal fun AttendancePanel(id:String,name:String,date:LocalDate,direction:SchoolDirection,tools:JSONObject?,busy:Boolean,actions:GuardianActions,close:()->Unit) {
    var type by rememberSaveable(id,date.toString(),direction) { mutableStateOf("") }
    var note by rememberSaveable(id,date.toString(),direction) { mutableStateOf("") }
    var noteOpen by rememberSaveable { mutableStateOf(false) }
    val past=date.isBefore(LocalDate.now())
    Text("${date.monthValue}월 ${date.dayOfMonth}일 · $name",style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
    Surface(color=Majung.Pale,shape=RoundedCornerShape(12.dp)) {
        Text(if(past) "지난 날짜의 변경 요청을 확인해 주세요." else "변경 가능 여부는 기관 확인 후 반영돼요.\n긴급한 변경은 기관에 직접 알려 주세요.",Modifier.fillMaxWidth().padding(12.dp),style=MaterialTheme.typography.bodySmall)
    }
    val requests=tools?.optJSONArray("requests")?.objects().orEmpty().filter { it.optString("serviceDate").serviceDateText()==date.toString()&&it.optString("direction")==direction.api }
    requests.forEach { Text("${requestTypeLabel(it.optString("requestType"))} · ${requestStatusLabel(it.optString("status"))}",style=MaterialTheme.typography.bodySmall) }
    if(!past) {
        listOf("NOT_RIDING" to "차량을 이용하지 않아요","ABSENT" to "오늘 결석해요","SELF_PICKUP" to "직접 데리러 가요").forEach { (value,label) ->
            ChoiceRow(label,type==value,!busy,Modifier.testTag("request-$value")) { type=value }
        }
        TextButton(onClick={noteOpen=!noteOpen}) {Text(if(noteOpen) "메모 접기" else "+ 메모 남기기 · 선택",color=Majung.Secondary)}
        if(noteOpen) NoteField(note,{note=it},"기관에 전달할 메모","내용을 입력해 주세요")
        NoteButton("변경 요청 보내기",!busy&&type.isNotBlank(),Modifier.testTag("save-attendance")) {actions.attendance(id,date.toString(),direction.api,type,note,close)}
    }
}

@Composable internal fun ChoiceRow(label:String,selected:Boolean,enabled:Boolean=true,modifier:Modifier=Modifier,action:()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=if(selected)Majung.Pale else Majung.Card,
        border=BorderStroke(1.dp,if(selected)Majung.Yellow else Majung.Border)) {
        Row(Modifier.selectable(selected,onClick=action,enabled=enabled,role=Role.RadioButton).heightIn(min=60.dp).padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(label,Modifier.weight(1f),color=if(enabled)Majung.Text else Majung.Secondary)
            Box(Modifier.size(18.dp),contentAlignment=Alignment.Center) {
                DesignAsset("cdec0.svg",null,Modifier.fillMaxSize())
                if(selected) DesignAsset("3af5e.svg",null,Modifier.size(10.dp))
            }
        }
    }
}

@Composable internal fun PickupPanel(id:String,date:LocalDate,direction:SchoolDirection,tools:JSONObject?,busy:Boolean,actions:GuardianActions,changeDirection:(SchoolDirection)->Unit,register:()->Unit,close:()->Unit) {
    val validTools=tools?.takeIf { it.optString("serviceDate")==date.toString() }
    val current=validTools?.optJSONObject(if(direction==SchoolDirection.Morning)"pickUp" else "dropOff")
    var choice by rememberSaveable(id,date.toString(),direction,current?.toString()) { mutableStateOf<String?>(current?.optString("pickupPersonId")?.takeIf { it.isNotBlank()&&it!="null" }) }
    var chosen by rememberSaveable(id,date.toString(),direction) { mutableStateOf(false) }
    Text("${date.monthValue}월 ${date.dayOfMonth}일의 마중 보호자",color=Majung.Secondary)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { SchoolDirection.entries.forEach { dir->
        FilterChip(selected=direction==dir,onClick={changeDirection(dir)},label={Text(dir.label)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Majung.Pale))
    } }
    val past=date.isBefore(LocalDate.now())
    if(current!=null) Text("현재 담당: ${current.optString("personName").ifBlank { "등록된 정보 없음" }}",style=MaterialTheme.typography.bodySmall)
    ChoiceRow("나 · 보호자",choice==null,!busy&&!past) {choice=null;chosen=true}
    val persons=validTools?.optJSONArray("persons")?.objects().orEmpty()
    persons.filter { it.optString("status")=="APPROVED" }.forEach { person->
        ChoiceRow("${person.optString("name")} · ${person.optString("relationship")}",choice==person.optString("id"),!busy&&!past) {choice=person.optString("id");chosen=true}
    }
    Text("대리 보호자는 기관 승인 후 선택할 수 있어요.",style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
    if(!past) {
        NoteRow("대리 보호자 등록","이름과 관계를 기관에 전달해요",action=register)
        NoteButton("마중 보호자 저장",!busy&&chosen&&validTools!=null) {actions.pickup(id,date.toString(),direction.api,choice,close)}
    }
}

@Composable internal fun PersonsPanel(id:String,tools:JSONObject?,busy:Boolean,actions:GuardianActions) {
    var name by rememberSaveable { mutableStateOf("") };var relationship by rememberSaveable { mutableStateOf("") }
    Text("기관 승인이 완료되면 날짜별 마중 보호자로 지정할 수 있어요.",style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
    tools?.optJSONArray("persons")?.objects().orEmpty().forEach {person->
        Surface(color=Majung.Card,shape=RoundedCornerShape(16.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("${person.optString("name")} · ${person.optString("relationship")}",style=MaterialTheme.typography.titleSmall)
            Text(requestStatusLabel(person.optString("status")),style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
        } }
    }
    NoteField(name,{name=it},"보호자 이름","이름 입력")
    NoteField(relationship,{relationship=it},"아이와의 관계","예: 할머니")
    NoteButton("등록 신청",!busy&&name.isNotBlank()&&relationship.isNotBlank()) {actions.registerPerson(id,name,relationship)}
}

@Composable internal fun InvitePanel(busy:Boolean,accept:(String)->Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    Text("기관에서 받은 초대 코드를 입력해 주세요.\n초대받은 이메일 계정으로 연결할 수 있어요.",color=Majung.Secondary)
    NoteField(code,{code=it},"초대 코드","초대 코드 입력",Modifier.testTag("parent-code"))
    NoteButton("아이 연결",!busy&&code.isNotBlank(),Modifier.testTag("parent-accept")) {accept(code)}
}
