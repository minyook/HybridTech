/* 소스 안내
 * AP 세션과 장비 목록을 서비스에서 ViewModel로 전달하는 StateFlow. 연결 비밀은 현재 세션 메모리에만 둔다.
 */
package com.minyook.sllm2.gas

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ApPhase { OFF, STARTING, RUNNING, ERROR }

data class WifiApUiState(
    val phase: ApPhase = ApPhase.OFF,
    val status: String = "휴대폰 AP를 켜고 감지기를 연결해 주세요.",
    val ssid: String = "",
    val password: String = "",
    val host: String = "",
    val port: Int = WifiMeterProtocol.DEFAULT_PORT,
    val token: String = "",
    val band: String = "",
    val selectedDeviceId: String? = null,
    val meters: List<WifiMeterView> = emptyList(),
) {
    val selected get() = meters.firstOrNull { it.deviceId == selectedDeviceId }
}

/**
 * 서비스 → ViewModel 사이의 AP 세션 상태 통로. 서비스만 연결/측정 상태를 갱신하고 UI는 StateFlow를 읽는다.
 * SSID·암호·연결 코드는 메모리의 현재 세션에만 존재하며 preferences나 로그에 저장하지 않는다.
 * select는 표시할 장비만 바꾸고 감지기 연결이나 실제 측정값을 만들지 않는다.
 */
object WifiApHub {
    private val mutable = MutableStateFlow(WifiApUiState())
    val state = mutable.asStateFlow()
    internal fun update(transform: (WifiApUiState) -> WifiApUiState) = mutable.update(transform)
    fun select(id: String) = mutable.update { state ->
        if (state.meters.any { it.deviceId == id }) state.copy(selectedDeviceId = id) else state
    }
}
