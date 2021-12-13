package no.nav.su.se.bakover.domain.avkorting

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface AvkortingsvarselRepo {
    fun hentUteståendeAvkortinger(
        sakId: UUID,
        sessionContext: SessionContext = defaultSessionContext(),
    ): List<Avkortingsvarsel.Utenlandsopphold.SkalAvkortes>

    fun lagre(
        avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.Avkortet,
        transactionContext: TransactionContext = defaultTransactionContext()
    )

    fun defaultSessionContext(): SessionContext
    fun defaultTransactionContext(): TransactionContext
}
