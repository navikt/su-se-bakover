package no.nav.su.se.bakover.database

import arrow.core.Either
import arrow.core.getOrHandle
import kotliquery.using
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withTransaction
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Holder en transaksjon på tvers av repo-kall.
 * Ikke tråd-sikker.
 */
internal class PostgresTransactionContext(
    private val dataSource: DataSource,
    private val timedDbMetrics: DbMetrics,
    private val sessionCounter: SessionCounter,
) : TransactionContext {

    private val log = LoggerFactory.getLogger(this::class.java)

    // Det er viktig at sesjoner ikke opprettes utenfor en try-with-resource, som kan føre til connection-lekkasjer.
    private var transactionalSession: TransactionalSession? = null

    companion object {
        /**
         * Første kall lager en ny transaksjonell sesjon og lukkes automatisk sammen med funksjonsblokka..
         * Påfølgende kall gjenbruker samme transaksjon.
         *
         * * @throws IllegalStateException dersom den transaksjonelle sesjonen er lukket.
         */
        // Dette er en extension function og ikke en funksjon i interfacet siden vi ikke ønsker en referanse til Session, som er infrastrukturspesifikt, i domenelaget.
        fun <T> TransactionContext.withTransaction(action: (TransactionalSession) -> T): T {
            this as PostgresTransactionContext
            return if (transactionalSession == null) {
                // Vi ønsker kun at den ytterste blokka lukker sesjonen (using)
                using(sessionOf(dataSource, timedDbMetrics)) { session ->
                    session.transaction { transactionalSession ->
                        this.transactionalSession = transactionalSession
                        sessionCounter.withCountSessions {
                            action(transactionalSession)
                        }
                    }
                }
            } else {
                if (isClosed()) {
                    throw IllegalStateException("Den transaksjonelle sesjonen er lukket.")
                }
                action(transactionalSession!!)
            }
        }

        // Dette er en extension function og ikke en funksjon i interfacet siden vi ikke ønsker en referanse til Session, som er infrastrukturspesifikt, i domenelaget.
        /**
         * @throws IllegalStateException dersom man ikke har kalt [withTransaction] først eller den transaksjonelle sesjonen er lukket.
         */
        fun <T> TransactionContext.withSession(action: (session: Session) -> T): T {
            this as PostgresTransactionContext
            if (transactionalSession == null) {
                throw IllegalStateException("Må først starte en withTransaction(...) før man kan kalle withSession(...) for en TransactionContext.")
            }
            if (isClosed()) {
                throw IllegalStateException("Den transaksjonelle sesjonen er lukket.")
            }
            return action(transactionalSession!!)
        }
    }

    /**
     * @return true dersom 1) den aldri har vært åpnet 2) er lukket 3) en feil skjedde.
     */
    override fun isClosed(): Boolean {
        if (transactionalSession == null) return true
        return Either.catch { transactionalSession!!.connection.underlying.isClosed }.getOrHandle {
            log.error("En feil skjedde når vi prøvde å sjekke om den den transaksjonelle sesjonen var lukket", it)
            true
        }
    }
}
