package com.hanhyo.shuttle.parent

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private lateinit var model: ParentModel
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { model.readPushStatus() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Push.channel(this)
        model = ViewModelProvider(this)[ParentModel::class.java]
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Ink, background = Paper)) {
                ParentScreen(model) { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
    }
    override fun onStart() { super.onStart(); model.foreground = true }
    override fun onStop() { model.foreground = false; super.onStop() }
    override fun onResume() { super.onResume(); model.refresh() }
}

@Composable fun ParentScreen(vm: ParentModel, notifications: () -> Unit) {
    var server by rememberSaveable { mutableStateOf(vm.base) }
    var join by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Password is deliberately not saved in instance state.
    var password by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf(false) }
    var inviteOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var managedChildId by rememberSaveable { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    var verificationCode by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.requestInitialNotification(notifications) }
    LaunchedEffect(vm.loggedIn) {
        if (vm.loggedIn) { password = "" }
        while (vm.loggedIn) { vm.readPushStatus(); delay(2000) }
    }
    LaunchedEffect(vm.loggedIn, vm.foreground) {
        while (vm.loggedIn && vm.foreground) { vm.refreshTracking(); delay(5000) }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            GuardianHeader(if(vm.loggedIn) ({ settings=true }) else null)
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.message.isNotEmpty()) Text(vm.message, modifier = Modifier.testTag("parent-message"))
            if (!vm.loggedIn) {
                Text("기관에서 받은 초대를 연결하면 승하차 기록을 확인하고 하차 알림을 받을 수 있습니다.")
                OutlinedTextField(server, { server = it }, label = { Text("서버 주소 · 개발용") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("parent-server"), enabled = !vm.busy)
                if (join) OutlinedTextField(name, { name = it }, label = { Text("보호자 이름") }, modifier = Modifier.fillMaxWidth().testTag("parent-name"))
                OutlinedTextField(email, { email = it }, label = { Text("초대받은 이메일") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("parent-email"))
                OutlinedTextField(password, { password = it }, label = { Text("비밀번호 · 8자 이상") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("parent-password"))
                if (join) OutlinedTextField(verificationCode, { verificationCode = it }, label = { Text("이메일 인증번호 · 개발 환경에서는 화면 메시지에 표시") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.authenticate(join, server, name, email, password, verificationCode) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().testTag("parent-auth")) { Text(if (join) if (verificationCode.isBlank()) "인증번호 받기" else "인증하고 가입" else "로그인") }
                TextButton(onClick = { join = !join }, enabled = !vm.busy) { Text(if (join) "기존 계정으로 로그인" else "처음이라면 보호자 가입") }
            } else {
                val children=vm.state.optJSONArray("children")
                val childCount=children?.length()?:0
                if(childCount==0) {
                    Text("어린이를 연결해 주세요",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Ink)
                    Text("기관에서 받은 초대 코드를 한 번 연결하면, 이후 하원 소식을 여기서 볼 수 있어요.",color=Muted)
                }
                repeat(childCount) { index ->
                    val child=children!!.getJSONObject(index)
                    val childId=child.getString("id")
                    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),
                        colors=CardDefaults.cardColors(containerColor=Color.White),border=BorderStroke(1.dp,Color(0xFFEBEDF0))) {
                        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            GuardianChildHeading(child.getString("name"),child.getString("institution"),child.getString("vehicle"))
                            TrackingCard(vm,childId)
                            TextButton(onClick={managedChildId=childId;vm.loadChildTools(childId)},modifier=Modifier.fillMaxWidth().testTag("manage-child-$childId")) {
                                Text("출결 · 픽업 정보 관리",color=Ink)
                            }
                        }
                    }
                }
                if(childCount>0) TextButton(onClick={inviteOpen=!inviteOpen},modifier=Modifier.testTag("open-invite")) {
                    Text(if(inviteOpen) "어린이 연결 닫기" else "+ 어린이 추가 연결",color=Muted)
                }
                if(childCount==0||inviteOpen) {
                    OutlinedTextField(code,{code=it},label={Text("기관에서 받은 초대 코드")},singleLine=true,
                        modifier=Modifier.fillMaxWidth().testTag("parent-code"),shape=RoundedCornerShape(14.dp))
                    Button(onClick={vm.accept(code)},enabled=!vm.busy&&code.isNotBlank(),
                        modifier=Modifier.fillMaxWidth().testTag("parent-accept"),shape=RoundedCornerShape(14.dp)) {Text("어린이 연결")}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={historyOpen=!historyOpen}) {Text(if(historyOpen) "최근 기록 접기" else "최근 하원 기록",color=Muted)}
                    TextButton(onClick=vm::refresh,enabled=!vm.busy,modifier=Modifier.testTag("parent-refresh")) {Text("새로고침",color=Ink)}
                }
                if(historyOpen) {
                    val events=vm.state.optJSONArray("events")
                    if(events==null||events.length()==0) Text("아직 등록된 기록이 없어요.",color=Muted)
                    else repeat(minOf(events.length(),6)) { index ->
                        val event=events.getJSONObject(index)
                        Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(event.getString("body"),fontSize=14.sp,color=Ink)
                            Text(guardianClock(event.getLong("recorded")),fontSize=12.sp,color=Muted)
                            HorizontalDivider(color=Color(0xFFE1E8E2))
                        }
                    }
                }
            }
        }
    }
    if(settings) AlertDialog(onDismissRequest={settings=false},title={Text("알림·설정")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(vm.pushStatus,modifier=Modifier.testTag("parent-push-status"),color=Muted)
            TextButton(onClick=notifications) {Text("알림 권한 허용")}
            TextButton(onClick=vm::refresh,enabled=!vm.busy) {Text("연결 새로고침")}
            TextButton(onClick={settings=false;vm.logout()},enabled=!vm.busy) {Text("로그아웃 · 알림 연결 해제")}
        }},confirmButton={TextButton(onClick={settings=false}) {Text("닫기")}})
    val managedChild=managedChildId?.let { id ->
        val items=vm.state.optJSONArray("children")
        (0 until (items?.length()?:0)).map { items!!.getJSONObject(it) }.firstOrNull { it.optString("id")==id }
    }
    if(managedChild!=null) ChildManagementDialog(managedChild,vm) { managedChildId=null }
}

