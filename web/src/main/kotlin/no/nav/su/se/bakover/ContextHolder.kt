package no.nav.su.se.bakover

import io.ktor.http.HttpHeaders
import kotlinx.coroutines.asContextElement

class ContextHolder(
        private val security: SecurityContext,
        private val mdc: MdcContext
) {
    init {
        securityContext.set(security)
        mdcContext.set(mdc)
    }

    companion object {
        private val securityContext: ThreadLocal<SecurityContext> = ThreadLocal()
        private val mdcContext: ThreadLocal<MdcContext> = ThreadLocal()

        fun authentication() = securityContext.get().token
        fun correlationId() = mdcContext.get()?.mdc?.get(HttpHeaders.XCorrelationId) ?: throw RuntimeException("Could not find correlationId")
    }

    fun asContextElement() = securityContext.asContextElement(security) + mdcContext.asContextElement(mdc)

    data class SecurityContext(
            val token: String
    )

    data class MdcContext(
            val mdc: Map<String, String> = emptyMap()
    )
}