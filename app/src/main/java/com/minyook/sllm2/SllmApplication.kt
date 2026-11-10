/* 소스 안내
 * 기본 프로세스의 ObjectBox와 다운로드 알림 채널을 초기화한다. :npu 프로세스의 불필요한 DB 초기화는 건너뛴다.
 * 연결 모듈: com.minyook.sllm2.data.ObjectBoxStore, com.minyook.sllm2.model.ModelNotifications.
 */
package com.minyook.sllm2

import android.app.Application
import com.minyook.sllm2.data.ObjectBoxStore
import com.minyook.sllm2.model.ModelNotifications

/**
 * 프로세스 시작 시 ObjectBox 지식 저장소와 모델 다운로드 알림 채널을 준비한다.
 * MainActivity가 ViewModel을 만들기 전에 실행된다. 별도 :npu 프로세스는 추론 서비스만 사용하므로
 * 같은 DB를 중복으로 열지 않도록 초기화를 건너뛴다. 문서 JSON 적재는 ViewModel → KnowledgeSeeder가 맡는다.
 */
class SllmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (android.os.Build.VERSION.SDK_INT >= 28 && getProcessName().endsWith(":npu")) return
        ObjectBoxStore.init(this)
        ModelNotifications.createChannel(this)
    }
}
