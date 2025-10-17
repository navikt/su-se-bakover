package no.nav.su.se.bakover.test

import io.kotest.assertions.fail
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import kotlin.concurrent.getOrSet

class TestSessionFactory : SessionFactory {

    companion object {
        // Gjør det enklere å verifisere i testene.
        val sessionContext = object : SessionContext {
            override fun isClosed() = false
        }
        val transactionContext = object : TransactionContext {
            override fun isClosed() = false
        }
    }

    override fun <T> withSessionContext(action: (SessionContext) -> T): T =
        SessionCounter().withCountSessions { action(sessionContext) }

    override fun <T> withSessionContext(sessionContext: SessionContext?, action: (SessionContext) -> T): T {
        return SessionCounter().withCountSessions { action(sessionContext ?: TestSessionFactory.sessionContext) }
    }

    override fun <T> withTransactionContext(action: (TransactionContext) -> T): T =
        SessionCounter().withCountSessions { action(transactionContext) }

    override fun <T> withTransactionContext(transactionContext: TransactionContext?, action: (TransactionContext) -> T): T {
        return SessionCounter().withCountSessions { action(transactionContext ?: TestSessionFactory.transactionContext) }
    }

    override fun <T> use(transactionContext: TransactionContext, action: (TransactionContext) -> T): T {
        return SessionCounter().withCountSessions { action(transactionContext) }
    }

    override fun newSessionContext() = sessionContext
    override fun newTransactionContext() = transactionContext
    override fun close() {
        // Do nothing. Kan ikke lukke en raw datasource
    }

    // TODO jah: Denne er duplikat med den som ligger i database siden test-common ikke har en referanse til database-modulen.
    private class SessionCounter {
        private val activeSessionsPerThread: ThreadLocal<Int> = ThreadLocal()

        fun <T> withCountSessions(action: () -> T): T {
            return activeSessionsPerThread.getOrSet { 0 }.inc().let {
                if (it > 1) {
                    fail("Database sessions were over the threshold while running test.")
                }
                activeSessionsPerThread.set(it)
                try {
                    action()
                } finally {
                    activeSessionsPerThread.set(activeSessionsPerThread.getOrSet { 1 }.dec())
                }
            }
        }
    }
}
