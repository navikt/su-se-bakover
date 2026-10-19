package no.nav.su.se.bakover.common.infrastructure.correlation

import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import org.slf4j.MDC
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

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

    suspend fun <T> useSuspending(block: suspend () -> T): T =
        withContext(DiagnosticContextElement(traceContext, mdcContext)) {
            block()
        }

    private class DiagnosticContextElement(
        private val traceContext: Context,
        private val mdcContext: Map<String, String>?,
    ) : AbstractCoroutineContextElement(Key),
        ThreadContextElement<PreviousContext> {

        override fun updateThreadContext(context: CoroutineContext): PreviousContext {
            val previousContext = PreviousContext(
                traceScope = traceContext.makeCurrent(),
                mdcContext = MDC.getCopyOfContextMap(),
            )
            setMdcContext(mdcContext)
            return previousContext
        }

        override fun restoreThreadContext(context: CoroutineContext, oldState: PreviousContext) {
            oldState.traceScope.close()
            setMdcContext(oldState.mdcContext)
        }

        private companion object Key : CoroutineContext.Key<DiagnosticContextElement>
    }

    private data class PreviousContext(
        val traceScope: Scope,
        val mdcContext: Map<String, String>?,
    )

    companion object {
        fun capture(): DiagnosticContext = DiagnosticContext(
            traceContext = Context.current(),
            mdcContext = MDC.getCopyOfContextMap()?.toMap(),
        )

        internal fun setMdcContext(context: Map<String, String>?) {
            if (context == null) MDC.clear() else MDC.setContextMap(context)
        }
    }
}
