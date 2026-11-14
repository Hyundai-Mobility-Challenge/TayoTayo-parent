package com.hanhyo.shuttle.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.LocalDate

internal data class GuardianActions(
    val loadTools:(String,String)->Unit={_,_->},
    val attendance:(String,String,String,String,String,()->Unit)->Unit={_,_,_,_,_,_->},
    val pickup:(String,String,String,String?,()->Unit)->Unit={_,_,_,_,_->},
    val registerPerson:(String,String,String)->Unit={_,_,_->},
    val invite:(String)->Unit={},val refresh:()->Unit={},val logout:()->Unit={},val notificationPermission:()->Unit={},
    val notificationSettings:()->Unit={},
    val share:(String,String)->Unit={_,_->},val revoke:(String,String)->Unit={_,_->}
)

internal enum class GuardianTab(val label:String,val basic:String,val selected:String) {
    Schedule("일정","96fd4.svg","5b4de.svg"),Home("홈","f707d.svg","9c552.svg"),Child("아이","1f3f8.svg","bde26.svg")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GuardianApp(children:List<JSONObject>,tracking:Map<String,JSONObject>,
    directions:Map<String,SchoolDirection>,tools:Map<String,JSONObject>,events:List<JSONObject>,busy:Boolean=false,
    message:String="",pushStatus:String="",openInbox:Boolean=false,onInboxConsumed:()->Unit={},actions:GuardianActions=GuardianActions()) {
    var tabName by rememberSaveable { mutableStateOf(GuardianTab.Home.name) }
    val tab=GuardianTab.valueOf(tabName)
    var selectedId by rememberSaveable { mutableStateOf("") }
    val child=children.firstOrNull { it.optString("id")==selectedId } ?: children.firstOrNull()
    val childId=child?.optString("id").orEmpty()
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val date=LocalDate.parse(dateText)
    var panel by rememberSaveable { mutableStateOf("") }
    var sheetDirection by rememberSaveable { mutableStateOf(SchoolDirection.Afternoon.name) }
    val direction=directions[childId]?:SchoolDirection.Afternoon
    var page by rememberSaveable { mutableStateOf("") }
    val contentScroll=remember(tabName,page,childId) { androidx.compose.foundation.ScrollState(0) }
    val snackbar=remember { SnackbarHostState() }
    LaunchedEffect(message) { if(message.isNotBlank()) snackbar.showSnackbar(message) }
    LaunchedEffect(childId,dateText) { if(childId.isNotBlank()) actions.loadTools(childId,dateText) }
    LaunchedEffect(openInbox) { if(openInbox) { page="inbox";onInboxConsumed() } }
    BackHandler(page.isNotEmpty()) { page="" }
    BackHandler(page.isEmpty()&&tab!=GuardianTab.Home) { tabName=GuardianTab.Home.name }
    fun openSheet(kind:String,dir:SchoolDirection,day:LocalDate) { dateText=day.toString();sheetDirection=dir.name;panel=kind }
    Scaffold(containerColor=Majung.App,snackbarHost={SnackbarHost(snackbar)},
        bottomBar={ if(page.isEmpty()) GuardianTabBar(tab) { tabName=it.name } },
        contentWindowInsets=WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal)) { inset ->
        Column(Modifier.fillMaxSize().padding(inset)) {
            if(page.isEmpty()) ChildHeader(child,onSelect={panel="children"},onInbox={page="inbox"},onConnect={panel="connect"})
            else Row(Modifier.fillMaxWidth().padding(horizontal=12.dp).height(64.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={page=""}) { DesignAsset("9c2c6.svg","뒤로",Modifier.size(24.dp)) }
                Text(if(page=="inbox") "알림" else "설정",style=MaterialTheme.typography.titleLarge)
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            Column(Modifier.weight(1f).verticalScroll(contentScroll).padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                when {
                    page=="inbox" -> {
                        Text("최근 운행 소식",style=MaterialTheme.typography.titleMedium)
                        Text("연결된 아이들의 운행 기록이에요.",style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
                        if(events.isEmpty()) EmptyNote("아직 소식이 없어요","새로운 운행 기록이 생기면 여기에 보여요.")
                        events.forEach { event ->
                            Surface(shape=RoundedCornerShape(16.dp),color=Majung.Card) {
                                Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    DesignAsset("c0659.svg",null,Modifier.size(24.dp))
                                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                        Text(event.optString("body"))
                                        if(event.optLong("recorded")>0) Text(guardianClock(event.optLong("recorded")),style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
                                    }
                                }
                            }
                        }
                        NoteRow("알림 설정",pushStatus) { page="settings" }
                    }
                    page=="settings" -> {
                        NoteTitle("소식을 놓치지 않도록")
                        Text(pushStatus,Modifier.testTag("parent-push-status"),color=Majung.Secondary)
                        NoteButton("알림 권한 확인",action=actions.notificationPermission)
                        NoteButton("기기 알림 설정 열기",secondary=true,action=actions.notificationSettings)
                        NoteButton("연결 새로고침",!busy,secondary=true,action=actions.refresh)
                        NoteRow("로그아웃","이 기기의 알림 연결도 해제합니다") { panel="logout" }
                    }
                    child==null -> {
                        NoteTitle("함께할 아이를 연결해요")
                        EmptyNote("아직 연결된 아이가 없어요","기관에서 받은 초대 코드를 입력하면\n우리 아이의 통학 소식을 볼 수 있어요.")
                        NoteButton("초대 코드 연결") { panel="connect" }
                        NoteButton("다시 확인",!busy,secondary=true,action=actions.refresh)
                    }
                    tab==GuardianTab.Home -> {
                        NoteTitle("오늘의 ${direction.label}")
                        JourneyCard(tracking[childId],direction)
                        if(tracking[childId]?.optBoolean("connectionError")==true) NoteButton("운행 소식 다시 확인",!busy,secondary=true,action=actions.refresh)
                        NoteRow("등하원 변경","오늘 ${direction.label} 이용 정보를 변경해요",Modifier.testTag("home-attendance")) { openSheet("attendance",direction,LocalDate.now()) }
                        NoteRow("오늘 누가 마중 나오나요?","보호자와 마중 정보를 확인해요",Modifier.testTag("home-pickup")) { openSheet("pickup",direction,LocalDate.now()) }
                        val trip=tracking[childId]
                        if(trip?.optBoolean("available")==true&&trip.optString("journeyStatus") !in listOf("COMPLETED","HANDED_OVER","TRIP_ENDED")) {
                            NoteButton("통학 상황 공유",!busy,secondary=true) { actions.share(childId,trip.optString("tripId")) }
                        }
                        if(trip != null && (trip.optJSONArray("shares")?.length() ?: 0) > 0) {
                            if(trip.optBoolean("shareCleanupError")) Text("공유 링크 종료를 다시 시도하고 있어요. 서버 연결을 확인해 주세요.",
                                Modifier.testTag("share-cleanup-pending"),style=MaterialTheme.typography.bodySmall,color=Majung.Error)
                            TextButton(onClick={actions.revoke(childId,trip.optString("tripId"))},enabled=!busy,
                                modifier=Modifier.testTag("home-revoke-share")) { Text("공유 링크 종료",color=Majung.Text) }
                        }
                        Text("운행 상황에 따라 등원·하원 화면이 자동으로 바뀌어요.",style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
                    }
                    tab==GuardianTab.Schedule -> {
                        NoteTitle("우리 아이 일정")
                        ScheduleCalendar(date) { dateText=it.toString() }
                        Text("${date.monthValue}월 ${date.dayOfMonth}일 · ${child.optString("name")}",style=MaterialTheme.typography.titleMedium)
                        if(date.isBefore(LocalDate.now())) Text("지난 일정은 확인만 할 수 있어요.",style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
                        SchoolDirection.entries.forEach { dir ->
                            val requests=tools[childId]?.optJSONArray("requests")?.objects().orEmpty().filter { it.optString("serviceDate").serviceDateText()==dateText&&it.optString("direction")==dir.api }
                            val recent=requests.firstOrNull()
                            NoteRow("${dir.label} 변경",recent?.let { requestTypeLabel(it.optString("requestType"))+" · "+requestStatusLabel(it.optString("status")) }?:"등록된 변경 요청 없음",Modifier.testTag("schedule-${dir.api}")) { openSheet("attendance",dir,date) }
                        }
                        NoteRow("마중 보호자","날짜별 등원·하원 보호자 지정") { openSheet("pickup",direction,date) }
                    }
                    tab==GuardianTab.Child -> {
                        NoteTitle("우리 아이")
                        Surface(shape=RoundedCornerShape(20.dp),color=Majung.Card) {
                            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(child.optString("name"),style=MaterialTheme.typography.titleLarge)
                                Text(listOf(child.optString("institution"),child.optString("className")).filter { it.isNotBlank() }.joinToString(" · "),color=Majung.Secondary)
                                Text("이름과 반 정보는 기관에서 관리해요.",style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
                            }
                        }
                        NoteRow("마중 보호자 관리","대리 보호자 등록 및 승인 상태 확인") { panel="persons" }
                        NoteRow("아이·기관 연결","아이 추가 또는 연결된 아이 선택") { panel="children" }
                        NoteRow("알림과 계정 설정","소식 수신 상태 · 로그아웃") { page="settings" }
                    }
                }
            }
        }
    }
    if(panel.isNotBlank()) {
        ModalBottomSheet(onDismissRequest={if(!busy) panel=""},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Majung.App,
            dragHandle={Box(Modifier.padding(top=10.dp).size(36.dp,4.dp).background(Majung.Border,RoundedCornerShape(2.dp)))}) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp).testTag("guardian-sheet"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                val title=when(panel) { "children"->"아이·기관 선택";"connect"->"기관 초대 연결";"attendance"->"${SchoolDirection.valueOf(sheetDirection).label} 변경";"pickup"->"마중 보호자";"persons"->"마중 보호자 관리";else->"로그아웃" }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    IconButton(onClick={panel=""},enabled=!busy) { DesignAsset("761ae.svg","닫기",Modifier.size(20.dp)) }
                }
                when(panel) {
                    "children" -> {
                        children.forEach { c -> NoteRow(c.optString("name"),c.optString("institution")) { selectedId=c.optString("id");panel="" } }
                        NoteButton("아이 추가 연결") { panel="connect" }
                    }
                    "connect" -> InvitePanel(busy,actions.invite)
                    "attendance" -> AttendancePanel(childId,child?.optString("name").orEmpty(),date,SchoolDirection.valueOf(sheetDirection),tools[childId],busy,actions) {panel=""}
                    "pickup" -> PickupPanel(childId,date,SchoolDirection.valueOf(sheetDirection),tools[childId],busy,actions,{sheetDirection=it.name},{panel="persons"}) {panel=""}
                    "persons" -> PersonsPanel(childId,tools[childId],busy,actions)
                    "logout" -> { Text("이 기기에서 로그아웃하고 알림 연결을 해제할까요?");NoteButton("로그아웃",!busy) { actions.logout();panel="" } }
                }
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                // Errors remain visible while the sheet is open; a failed request never closes it.
                if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
            }
        }
    }
}

