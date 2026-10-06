package com.minyook.sllm2.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Updates only the visible pending message; the inference state is not changed each second. */
@Composable
internal fun answerElapsedTime(startedAtElapsedRealtime: Long): String {
    var now by remember(startedAtElapsedRealtime) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAtElapsedRealtime) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }
    val seconds = if (startedAtElapsedRealtime > 0L) {
        ((now - startedAtElapsedRealtime).coerceAtLeast(0L)) / 1_000L
    } else 0L
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}