@Composable private fun ChildManagementDialog(child: org.json.JSONObject, vm: ParentModel, onClose: () -> Unit) {
    val childId=child.optString("id")
    var date by remember(childId) { mutableStateOf(LocalDate.now().toString()) }
    var direction by remember(childId) { mutableStateOf("DROP_OFF") }
    var requestType by remember(childId) { mutableStateOf("NOT_RIDING") }
    var reason by remember(childId) { mutableStateOf("") }
    var pickupName by remember(childId) { mutableStateOf("") }
    var pickupRelationship by remember(childId) { mutableStateOf("") }
    var validFrom by remember(childId) { mutableStateOf("") }
    var validTo by remember(childId) { mutableStateOf("") }
    val tools=vm.childTools[childId]
    val requests=tools?.optJSONArray("requests")
    val persons=tools?.optJSONArray("persons")
    AlertDialog(onDismissRequest=onClose,title={Text("${child.optString("name")} 정보 관리",fontWeight=FontWeight.Bold)},
        text={Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("${child.optString("institution")} · ${child.optString("className").ifBlank { "반 미배정" }}",fontSize=13.sp,color=Muted)
            Text("아이 이름과 반 정보는 기관에서 관리합니다. 보호자 앱에서는 연결된 아이의 출결 요청과 픽업 정보를 관리할 수 있어요.",fontSize=12.sp,color=Muted)
            HorizontalDivider()
            Text("출결·하원 변경 요청",fontSize=15.sp,fontWeight=FontWeight.Bold,color=Ink)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=direction=="PICK_UP",onClick={direction="PICK_UP"},label={Text("등원")})
                FilterChip(selected=direction=="DROP_OFF",onClick={direction="DROP_OFF"},label={Text("하원")})
            }
            OutlinedTextField(date,{date=it},label={Text("대상 날짜 · YYYY-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("요청 종류",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Ink)
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                listOf("NOT_RIDING" to "오늘 셔틀을 타지 않아요", "ABSENT" to "오늘 결석해요", "SELF_PICKUP" to "직접 데리러 갈게요").forEach { (value,label) ->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected=requestType==value,onClick={requestType=value})
                        Text(label,fontSize=12.sp,color=Ink)
                    }
                }
            }
            OutlinedTextField(reason,{reason=it},label={Text("메모 · 선택")},modifier=Modifier.fillMaxWidth(),maxLines=2)
            Button(onClick={vm.createAttendanceRequest(childId,date,direction,requestType,reason)},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) { Text("변경 요청 보내기") }
            if(requests!=null && requests.length()>0) {
                Text("최근 요청",fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=Ink)
                repeat(minOf(requests.length(),4)) { i ->
                    val row=requests.getJSONObject(i)
                    Text("${row.optString("serviceDate")} · ${if(row.optString("direction")=="DROP_OFF") "등원" else "하원"} · ${requestTypeLabel(row.optString("requestType"))} · ${requestStatusLabel(row.optString("status"))}",fontSize=11.sp,color=Muted)
                }
            }
            HorizontalDivider()
            Text("오늘 픽업 담당자",fontSize=15.sp,fontWeight=FontWeight.Bold,color=Ink)
            Text("날짜 입력값 기준으로 등원·하원 담당자를 각각 지정합니다.",fontSize=11.sp,color=Muted)
            OutlinedTextField(date,{date=it},label={Text("픽업 날짜 · YYYY-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth())
            listOf("PICK_UP" to "등원", "DROP_OFF" to "하원").forEach { (dir,label) ->
                val current=tools?.optJSONObject(if(dir=="DROP_OFF") "dropOff" else "pickUp")
                Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFF8F9FA)),shape=RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text("$label 담당 · ${current?.optString("personName").takeUnless { it.isNullOrBlank() } ?: "나(보호자)"}",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Ink)
                        OutlinedButton(onClick={vm.setDailyPickup(childId,date,dir,"GUARDIAN")},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) { Text("나를 $label 담당자로 설정") }
                        if(persons!=null) repeat(persons.length()) { i ->
                            val person=persons.getJSONObject(i)
                            if(person.optString("status")=="APPROVED") OutlinedButton(onClick={vm.setDailyPickup(childId,date,dir,"AUTHORIZED_PERSON",person.optString("id"))},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {
                                Text("${person.optString("name")}에게 $label 맡기기")
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("대리 픽업 인원 등록",fontSize=15.sp,fontWeight=FontWeight.Bold,color=Ink)
            Text("등록 후 기관 승인이 완료되면 날짜별 픽업 담당자로 지정할 수 있습니다.",fontSize=11.sp,color=Muted)
            OutlinedTextField(pickupName,{pickupName=it},label={Text("이름")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(pickupRelationship,{pickupRelationship=it},label={Text("관계 · 예: 할머니")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(validFrom,{validFrom=it},label={Text("시작일")},singleLine=true,modifier=Modifier.weight(1f))
                OutlinedTextField(validTo,{validTo=it},label={Text("종료일")},singleLine=true,modifier=Modifier.weight(1f))
            }
            Button(onClick={vm.addAuthorizedPickupPerson(childId,pickupName,pickupRelationship,validFrom,validTo);pickupName="";pickupRelationship=""},enabled=!vm.busy&&pickupName.isNotBlank()&&pickupRelationship.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("대리 픽업 등록 신청") }
            if(persons!=null) repeat(persons.length()) { i ->
                val person=persons.getJSONObject(i)
                Text("${person.optString("name")} · ${person.optString("relationship")} · ${authorizedStatusLabel(person.optString("status"))}",fontSize=11.sp,color=Muted)
            }
            if(tools==null) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) { CircularProgressIndicator(Modifier.size(23.dp),strokeWidth=2.dp) }
        }},confirmButton={TextButton(onClick=onClose) { Text("닫기") }})
}

private fun requestTypeLabel(type:String)=when(type) { "NOT_RIDING"->"미탑승"; "ABSENT"->"결석"; "SELF_PICKUP"->"직접 픽업"; "STOP_CHANGE"->"정류장 변경"; "PICKUP_PERSON"->"픽업 담당 변경"; else->type }
private fun requestStatusLabel(status:String)=when(status) { "PENDING"->"확인 대기"; "APPROVED"->"승인"; "REJECTED"->"반려"; "CANCELLED"->"취소"; else->status }
private fun authorizedStatusLabel(status:String)=when(status) { "PENDING"->"기관 승인 대기"; "APPROVED"->"승인됨"; "REJECTED"->"반려"; else->status }
