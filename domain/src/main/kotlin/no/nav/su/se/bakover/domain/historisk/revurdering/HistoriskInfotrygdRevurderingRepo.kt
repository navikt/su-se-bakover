package no.nav.su.se.bakover.domain.historisk.revurdering

import arrow.core.Either
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.tid.periode.Periode
import java.util.UUID

interface HistoriskInfotrygdRevurderingRepo {
    fun opprett(
        revurdering: HistoriskInfotrygdRevurdering,
        transactionContext: TransactionContext = defaultTransactionContext(),
    ): Either<KunneIkkeOppretteHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering>

    fun lagre(
        revurdering: HistoriskInfotrygdRevurdering,
        transactionContext: TransactionContext = defaultTransactionContext(),
    )

    fun hent(id: HistoriskInfotrygdRevurderingId): HistoriskInfotrygdRevurdering?

    fun hentForSak(sakId: UUID): List<HistoriskInfotrygdRevurdering>

    fun lagreVedtak(
        vedtak: HistoriskInfotrygdRevurderingsvedtak,
        transactionContext: TransactionContext = defaultTransactionContext(),
    )

    fun hentVedtakForUtbetaling(
        utbetalingId: UUID30,
        sessionContext: SessionContext? = null,
    ): HistoriskInfotrygdRevurderingsvedtak?

    fun hentIverksatteEffekter(
        sakId: UUID,
        periode: Periode,
    ): List<HistoriskInfotrygdRevurderingseffekt>

    fun defaultTransactionContext(): TransactionContext
}
