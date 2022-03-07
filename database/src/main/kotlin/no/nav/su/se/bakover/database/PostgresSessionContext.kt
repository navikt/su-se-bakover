package no.nav.su.se.bakover.database

import arrow.core.Either
import arrow.core.getOrHandle
import kotliquery.using
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import org.slf4j.LoggerFactory
import javax.sql.DataSource
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withSession as transactionContextWithSession

internal open class PostgresSessionContext(
    private val dataSource: DataSource,
    private val dbMetrics: DbMetrics,
    private val sessionCounter: SessionCounter,
) : SessionContext {

    private val log = LoggerFactory.getLogger(this::class.java)

    // Det er viktig at sesjoner ikke opprettes utenfor en try-with-resource, som kan føre til connection-lekkasjer.
    private var session: Session? = null

    companion object {
        /**
         * Første kall lager en ny sesjon og lukkes automatisk sammen med funksjonsblokka.
         * Påfølgende kall gjenbruker samme sesjon.
         *
         * * @throws IllegalStateException dersom sesjonen er lukket.
         */
        // Dette er en extension function og ikke en funksjon i interfacet siden vi ikke ønsker en referanse til Session, som er infrastrukturspesifikt, i domenelaget.
        fun <T> SessionContext.withSession(action: (session: Session) -> T): T {
            if (this is TransactionContext) {
                return this.transactionContextWithSession(action)
            }
            this as PostgresSessionContext
            return if (session == null) {
                // Vi ønsker kun at den ytterste blokka lukker sesjonen (using)
                using(sessionOf(dataSource, dbMetrics).also { session = it }) {
                    sessionCounter.withCountSessions {
                        action(it)
                    }
                }
            } else {
                if (isClosed()) {
                    throw IllegalStateException("Sesjonen er lukket.")
                }
                action(session!!)
            }
        }
    }

    /**
     * @return true dersom 1) den aldri har vært åpnet 2) er lukket 3) en feil skjedde.
     */
    override fun isClosed(): Boolean {
        if (session == null) return true
        return Either.catch { session!!.connection.underlying.isClosed }.getOrHandle {
            log.error("En feil skjedde når vi prøvde å sjekke om sesjonen var lukket", it)
            true
        }
    }
}
