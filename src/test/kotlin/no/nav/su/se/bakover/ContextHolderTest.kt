package no.nav.su.se.bakover

import io.ktor.http.HttpHeaders.XCorrelationId
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.ContextHolder.SecurityContext
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class ContextHolderTest {

    @Test
    fun `should set and get context`() {
        ContextHolder.setSecurityContext(SecurityContext("token"))
        assertEquals("token", ContextHolder.getSecurityContext().token)
    }

    @Test
    fun `should preserve different contexts for different scopes`() {
        ContextHolder.setMdcContext(ContextHolder.MdcContext(mapOf(XCorrelationId to DEFAULT_CALL_ID)))
        runBlocking {
            ContextHolder.setSecurityContext(SecurityContext("outer"))
            val outer = Thread.currentThread()
            launchWithContext(SecurityContext("inner")) {
                val inner = Thread.currentThread()
                assertEquals("inner", ContextHolder.getSecurityContext().token)
                assertEquals(DEFAULT_CALL_ID, ContextHolder.getMdc(XCorrelationId))
                assertNotEquals(outer, inner)
                launchWithContext(SecurityContext("furtherin")) {
                    assertEquals("furtherin", ContextHolder.getSecurityContext().token)
                    assertEquals(DEFAULT_CALL_ID, ContextHolder.getMdc(XCorrelationId))
                }
            }
            assertEquals("outer", ContextHolder.getSecurityContext().token)
            assertEquals(DEFAULT_CALL_ID, ContextHolder.getMdc(XCorrelationId))
        }
    }
}
