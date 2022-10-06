package no.nav.su.se.bakover.common.persistence

/** Holder en sesjon åpen på tvers av repo-kall. Ikke trådsikker. */
interface SessionContext {
    /**
     * @return true dersom 1) den aldri har vært åpnet 2) er lukket 3) en feil skjedde.
     */
    fun isClosed(): Boolean
}

/** Holder en transaksjon åpen på tvers av repo-kall. Ikke trådsikker. */
interface TransactionContext : SessionContext

/** Starter og lukker nye sesjoner og transaksjoner */
interface SessionFactory {
    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    fun <T> withSessionContext(action: (SessionContext) -> T): T

    /** Lager en ny context og starter sesjonen - lukkes automatisk  */
    fun <T> withTransactionContext(action: (TransactionContext) -> T): T

    /** Bruker en eksisterende context og starter sesjonen hvis den ikke er åpen */
    fun <T> use(transactionContext: TransactionContext, action: (TransactionContext) -> T): T

    fun newSessionContext(): SessionContext

    fun newTransactionContext(): TransactionContext
}
