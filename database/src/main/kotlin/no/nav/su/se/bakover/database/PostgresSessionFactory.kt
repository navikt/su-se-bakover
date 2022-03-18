package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.database.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withTransaction
import javax.sql.DataSource

internal class PostgresSessionFactory(
    private val dataSource: DataSource,
    private val dbMetrics: DbMetrics,
    private val sessionCounter: SessionCounter,
) : SessionFactory {

    /** Lager en ny context - starter ikke sesjonen */
    override fun newSessionContext(): PostgresSessionContext {
        return PostgresSessionContext(dataSource, dbMetrics, sessionCounter)
    }

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    override fun <T> withSessionContext(action: (SessionContext) -> T): T {
        return newSessionContext().let { context ->
            context.withSession {
                action(context)
            }
        }
    }

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    internal fun <T> withSession(action: (Session) -> T): T {
        return newSessionContext().let { context ->
            context.withSession {
                action(it)
            }
        }
    }

    /**
     * Lager en ny context - starter ikke sesjonen.
     *
     * Merk: Man må kalle withTransaction {...} før man kaller withSession {...} hvis ikke får man en [IllegalStateException]
     * withSession {...} vil kjøre inne i den samme transaksjonen.
     * */
    override fun newTransactionContext(): PostgresTransactionContext {
        return PostgresTransactionContext(dataSource, dbMetrics, sessionCounter)
    }

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    override fun <T> withTransactionContext(action: (TransactionContext) -> T): T {
        return newTransactionContext().let { context ->
            context.withTransaction {
                action(context)
            }
        }
    }

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    internal fun <T> withTransaction(action: (TransactionalSession) -> T): T {
        return newTransactionContext().let { context ->
            context.withTransaction {
                action(it)
            }
        }
    }
}
