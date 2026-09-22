package com.minyook.sllm2

import android.app.Application
import com.minyook.sllm2.data.ObjectBoxStore
import com.minyook.sllm2.model.ModelNotifications

class SllmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ObjectBoxStore.init(this)
        ModelNotifications.createChannel(this)
    }
}
