/* 소스 안내
 * 대상 앱 패키지와 Android 계측 실행을 검증하는 Android 계측 검사다.
 * 검사 예: useAppContext
 * 실행: 연결된 Android 기기/에뮬레이터의 AndroidJUnitRunner. opt-in 인자는 본문 assume/arguments 조건을 확인한다.
 */
package com.minyook.sllm2

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.minyook.sllm2", appContext.packageName)
    }
}