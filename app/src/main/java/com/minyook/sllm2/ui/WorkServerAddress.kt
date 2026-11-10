/* 소스 안내
 * 서버 origin과 같은 출처 탐색 조건을 검증한다. 저장/진입 주소 검사이며 감독관 권한 검사는 아니다.
 */
package com.minyook.sllm2.ui

import java.net.URI
import java.util.Locale

/**
 * ViewModel 저장과 NativeWorkPage 진입에서 공유하는 서버 주소 검증.
 * 경로/로그인 정보 없는 HTTPS origin만 허용하며 디버그에 한해 loopback HTTP를 허용한다.
 * 반환 주소를 같은 origin 탐색 검사에 사용한다. 이 검사는 서버 감독관 인증을 대신하지 않는다.
 */
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
