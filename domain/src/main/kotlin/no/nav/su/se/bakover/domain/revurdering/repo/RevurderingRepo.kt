package no.nav.su.se.bakover.domain.revurdering.repo

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingId

interface RevurderingRepo {
    fun hent(id: RevurderingId): AbstraktRevurdering?
    fun hent(id: RevurderingId, sessionContext: SessionContext): AbstraktRevurdering?
    fun lagre(revurdering: AbstraktRevurdering, transactionContext: TransactionContext = defaultTransactionContext())
    fun defaultTransactionContext(): TransactionContext
}
