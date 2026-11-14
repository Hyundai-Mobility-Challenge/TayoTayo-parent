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
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: ParentModel by viewModels()
    @Inject lateinit var settings: GuardianSettings
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
        lifecycleScope.launch {
            if (Build.VERSION.SDK_INT>=33 && !Push.permissionGranted(this@MainActivity) &&
                (!settings.runtimePermissionRequested.first() || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                settings.recordRuntimePermissionRequest()
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (!Push.permissionGranted(this@MainActivity) || !Push.appNotificationsEnabled(this@MainActivity) || !Push.channelEnabled(this@MainActivity)) {
                Push.openSettings(this@MainActivity)
            } else { Push.register(this@MainActivity); model.readPushStatus() }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))
        Push.channel(this)
        readNotification(intent)
        setContent { MajungTheme { ParentScreen(model,openInbox,{openInbox=false;consumeNotification()},
            notificationTarget=notificationTarget,onTargetConsumed=::consumeNotification,notifications=::requestNotificationPermission) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);readNotification(intent) }
    override fun onStart() { super.onStart();model.dispatch(ParentIntent.Foreground(true)) }
    override fun onStop() { model.dispatch(ParentIntent.Foreground(false));super.onStop() }
    override fun onResume() { super.onResume();model.readPushStatus();Push.register(this);model.refresh() }
}

@Composable fun ParentScreen(vm: ParentModel,openInbox: Boolean=false,onInboxConsumed: ()->Unit={},
    notificationTarget: NotificationTarget?=null,onTargetConsumed: ()->Unit={},notifications: ()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val collectedState = vm.uiState.collectAsStateWithLifecycle()
    val ui = collectedState.value
    val nav = rememberNavController()
    val destination = if (!ui.loggedIn) "auth" else if (ui.notificationTarget != null) "notification" else "home"
    LaunchedEffect(destination) {
        nav.navigate(destination) { popUpTo(nav.graph.id) { inclusive = true }; launchSingleTop = true }
    }
    LaunchedEffect(ui.loggedIn,ui.foreground) {
        while(ui.loggedIn&&ui.foreground) { vm.dispatch(ParentIntent.RefreshTracking);vm.readPushStatus();delay(5000) }
    }
    LaunchedEffect(ui.loggedIn) { if(ui.loggedIn) vm.requestInitialNotification(notifications) }
    LaunchedEffect(notificationTarget,ui.loggedIn) {
        if (ui.loggedIn && notificationTarget != null) {
            vm.dispatchAndAwait(ParentIntent.OpenNotification(notificationTarget))
            if (vm.loggedIn) onTargetConsumed()
        }
    }
    NavHost(navController=nav,startDestination="auth") {
        composable("auth") { AuthScreen(vm) }
        composable("notification") {
            val screen = collectedState.value
            if(screen.loggedIn) NotificationScreen(screen.notificationChildName,screen.notificationData,screen.notificationError,screen.busy,
                close={vm.dispatch(ParentIntent.CloseNotification)},
                retry={screen.notificationTarget?.let { vm.dispatch(ParentIntent.OpenNotification(it)) }})
        }
        composable("home") {
        val screen = collectedState.value
        if(screen.loggedIn) GuardianApp(children=screen.connections.optJSONArray("children")?.objects().orEmpty(),tracking=screen.tracking,
        directions=screen.directions,tools=screen.childTools,events=screen.connections.optJSONArray("events")?.objects().orEmpty(),
        busy=screen.busy,message=screen.message,pushStatus=screen.pushStatus,openInbox=openInbox,onInboxConsumed=onInboxConsumed,
        actions=GuardianActions(
            loadTools={id,date->scope.launch { vm.uiState.first { !it.busy };vm.dispatch(ParentIntent.LoadTools(id,date)) }},
            attendance={id,date,dir,type,note,done->vm.dispatch(ParentIntent.Attendance(id,date,dir,type,note,done))},
            pickup={id,date,dir,person,done->vm.dispatch(ParentIntent.Pickup(id,date,dir,person,done))},
            registerPerson={id,name,relationship->vm.dispatch(ParentIntent.RegisterPerson(id,name,relationship))},
            invite={vm.dispatch(ParentIntent.AcceptInvitation(it))},refresh={vm.dispatch(ParentIntent.Refresh)},logout={vm.dispatch(ParentIntent.Logout)},notificationPermission=notifications,
            notificationSettings={Push.openSettings(context)},
            share={id,trip->vm.dispatch(ParentIntent.Share(id,trip) { url->context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain";putExtra(Intent.EXTRA_TEXT,"오늘의 통학 상황을 확인해 주세요.\n$url")
            },"통학 상황 공유")) })},revoke={id,trip->vm.dispatch(ParentIntent.RevokeShare(id,trip))}))
        }
    }
}
