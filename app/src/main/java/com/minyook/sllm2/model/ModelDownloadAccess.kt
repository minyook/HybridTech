/* 소스 안내
 * Hugging Face 읽기 토큰을 Keystore AES-GCM 키로 암호화해 Worker에 제공한다. 다운로드 인자/보고서에 원문 토큰을 넣지 않는다.
 */
package com.minyook.sllm2.model

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * ViewModel이 받은 Hugging Face 읽기 토큰을 Worker에 전달하는 로컬 저장 경계.
 * Keystore의 내보낼 수 없는 AES-GCM 키로 암호화하고 preferences에는 IV/암호문만 기록한다.
 * Worker가 필요할 때 token으로 복호화하며 WorkManager 인자·로그·비교 보고서에 원문 토큰을 넣지 않는다.
 */
class ModelDownloadAccess(context: Context) {
    private val preferences = context.getSharedPreferences("model_download_access", Context.MODE_PRIVATE)

    fun save(token: String) {
        if (token.isBlank()) return
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.trim().toByteArray(Charsets.UTF_8))
        check(preferences.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }

    fun token(): String = runCatching {
        val value = preferences.getString("token", null) ?: return ""
        val iv = Base64.decode(preferences.getString("iv", null) ?: return "", Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrDefault("")

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private companion object { const val ALIAS = "fieldguard_model_download" }
}
