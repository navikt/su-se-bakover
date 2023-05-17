package no.nav.su.se.bakover.common

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import org.slf4j.MDC
import java.util.UUID

const val CorrelationIdHeader = "X-Correlation-ID"

@JvmInline
value class CorrelationId(val value: String) {
    override fun toString() = value

    companion object {
        fun generate(): CorrelationId = CorrelationId(UUID.randomUUID().toString())

        /** Henter `X-Correlation-ID` fra MDC */
        private fun getCorrelationIdFromThreadLocal(): CorrelationId? {
            return MDC.get(CorrelationIdHeader)?.let { CorrelationId(it) } ?: null.also {
                log.error(
                    "Mangler '$CorrelationIdHeader' på MDC. Er dette et asynk-kall? Da må det settes manuelt, så tidlig som mulig.",
                    RuntimeException("Genererer en stacktrace for enklere debugging."),
                )
            }
        }

        /** Henter [CorrelationIdHeader] fra MDC dersom den finnes eller genererer en ny og legger den på MDC. */
        fun getOrCreateCorrelationIdFromThreadLocal(): CorrelationId {
            return getCorrelationIdFromThreadLocal() ?: (
                generate().also {
                    MDC.put(CorrelationIdHeader, it.toString())
                }
                )
        }

        /**
         * Denne er kun tenkt brukt fra coroutines/jobber/consumers som ikke er knyttet til Ktor.
         * Ktor håndterer dette ved hjelp av [io.ktor.server.plugins.callid.callIdMdc]
         *
         * Overskriver den nåværende key-value paret i MDC.
         * Fjerner keyen etter body() har kjørt ferdig.
         */
        infix fun withCorrelationId(body: (CorrelationId) -> Unit) {
            runBlocking {
                withCorrelationIdSuspend(body)
            }
        }

        suspend infix fun withCorrelationIdSuspend(body: suspend (CorrelationId) -> Unit) {
            val correlationId = generate()
            try {
                MDC.put(CorrelationIdHeader, correlationId.toString())
                body(correlationId)
            } finally {
                Either.catch {
                    MDC.remove(CorrelationIdHeader)
                }.onLeft {
                    log.error("En ukjent feil skjedde når vi prøvde fjerne $CorrelationIdHeader fra MDC.", it)
                }
            }
        }
    }
}
