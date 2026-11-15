package com.hanhyo.shuttle.parent

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun BrandWordmark() {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Text("타요타요",fontFamily=Majung.Hand,fontSize=42.sp,lineHeight=44.sp,fontWeight=FontWeight.Bold)
        DesignAsset("c6fa3.svg",null,Modifier.width(158.dp).height(9.dp))
    }
}

@Composable internal fun AuthScreen(vm: ParentModel) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val ui by vm.uiState.collectAsStateWithLifecycle()
    var join by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var verification by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf(vm.base) }
    var settings by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(),color=Majung.App) {
        Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth().padding(vertical=12.dp),contentAlignment=Alignment.Center) { BrandWordmark() }
            if(!join) Column(Modifier.fillMaxWidth().background(Majung.Yellow,RoundedCornerShape(24.dp)).padding(20.dp)) {
                Text("우리 아이 하원길",fontFamily=Majung.Hand,fontSize=32.sp,fontWeight=FontWeight.Bold)
                Text("오늘의 작은 소식을 함께 확인해요.",style=MaterialTheme.typography.bodySmall)
                DesignAsset("2134b.png",null,Modifier.fillMaxWidth().aspectRatio(3f).padding(top=8.dp))
            } else Text("보호자 가입",style=MaterialTheme.typography.titleLarge)
            if(join) NoteField(name,{name=it},"보호자 이름","이름을 입력해 주세요",Modifier.testTag("parent-name"))
            NoteField(email,{email=it},"이메일","초대받은 이메일",Modifier.testTag("parent-email"),keyboardType=KeyboardType.Email)
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("비밀번호",style=MaterialTheme.typography.titleSmall)
                OutlinedTextField(password,{password=it},modifier=Modifier.fillMaxWidth().testTag("parent-password"),
                    singleLine=true,placeholder={Text("비밀번호 입력")},shape=RoundedCornerShape(12.dp),
                    visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
            }
            if(join) NoteField(verification,{verification=it},"이메일 인증번호","받은 번호를 입력해 주세요")
            if(ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(ui.message.isNotBlank()) Text(ui.message,Modifier.testTag("parent-message"),style=MaterialTheme.typography.bodySmall,color=Majung.Secondary)
            NoteButton(if(join) if(verification.isBlank()) "인증번호 받기" else "인증하고 가입" else "로그인",
                enabled=!ui.busy&&email.isNotBlank()&&password.length>=8&&(!join||name.isNotBlank()),modifier=Modifier.testTag("parent-auth")) {
                vm.dispatch(ParentIntent.Authenticate(join,server,name,email.trim(),password,verification))
            }
            TextButton(onClick={join=!join},modifier=Modifier.align(Alignment.CenterHorizontally),enabled=!ui.busy) {
                Text(if(join) "이미 계정이 있어요 · 로그인" else "처음이라면 보호자 가입  ›",color=Majung.Text)
            }
            TextButton(onClick={settings=true},modifier=Modifier.align(Alignment.CenterHorizontally)) { Text("서버 주소 설정",style=MaterialTheme.typography.bodySmall,color=Majung.Secondary) }
            if(BuildConfig.DEBUG) TextButton(onClick={context.startActivity(android.content.Intent().setClassName(context,"com.hanhyo.shuttle.parent.DesignPreviewActivity"))},modifier=Modifier.align(Alignment.CenterHorizontally)) { Text("디자인 미리보기 · 시연 데이터",style=MaterialTheme.typography.bodySmall,color=Majung.Secondary) }
        }
    }
    if(settings) AlertDialog(onDismissRequest={settings=false},title={Text("서버 주소")},text={
        NoteField(server,{server=it},"API 주소","http://10.0.2.2:8080",Modifier.testTag("parent-server"),keyboardType=KeyboardType.Uri)
    },confirmButton={TextButton(onClick={settings=false}) { Text("적용",color=Majung.Text) }})
}

@Composable internal fun NoteField(value: String,change:(String)->Unit,label:String,hint:String="",modifier:Modifier=Modifier,keyboardType:KeyboardType=KeyboardType.Text) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,style=MaterialTheme.typography.titleSmall)
        OutlinedTextField(value,change,modifier.fillMaxWidth(),singleLine=true,placeholder={Text(hint)},
            shape=RoundedCornerShape(12.dp),keyboardOptions=KeyboardOptions(keyboardType=keyboardType),
            colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Majung.Card,focusedContainerColor=Majung.Card,unfocusedBorderColor=Majung.Border))
    }
}
