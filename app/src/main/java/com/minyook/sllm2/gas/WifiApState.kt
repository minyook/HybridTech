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

/** Credentials live only for the current AP session, never in preferences or logs. */
object WifiApHub {
    private val mutable = MutableStateFlow(WifiApUiState())
    val state = mutable.asStateFlow()
    internal fun update(transform: (WifiApUiState) -> WifiApUiState) = mutable.update(transform)
    fun select(id: String) = mutable.update { state ->
        if (state.meters.any { it.deviceId == id }) state.copy(selectedDeviceId = id) else state
    }
}
