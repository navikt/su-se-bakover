package no.nav.su.se.bakover

class CallContext(
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
        fun correlationId() = mdcContext.get()?.mdc?.get("X-Correlation-ID") ?: throw RuntimeException("Could not find correlationId")
    }

    class SecurityContext(
            val token: String
    )

    data class MdcContext(
            val mdc: Map<String, String> = emptyMap()
    )

    fun securityContextElement() = Pair(securityContext, security)
    fun mdcContextElement() = Pair(mdcContext, mdc)
}