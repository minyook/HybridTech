package com.minyook.sllm2.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class WorkScenarioState(
    private val store: WorkScenarioStore,
    private val role: WorkView,
    private val scope: CoroutineScope,
) {
    var orders by mutableStateOf(emptyList<ScenarioOrder>())
        private set
    var actionError by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var selectedId by mutableStateOf<String?>(null)
    var requestOpen by mutableStateOf(false)
    var startRequestMic by mutableStateOf(false)
    var voiceFromWork by mutableStateOf(false)

    val currentOrder get() = orders.firstOrNull { it.id == selectedId }
        ?: orders.firstOrNull { it.status != WorkStatus.CLOSED }
        ?: orders.firstOrNull()

    fun clearError() {
        actionError = null
    }

    fun refresh() = change { withContext(Dispatchers.IO) { store.read() }.also { orders = it } }

    fun openRequest(voice: Boolean) {
        clearError()
        requestOpen = true
        startRequestMic = voice
    }

    fun create(name: String, site: String, location: String, task: String, onCreated: () -> Unit) = change {
        val local = requireNotNull(store as? LocalWorkScenario)
        val order = withContext(Dispatchers.IO) { local.create(name, site, location, task) }
        selectedId = order.id
        orders = listOf(order) + orders
        requestOpen = false
        onCreated()
    }

    fun advance(
        view: WorkView,
        order: ScenarioOrder,
        type: String,
        payload: JSONObject = JSONObject(),
        onSaved: () -> Unit = {},
    ) {
        if (view != role) {
            actionError = "이 앱에서는 해당 역할의 기능을 사용할 수 없어요."
            return
        }
        change {
            val updated = withContext(Dispatchers.IO) { store.advance(order, type, payload) }
            selectedId = updated.id
            orders = listOf(updated) + orders.filterNot { it.id == updated.id }
            onSaved()
        }
    }

    private fun change(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        actionError = null
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: SecurityException) {
                actionError = "두 앱이 같은 키로 서명되었는지 확인해 주세요."
            } catch (error: IllegalArgumentException) {
                actionError = error.message
                runCatching { withContext(Dispatchers.IO) { store.read() } }.getOrNull()?.let { orders = it }
            } catch (_: Exception) {
                actionError = if (role == WorkView.SUPERVISOR) "같은 기기에 설치된 작업자 앱의 기록을 열 수 없어요."
                else "기기에서 작업 기록을 처리하지 못했어요."
            } finally {
                busy = false
            }
        }
    }
}

@Composable
internal fun RefreshWorkOnResume(work: WorkScenarioState) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, work) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) work.refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
