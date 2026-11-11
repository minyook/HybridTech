/* 소스 안내
 * 기본 프로세스의 ObjectBox와 다운로드 알림 채널을 초기화한다.
 * 연결 모듈: com.minyook.sllm2.data.ObjectBoxStore, com.minyook.sllm2.model.ModelNotifications.
 */
package com.minyook.sllm2

import android.app.Application
import com.minyook.sllm2.data.ObjectBoxStore
import com.minyook.sllm2.model.ModelNotifications

/**
 * 프로세스 시작 시 ObjectBox 지식 저장소와 모델 다운로드 알림 채널을 준비한다.
 * 문서 JSON 적재는 ViewModel → KnowledgeSeeder가 맡는다.
 */
class SllmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ObjectBoxStore.init(this)
        ModelNotifications.createChannel(this)
    }
}
