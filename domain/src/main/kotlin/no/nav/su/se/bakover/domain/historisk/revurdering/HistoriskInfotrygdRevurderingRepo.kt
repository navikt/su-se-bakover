package no.nav.su.se.bakover.domain.historisk.revurdering

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.tid.periode.Periode
import java.util.UUID

interface HistoriskInfotrygdRevurderingRepo {
    fun opprett(
        revurdering: HistoriskInfotrygdRevurdering,
    ): Either<KunneIkkeOppretteHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering>

    fun lagre(
        revurdering: HistoriskInfotrygdRevurdering,
        transactionContext: TransactionContext = defaultTransactionContext(),
    )

    fun hent(id: HistoriskInfotrygdRevurderingId): HistoriskInfotrygdRevurdering?

    fun hentForSak(sakId: UUID): List<HistoriskInfotrygdRevurdering>

    fun lagreVedtak(vedtak: HistoriskInfotrygdRevurderingsvedtak)

    fun hentIverksatteMånedsresultater(
        sakId: UUID,
        periode: Periode,
    ): List<IverksatteMånedsresultater>

    fun defaultTransactionContext(): TransactionContext
}
