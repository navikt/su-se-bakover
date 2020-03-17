package no.nav.su.se.bakover

import kotlinx.coroutines.asContextElement
import org.slf4j.MDC

class ContextHolder {
    companion object {
        private val securityContext: ThreadLocal<SecurityContext> = ThreadLocal()
        fun setSecurityContext(securityContext: SecurityContext) = this.securityContext.set(securityContext)
        fun getSecurityContext(): SecurityContext = securityContext.get()
        fun getSecurityContextElement(securityContext: SecurityContext) = this.securityContext.asContextElement(securityContext)

        private val mdcContext: ThreadLocal<MdcContext> = ThreadLocal()
        fun setMdcContext(mdcContext: MdcContext) = this.mdcContext.set(mdcContext).also { mdcContext.mdc.forEach { MDC.put(it.key, it.value) } }
        fun getMdcContext() = mdcContext.get()
        fun getMdcContextElement(mdcContext: MdcContext) = this.mdcContext.asContextElement(mdcContext)
        fun getMdc(key: String): String = mdcContext.get()?.mdc?.get(key) ?: throw RuntimeException("MDC.get($key) should not be null")
    }

    data class SecurityContext(
            val token: String
    )

    data class MdcContext(
            val mdc: Map<String, String> = emptyMap()
    )
}