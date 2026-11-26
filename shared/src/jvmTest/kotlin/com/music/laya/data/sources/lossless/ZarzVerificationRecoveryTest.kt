package com.music.laya.data.sources.lossless

import com.music.laya.platform.HttpResponse
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZarzVerificationRecoveryTest {
    @Test
    fun aNewVerificationStartsAFreshChallenge() = runBlocking {
        var challenge = 0
        val session = ZarzSession("verification-restart-test@1", { "test" }) { request ->
            check(request.url.contains("/bootstrap?"))
            HttpResponse(200, emptyMap(), """{"challenge_id":"challenge-${++challenge}"}""".encodeToByteArray(), request.url)
        }

        assertEquals("challenge-1", Url(session.verificationUrl()).parameters["id"])
        assertEquals("challenge-2", Url(session.verificationUrl()).parameters["id"])
    }

    @Test
    fun providerFailureKeepsTheSessionButBrokerRejectionRequiresVerification() = runBlocking {
        var rejectSession = false
        val session = ZarzSession("verification-loss-test@1", { "test" }) { request ->
            val (status, payload) = when {
                request.url.contains("/bootstrap?") ->
                    200 to """{"session_id":"session","session_secret":"secret","expires_at":"2999-01-01T00:00:00Z"}"""
                rejectSession -> 428 to """{"origin":"gateway","code":"VERIFY_REQUIRED","action":"verify"}"""
                else -> 428 to """{"origin":"provider","code":"VERIFY_REQUIRED"}"""
            }
            HttpResponse(status, emptyMap(), payload.encodeToByteArray(), request.url)
        }
        assertEquals("", session.verificationUrl())

        assertFailsWith<ZarzException> { session.get("/qbz/track/search") }
        assertTrue(session.verified.value)
        assertNull(session.verificationLoss.value)

        rejectSession = true
        assertFailsWith<VerificationRequired> { session.get("/qbz/track/search") }
        assertFalse(session.verified.value)
        assertEquals(VerificationLoss.Rejected, session.verificationLoss.value)

        assertEquals("", session.verificationUrl())
        assertTrue(session.verified.value)
        assertNull(session.verificationLoss.value)
    }
}
