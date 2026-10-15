package com.minyook.sllm2.ui

import java.net.URI
import java.util.Locale

/** Only a server origin is accepted; credentials and arbitrary local schemes are rejected. */
fun canonicalWorkServerUrl(value: String, allowLoopbackHttp: Boolean): String {
    val uri = try { URI(value.trim()) } catch (_: Exception) { throw IllegalArgumentException("올바른 서버 주소를 입력해 주세요.") }
    val host = uri.host?.lowercase(Locale.ROOT)?.removeSurrounding("[", "]")
        ?: throw IllegalArgumentException("서버 주소에 도메인 또는 localhost를 입력해 주세요.")
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    val localHttp = allowLoopbackHttp && scheme == "http" && host in setOf("localhost", "127.0.0.1", "::1")
    require(scheme == "https" || localHttp) { "HTTPS 주소를 입력해 주세요. 개발 빌드에서는 localhost도 사용할 수 있어요." }
    require(uri.userInfo == null && uri.port in -1..65535 && (uri.path.isNullOrEmpty() || uri.path == "/") && uri.query == null) { "로그인 정보나 경로가 없는 서버 기본 주소를 입력해 주세요." }
    return URI(scheme, null, host, uri.port, null, null, null).toASCIIString()
}

fun sameWorkServerOrigin(server: String, target: String): Boolean = runCatching {
    val base = URI(server); val url = URI(target)
    fun port(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme == "https") 443 else 80
    base.scheme == url.scheme && base.host.equals(url.host, ignoreCase = true) && port(base) == port(url) && url.userInfo == null
}.getOrDefault(false)
