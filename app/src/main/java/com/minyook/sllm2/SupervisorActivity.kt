package com.minyook.sllm2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.minyook.sllm2.ui.FieldGuardTossTheme
import com.minyook.sllm2.ui.RefreshWorkOnResume
import com.minyook.sllm2.ui.SupervisorWorkScenario
import com.minyook.sllm2.ui.SupervisorWorkspace
import com.minyook.sllm2.ui.WorkScenarioState
import com.minyook.sllm2.ui.WorkView
import com.minyook.sllm2.ui.WorkspaceDesign

class SupervisorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.SUPERVISOR_APP) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            FieldGuardTossTheme {
                val scope = rememberCoroutineScope()
                val work = remember(scope) {
                    WorkScenarioState(SupervisorWorkScenario(contentResolver), WorkView.SUPERVISOR, scope)
                }
                RefreshWorkOnResume(work)
                Box(Modifier.fillMaxSize().background(WorkspaceDesign.Canvas).safeDrawingPadding()) {
                    SupervisorWorkspace(work)
                }
            }
        }
    }
}
