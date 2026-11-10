/* 소스 안내
 * Application에서 초기화하는 기본 프로세스 DB 소유자. Seeder와 Repository가 같은 store를 사용한다.
 */
package com.minyook.sllm2.data

import android.content.Context
import io.objectbox.BoxStore

/** 기본 프로세스의 지식 DB 소유자. Application이 먼저 초기화하고 Seeder/Repository가 같은 store를 사용한다. */
object ObjectBoxStore {
    lateinit var store: BoxStore
        private set

    @Synchronized
    fun init(context: Context) {
        if (::store.isInitialized) return
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .name("confined-space-rag")
            .build()
    }
}
