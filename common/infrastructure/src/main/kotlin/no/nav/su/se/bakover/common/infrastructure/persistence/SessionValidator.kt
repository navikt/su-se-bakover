package no.nav.su.se.bakover.common.infrastructure.persistence

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.concurrent.getOrSet

private val logger: Logger = LoggerFactory.getLogger(SessionValidator::class.java)

class SessionValidator(
    private val whenOverThreshold: (numberOfSession: Int) -> Unit = {
        logger.error(
            "Sessions per thread over threshold: We started a new session while a session for this thread was already open. Total number of sessions: $it.",
            RuntimeException("Genererer en stacktrace for enklere debugging."),
        )
    },
) {
    private val activeSessionsPerThread: ThreadLocal<Int> = ThreadLocal()

    /*
        https://news.ycombinator.com/item?id=28374333
     */
    fun <T> validateNotNestedSession(action: () -> T): T {
        val numSessions = activeSessionsPerThread.getOrSet { 0 }.inc()
        return if (numSessions > 1) {
            whenOverThreshold(numSessions)
            throw IllegalStateException("Started a new session while a session for this thread was already open. Total number of sessions: $numSessions.")
        } else {
            activeSessionsPerThread.set(numSessions)
            try {
                action()
            } finally {
                activeSessionsPerThread.set(activeSessionsPerThread.getOrSet { 1 }.dec())
            }
        }
    }
}