@Composable internal fun ChildHeader(child:JSONObject?,onSelect:()->Unit,onInbox:()->Unit,onConnect:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=24.dp).heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick=onSelect).padding(vertical=8.dp).testTag("child-selector")) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(child?.optString("name")?:"아이 연결",fontSize=20.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,false))
                DesignAsset("bba34.svg",null,Modifier.padding(start=6.dp).size(18.dp))
            }
            Text(child?.let { listOf(it.optString("institution"),it.optString("vehicle")).filter(String::isNotBlank).joinToString(" · ") }?:"기관에서 받은 초대를 연결해요",style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        IconButton(onClick=onInbox,modifier=Modifier.size(48.dp)) { DesignAsset("c0659.svg","알림",Modifier.size(24.dp)) }
        IconButton(onClick=onConnect,modifier=Modifier.size(48.dp)) { DesignAsset("1cefd.svg","연결 관리",Modifier.size(24.dp)) }
    }
}

@Composable internal fun GuardianTabBar(tab:GuardianTab,select:(GuardianTab)->Unit) {
    Surface(color=Majung.Card) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=24.dp)) {
            GuardianTab.entries.forEach { item ->
                val selected=item==tab
                Box(Modifier.weight(1f).heightIn(min=68.dp).selectable(selected,onClick={select(item)},role=Role.Tab).testTag("tab-${item.name}")) {
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(if(selected)4.dp else 1.dp).background(if(selected) Majung.Yellow else Majung.Border))
                    Column(Modifier.align(Alignment.Center).padding(top=5.dp,bottom=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        DesignAsset(if(selected)item.selected else item.basic,null,Modifier.size(24.dp))
                        Text(item.label,style=MaterialTheme.typography.labelMedium,color=if(selected)Majung.Text else Majung.Secondary)
                    }
                }
            }
        }
    }
}

@Composable internal fun EmptyNote(title:String,body:String) {
    Surface(color=Majung.Card,shape=RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            WaitingArt();Text(title,fontFamily=Majung.Hand,fontSize=24.sp,fontWeight=FontWeight.Bold)
            Text(body,style=MaterialTheme.typography.bodySmall,color=Majung.Secondary,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
