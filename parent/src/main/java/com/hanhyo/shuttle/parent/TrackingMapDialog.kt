package com.hanhyo.shuttle.parent

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.json.JSONObject

/** No auth token, JS bridge, file access or phone geolocation enters this map. */
@SuppressLint("SetJavaScriptEnabled")
@Composable internal fun TrackingMapSurface(data: JSONObject, modifier: Modifier = Modifier, timeline: Boolean = false) {
    val context = LocalContext.current
    val renderer = remember(timeline) { context.assets.open(if(timeline) "stop-timeline.js" else "route-map.js").bufferedReader().use { it.readText() } }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val view = JSONObject(data.toString())
    val age = (now - data.optLong("clientReceivedAt", now)).coerceAtLeast(0)
    view.put("clientElapsedSeconds", age / 1000.0)
    val location = data.optJSONObject("location")
    if (age > 30_000 || location == null || data.optDouble("serverTime") * 1000 + age - location.optDouble("receivedAt") > 30_000) view.put("stale", true)
    fun scriptJson(value: String) = value.replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
    val json = scriptJson(view.toString())
    // onPageFinished reads latest state, so a reload cannot show the first stale location.
    val latestJson = rememberUpdatedState(json)
    val html = remember(renderer) {
        val rendererName = if(timeline) "ShuttleStopTimeline" else "ShuttleRouteMap"
        """<!doctype html><html lang="ko"><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="no-referrer"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; img-src https://tile.openstreetmap.org; style-src 'unsafe-inline'; connect-src 'none'; base-uri 'none'"><style>html,body{height:100%;margin:0}#map{height:100%;min-height:240px}</style></head><body><div id="map"></div><script>$renderer</script><script>window.updateTracking=v=>$rendererName.draw(document.getElementById('map'),v);</script></body></html>"""
    }
    AndroidView(modifier = modifier, factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setGeolocationEnabled(false)
            settings.domStorageEnabled = false
            settings.userAgentString = settings.userAgentString + " TonghakMate/0.3 (Android; com.ssafy.tonghak.parent)"
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript("if(window.updateTracking)window.updateTracking(${latestJson.value});", null)
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    // Only the visible map attribution link can open a browser.
                    if (request?.url.toString() == "https://www.openstreetmap.org/copyright" && request?.hasGesture() == true) {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    }
                    return true
                }
            }
            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }, update = { it.evaluateJavascript("if(window.updateTracking)window.updateTracking($json);", null) },
        onRelease = { it.stopLoading(); it.destroy() })
}

@Composable internal fun TrackingMapDialog(data: JSONObject, dismiss: () -> Unit) {
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp)) {
                Text("내 픽업 지도", style = MaterialTheme.typography.titleLarge)
                if (data.optString("source") == "SIMULATED") Text("모의 위치 시험 · 실제 운행 아님")
                TrackingMapSurface(data, Modifier.weight(1f).fillMaxWidth().testTag("tracking-web-map"))
                Text("배경지도: OpenStreetMap · 차량 위치: Pleos", style = MaterialTheme.typography.bodySmall)
                Text("Pleos 실제 경로선은 아직 제공되지 않습니다.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
            }
        }
    }
}
