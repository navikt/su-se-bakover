package no.nav.su.se.bakover.domain.mottaker

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface MottakerService {
    fun hentMottaker(
        mottakerIdentifikator: MottakerIdentifikator,
        sakId: UUID,
        transactionContext: TransactionContext? = null,
    ): Either<FeilkoderMottaker, MottakerDomain?>

    fun lagreMottaker(mottaker: LagreMottaker, sakId: UUID): Either<FeilkoderMottaker, MottakerDomain>
    fun oppdaterMottaker(mottaker: OppdaterMottaker, sakId: UUID): Either<FeilkoderMottaker, Unit>
    fun slettMottaker(mottakerIdentifikator: MottakerIdentifikator, sakId: UUID): Either<FeilkoderMottaker, Unit>
}
