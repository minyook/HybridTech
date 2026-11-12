package com.minyook.sllm2

import androidx.test.platform.app.InstrumentationRegistry
import com.minyook.sllm2.ui.LocalWorkScenario
import com.minyook.sllm2.ui.SupervisorWorkScenario
import com.minyook.sllm2.ui.WorkStatus
import com.minyook.sllm2.ui.scenarioInspectionItems
import com.minyook.sllm2.ui.supervisorWorkUri
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run only on a test device with both APKs installed and a unique testWorkTask argument. */
class SeparatedWorkAppsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun task(supervisor: Boolean): String {
        assumeTrue(BuildConfig.SUPERVISOR_APP == supervisor)
        val task = InstrumentationRegistry.getArguments().getString("testWorkTask")
        assumeTrue(!task.isNullOrBlank())
        return requireNotNull(task)
    }

    @Test fun workerCreatesRequestAndCannotInvokeSupervisorProvider() {
        val order = LocalWorkScenario(context).create("시험 작업자", "시험 현장", "시험 위치", task(false))
        assertEquals(WorkStatus.REQUESTED, order.status)
        val denied = runCatching { context.contentResolver.call(supervisorWorkUri, "advance", order.id, null) }
        assertTrue(denied.exceptionOrNull() is SecurityException)
        val store = LocalWorkScenario(context)
        val unauthorized = runCatching { store.advance(order, "approve", JSONObject()) }
        assertTrue(unauthorized.exceptionOrNull() is IllegalArgumentException)
        assertEquals(WorkStatus.REQUESTED, store.read().first { it.id == order.id }.status)
    }

    @Test fun supervisorReadsWorkerRequestAndApproves() {
        val task = task(true)
        val store = SupervisorWorkScenario(context.contentResolver)
        val order = store.read().first { it.task == task }
        val checked = JSONArray(scenarioInspectionItems.map { it.first })
        val review = store.advance(order, "save-inspection", JSONObject().put("inspection", JSONObject().put("checked", checked)))
        assertEquals(WorkStatus.REVIEWING, review.status)
        assertEquals(WorkStatus.APPROVED, store.advance(review, "approve", JSONObject()).status)
    }

    @Test fun workerReadsApprovalAndRecordsEvacuation() {
        val task = task(false)
        val store = LocalWorkScenario(context)
        val approved = store.read().first { it.task == task }
        assertEquals(WorkStatus.APPROVED, approved.status)
        val entered = store.advance(approved, "enter", JSONObject().put("acknowledged", true))
        val supporting = store.advance(entered, "start-ai", JSONObject())
        val alarm = store.advance(supporting, "report-danger", JSONObject().put("reason", "시연 경보"))
        val evacuated = store.advance(alarm, "evacuate", JSONObject().put("location", "시연 집결지"))
        assertEquals(WorkStatus.EVACUATED, evacuated.status)
    }

    @Test fun supervisorSeesEvacuationAndClosesWork() {
        val task = task(true)
        val store = SupervisorWorkScenario(context.contentResolver)
        val order = store.read().first { it.task == task }
        assertEquals("시연 집결지", order.exitLocation)
        assertEquals(WorkStatus.CLOSED, store.advance(order, "close", JSONObject()).status)
    }
}
