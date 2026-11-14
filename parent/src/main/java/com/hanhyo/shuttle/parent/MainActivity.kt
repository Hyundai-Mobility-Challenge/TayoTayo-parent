package com.hanhyo.shuttle.parent

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var model: ParentModel
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Push.register(this); model.readPushStatus()
    }
    private var openInbox by mutableStateOf(false)
    private var notificationTarget by mutableStateOf<NotificationTarget?>(null)
    private fun readNotification(intent: Intent) {
        notificationTarget = NotificationTarget.from(intent.getStringExtra("childId"), intent.getStringExtra("tripId"))
        openInbox = notificationTarget == null && (intent.getBooleanExtra("openInbox",false) || intent.hasExtra("notificationId"))
    }
    private fun consumeNotification() {
        notificationTarget = null
        // Rotation must not reopen an already consumed notification.
        intent.removeExtra("childId"); intent.removeExtra("tripId"); intent.removeExtra("notificationId"); intent.removeExtra("openInbox")
    }
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT>=33 && !Push.permissionGranted(this) &&
            (!Push.prefs(this).getBoolean("notificationPermissionRequested",false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            Push.prefs(this).edit().putBoolean("notificationPermissionRequested",true).apply()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!Push.permissionGranted(this) || !Push.appNotificationsEnabled(this) || !Push.channelEnabled(this)) {
            Push.openSettings(this)
        } else { Push.register(this); model.readPushStatus() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))
        Push.channel(this)
        model=ViewModelProvider(this)[ParentModel::class.java]
        readNotification(intent)
        setContent { MajungTheme { ParentScreen(model,openInbox,{openInbox=false;consumeNotification()},
            notificationTarget=notificationTarget,onTargetConsumed=::consumeNotification,notifications=::requestNotificationPermission) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);readNotification(intent) }
    override fun onStart() { super.onStart();model.foreground=true }
    override fun onStop() { model.foreground=false;super.onStop() }
    override fun onResume() { super.onResume();model.readPushStatus();Push.register(this);model.refresh() }
}

@Composable fun ParentScreen(vm: ParentModel,openInbox: Boolean=false,onInboxConsumed: ()->Unit={},
    notificationTarget: NotificationTarget?=null,onTargetConsumed: ()->Unit={},notifications: ()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    LaunchedEffect(vm.loggedIn,vm.foreground) {
        while(vm.loggedIn&&vm.foreground) { vm.refreshTracking();vm.readPushStatus();delay(5000) }
    }
    LaunchedEffect(vm.loggedIn) { if(vm.loggedIn) vm.requestInitialNotification(notifications) }
    LaunchedEffect(notificationTarget,vm.loggedIn) {
        if (vm.loggedIn && notificationTarget != null) {
            snapshotFlow { vm.busy }.first { !it }
            vm.openNotification(notificationTarget)
            snapshotFlow { vm.busy }.first { !it }
            if (vm.loggedIn) onTargetConsumed()
        }
    }
    if(!vm.loggedIn) AuthScreen(vm)
    else if(vm.notificationTarget!=null) NotificationScreen(vm.notificationChildName,vm.notificationData,
        vm.notificationError,vm.busy,close=vm::closeNotification,retry={vm.notificationTarget?.let { vm.openNotification(it) }})
    else GuardianApp(children=vm.state.optJSONArray("children")?.objects().orEmpty(),tracking=vm.tracking,
        directions=vm.directions,tools=vm.childTools,events=vm.state.optJSONArray("events")?.objects().orEmpty(),
        busy=vm.busy,message=vm.message,pushStatus=vm.pushStatus,openInbox=openInbox,onInboxConsumed=onInboxConsumed,
        actions=GuardianActions(
            loadTools={id,date->scope.launch { snapshotFlow { vm.busy }.first { !it };vm.loadChildTools(id,date) }},
            attendance={id,date,dir,type,note,done->vm.createAttendanceRequest(id,date,dir,type,note,done)},
            pickup={id,date,dir,person,done->vm.setDailyPickup(id,date,dir,if(person==null) "GUARDIAN" else "AUTHORIZED_PERSON",person,done)},
            registerPerson={id,name,relationship->vm.addAuthorizedPickupPerson(id,name,relationship,"","")},
            invite=vm::accept,refresh=vm::refresh,logout=vm::logout,notificationPermission=notifications,
            notificationSettings={Push.openSettings(context)},
            share={id,trip->vm.shareTracking(id,trip) { url->context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain";putExtra(Intent.EXTRA_TEXT,"오늘의 통학 상황을 확인해 주세요.\n$url")
            },"통학 상황 공유")) }},revoke=vm::revokeTracking))
}
