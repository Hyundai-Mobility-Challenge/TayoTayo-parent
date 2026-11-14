package com.hanhyo.shuttle.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable internal fun NotificationScreen(childName: String, data: JSONObject?, error: String,
    busy: Boolean, close: () -> Unit, retry: () -> Unit) {
    BackHandler(onBack = close)
    Surface(color = Majung.App) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(24.dp).testTag("notification-detail"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = close) { Text("홈으로", color = Majung.Text) }
            Text(if (childName.isBlank()) "알림의 운행 확인" else "$childName · 운행 소식",
                style = MaterialTheme.typography.titleLarge)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                error.isNotBlank() -> Text(error, Modifier.testTag("notification-error"))
                data != null -> {
                    Text(data.optString("serviceDate"), style = MaterialTheme.typography.bodySmall)
                    JourneyCard(data, if (data.optString("direction") == "PICK_UP") SchoolDirection.Morning else SchoolDirection.Afternoon)
                    Text("지난 알림도 해당 운행의 최신 기록을 조회해 표시해요.", style = MaterialTheme.typography.bodySmall)
                }
                else -> Text("접근 권한과 최신 운행 기록을 확인하고 있어요.")
            }
            NoteButton("다시 확인", !busy, secondary = true, action = retry)
        }
    }
}
