package no.nav.su.se.bakover.common.infrastructure.correlation

import io.opentelemetry.context.Context
import org.slf4j.MDC

class DiagnosticContext private constructor(
    private val traceContext: Context,
    private val mdcContext: Map<String, String>?,
) {
    fun <T> use(block: () -> T): T {
        val previousMdcContext = MDC.getCopyOfContextMap()
        return traceContext.makeCurrent().use {
            setMdcContext(mdcContext)
            try {
                block()
            } finally {
                setMdcContext(previousMdcContext)
            }
        }
    }

    companion object {
        fun capture(): DiagnosticContext = DiagnosticContext(
            traceContext = Context.current(),
            mdcContext = MDC.getCopyOfContextMap()?.toMap(),
        )

        private fun setMdcContext(context: Map<String, String>?) {
            if (context == null) MDC.clear() else MDC.setContextMap(context)
        }
    }
}
