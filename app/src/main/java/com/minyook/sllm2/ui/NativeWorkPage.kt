package com.minyook.sllm2.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.minyook.sllm2.BuildConfig
import com.minyook.sllm2.model.ModelPhase

@Composable
internal fun WorkServerSettings(state: FieldGuardUiState, actions: FieldGuardActions) {
    var address by remember(state.workServerUrl) { mutableStateOf(state.workServerUrl) }
    var error by remember { mutableStateOf("") }
    TossScrollPage {
        WorkspaceHeading("YOUR WORKSPACE", "현장 작업 서버 연결", "앱과 웹에서 같은 작업 요청·점검·승인 화면을 사용해요.")
        TossPanel {
            Text("작업 서버 주소", style = MaterialTheme.typography.titleMedium)
            WorkspaceField("서버 기본 주소",address,{ address=it;error="" },"https://현장서버주소",Modifier.testTag("workServerUrl"))
            Text("작업 기록과 감독관 인증은 입력한 서버에서 관리해요. 문서 질문과 가스 측정은 기존 앱 기능을 사용해요.", style = MaterialTheme.typography.bodySmall)
            if (BuildConfig.DEBUG) Text("PC USB 테스트: http://localhost:4173 · USB 포트 연결을 유지해 주세요.", style = MaterialTheme.typography.bodySmall)
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            TossPrimaryButton("서버 저장하고 점검 열기", {
                runCatching { canonicalWorkServerUrl(address, BuildConfig.DEBUG) }
                    .onSuccess(actions.onSaveWorkServer).onFailure { error = it.message ?: "서버 주소를 확인해 주세요." }
            })
        }
        TossSecondaryButton("이전 기기 점검 기록 보기", { actions.onNavigate(AppDestination.LOCAL_CHECKLIST) })
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun NativeWorkPage(state: FieldGuardUiState, actions: FieldGuardActions) {
    val origin = remember(state.workServerUrl) { runCatching { canonicalWorkServerUrl(state.workServerUrl, BuildConfig.DEBUG) }.getOrNull() }
    if (origin == null) { WorkServerSettings(state, actions); return }
    val start = if (state.destination == AppDestination.SUPERVISOR) "/supervisor-access" else "/checklist"
    val nativeReady = state.modelStatus.phase == ModelPhase.READY
    val url = "$origin/?embedded=android&nativeAiReady=$nativeReady#$start"
    var loading by remember(url) { mutableStateOf(true) }
    var error by remember(url) { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) { onDispose { webView?.stopLoading(); webView?.destroy(); webView = null } }
    Column(Modifier.fillMaxSize()) {
        if (loading) CircularProgressIndicator(Modifier.padding(20.dp))
        if (error.isNotEmpty()) Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("작업 서버에 연결하지 못했어요", style = MaterialTheme.typography.titleLarge)
            Text(error, style = MaterialTheme.typography.bodyMedium)
            TossPrimaryButton("다시 연결", { error = ""; loading = true; webView?.reload() })
            TossSecondaryButton("서버 주소 확인", { actions.onNavigate(AppDestination.WORK_SERVER) })
        }
        AndroidView(modifier = Modifier.fillMaxWidth().weight(1f).testTag("sharedWorkPage"), factory = { context ->
            WebView(context).apply {
                webView = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.safeBrowsingEnabled = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val destination = request.url.toString()
                        if (request.isForMainFrame && request.url.scheme == "fieldguard") {
                            if (view.url?.let { sameWorkServerOrigin(origin, it) } != true) return true
                            when (request.url.host) {
                                "ask" -> actions.onNavigate(AppDestination.CHAT)
                                "settings" -> actions.onNavigate(AppDestination.MODEL)
                            }
                            return true
                        }
                        return !sameWorkServerOrigin(origin, destination)
                    }
                    override fun onPageStarted(view: WebView, address: String, favicon: Bitmap?) { loading = true }
                    override fun onPageFinished(view: WebView, address: String) { loading = false; CookieManager.getInstance().flush() }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                        if (request.isForMainFrame) { loading = false; error = "서버 주소와 인터넷 연결을 확인해 주세요. PC 테스트 중이면 USB 포트 연결도 확인해 주세요." }
                    }
                }
                loadUrl(url)
            }
        }, update = { view ->
            if (view.url?.substringBefore('#') != url.substringBefore('#')) view.loadUrl(url)
        })
        TextButton(onClick = { actions.onNavigate(AppDestination.WORK_SERVER) }) { Text("작업 서버 설정") }
    }
}
