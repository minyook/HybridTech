package com.minyook.sllm2

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

/** Empty fixture host. It never creates a model, BLE service or production ViewModel. */
class WorkspacePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
}
