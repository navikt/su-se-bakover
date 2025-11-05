package no.nav.su.se.bakover.common.infrastructure.persistence

import com.zaxxer.hikari.HikariDataSource
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import javax.sql.DataSource

/**
 * Bør på sikt flyttes til common/infrastructure/persistence
 */
class PostgresSessionFactory(
    private val dataSource: DataSource,
    private val dbMetrics: DbMetrics,
    private val sessionValidator: SessionValidator,
    private val queryParameterMappers: List<QueryParameterMapper>,
) : SessionFactory {

    /** Lager en ny context - starter ikke sesjonen.
     * DEPRECATION: Use nullable paramters and withSessionContext(sessionContext: SessionContext?, action) instead */
    override fun newSessionContext(): PostgresSessionContext {
        return PostgresSessionContext(dataSource, dbMetrics, sessionValidator, queryParameterMappers)
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
    override fun <T> withSessionContext(sessionContext: SessionContext?, action: (SessionContext) -> T): T {
        return (sessionContext ?: newSessionContext()).let { context ->
            context.withSession {
                action(context)
            }
        }
    }

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    fun <T> withSession(
        disableSessionCounter: Boolean = false,
        action: (Session) -> T,
    ): T {
        return newSessionContext().let { context ->
            context.withSession(disableSessionCounter = disableSessionCounter) {
                action(it)
            }
        }
    }

    /** Gjenbruker [sessionContext] hvis den ikke er null, ellers lages en ny context og starter sesjonen - lukkes automatisk  */
    fun <T> withSession(
        sessionContext: SessionContext?,
        disableSessionCounter: Boolean = false,
        action: (Session) -> T,
    ): T {
        return withSessionContext(sessionContext) { context ->
            context.withSession(disableSessionCounter = disableSessionCounter) {
                action(it)
            }
        }
    }

    /**
     * Lager en ny context - starter ikke sesjonen.
     *
     * Merk: Man må kalle withTransaction {...} før man kaller withSession {...} hvis ikke får man en [IllegalStateException]
     * withSession {...} vil kjøre inne i den samme transaksjonen.
     *
     * DEPRECATION: Use nullable paramters and withTransactionContext(tx: TransactionContext?, action) instead
     * */
    override fun newTransactionContext(): PostgresTransactionContext {
        return PostgresTransactionContext(dataSource, dbMetrics, sessionValidator, queryParameterMappers)
    }

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    override fun <T> withTransactionContext(
        action: (TransactionContext) -> T,
    ): T {
        return newTransactionContext().let { context ->
            context.withTransaction {
                action(context)
            }
        }
    }

    /** Lager en ny context dersom den ikke finnes og starter sesjonen - lukkes automatisk  */
    override fun <T> withTransactionContext(
        transactionContext: TransactionContext?,
        action: (TransactionContext) -> T,
    ): T {
        return (transactionContext ?: newTransactionContext()).let { context ->
            context.withTransaction {
                action(context)
            }
        }
    }

    override fun <T> use(transactionContext: TransactionContext, action: (TransactionContext) -> T): T {
        return transactionContext.withTransaction {
            action(transactionContext)
        }
    }

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    fun <T> withTransaction(action: (TransactionalSession) -> T): T {
        return newTransactionContext().let { context ->
            context.withTransaction {
                action(it)
            }
        }
    }

    /** Lager en ny context dersom den ikke finnes og starter sesjonen - lukkes automatisk  */
    fun <T> withTransaction(
        transactionContext: TransactionContext?,
        action: (TransactionalSession) -> T,
    ): T {
        return withTransactionContext(transactionContext) {
            it.withTransaction {
                action(it)
            }
        }
    }

    /*
        TODO: datasource her burde vært en HikariDataSource men siden
        testene bruker denne og mange av repoklassene bruker denne og ikke testklassen ble det for mye å skrive om
     */
    override fun close() {
        if (dataSource is HikariDataSource) {
            dataSource.close()
        } else {
            throw IllegalStateException("DataSource is not a HikariDataSource: ${dataSource::class.qualifiedName}")
        }
    }
}
