/* 소스 안내
 * Compose 계측 화면을 호스팅하는 debug 전용 Activity. 제품 ViewModel·모델·AP를 자동 시작하지 않는다.
 */
package com.minyook.sllm2

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

/** Empty fixture host. It never creates a model, AP service or production ViewModel. */
class WorkspacePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
}
