package com.minyook.sllm2.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkServerAddressTest {
    @Test fun serverAddressKeepsOnlyTheTrustedOrigin() {
        assertEquals("https://field.example", canonicalWorkServerUrl(" https://field.example/#/checklist ", false))
        assertEquals("http://localhost:4173", canonicalWorkServerUrl("http://localhost:4173/", true))
    }
    @Test fun cleartextRemoteServersCredentialsAndFileSchemesAreRejected() {
        listOf("http://field.example", "https://user:pass@field.example", "file:///etc/passwd", "javascript:alert(1)", "https://field.example/arbitrary", "https://field.example?secret=code").forEach {
            assertTrue(it, runCatching { canonicalWorkServerUrl(it, true) }.isFailure)
        }
        assertTrue(runCatching { canonicalWorkServerUrl("http://localhost:4173", false) }.isFailure)
    }
    @Test fun webNavigationMustMatchSchemeHostAndPort() {
        assertTrue(sameWorkServerOrigin("https://field.example", "https://field.example/#/supervisor"))
        assertFalse(sameWorkServerOrigin("https://field.example", "https://field.example.evil.test"))
        assertFalse(sameWorkServerOrigin("https://field.example", "http://field.example"))
        assertFalse(sameWorkServerOrigin("https://field.example", "https://field.example:444"))
        assertFalse(sameWorkServerOrigin("https://field.example", "fieldguard://ask"))
    }
}
