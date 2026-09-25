package no.nav.su.se.bakover.domain.historisk.revurdering

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext

interface HistoriskInfotrygdRevurderingRepo {
    fun opprett(
        revurdering: HistoriskInfotrygdRevurdering,
        transactionContext: TransactionContext = defaultTransactionContext(),
    ): Either<KunneIkkeOppretteHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering>

    fun lagre(
        revurdering: HistoriskInfotrygdRevurdering,
        forventetVersjon: Long,
        transactionContext: TransactionContext = defaultTransactionContext(),
    ): Boolean

    fun hent(id: HistoriskInfotrygdRevurderingId): HistoriskInfotrygdRevurdering?

    fun lagreVedtak(
        vedtak: HistoriskInfotrygdRevurderingsvedtak,
        transactionContext: TransactionContext = defaultTransactionContext(),
    )

    fun hentVedtakForUtbetaling(
        utbetalingId: UUID30,
        sessionContext: SessionContext? = null,
    ): HistoriskInfotrygdRevurderingsvedtak?

    fun defaultTransactionContext(): TransactionContext
}
