package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface RevurderingRepo {
    fun hent(id: UUID): AbstraktRevurdering?
    fun lagre(revurdering: AbstraktRevurdering, transactionContext: TransactionContext = defaultTransactionContext())
    fun defaultTransactionContext(): TransactionContext
}
