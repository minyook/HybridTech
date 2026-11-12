package com.minyook.sllm2.ui

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun InternetStatusIndicator() {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) { context.getSystemService(ConnectivityManager::class.java) }
    var connected by remember(manager) { mutableStateOf<Boolean?>(null) }

    DisposableEffect(manager) {
        if (manager == null) return@DisposableEffect onDispose {}

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                connected = capabilities.hasValidatedInternet()
            }

            override fun onLost(network: Network) {
                connected = false
            }
        }
        manager.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        connected = manager.activeNetwork?.let(manager::getNetworkCapabilities)?.hasValidatedInternet() ?: false
        onDispose { manager.unregisterNetworkCallback(callback) }
    }

    val label = when (connected) {
        true -> "인터넷 연결"
        false -> "인터넷 없음"
        null -> "인터넷 확인 중"
    }
    val color = if (connected == true) WorkspaceDesign.Green else WorkspaceDesign.Muted
    Row(
        Modifier.testTag("internetStatus"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

private fun NetworkCapabilities.hasValidatedInternet() =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
