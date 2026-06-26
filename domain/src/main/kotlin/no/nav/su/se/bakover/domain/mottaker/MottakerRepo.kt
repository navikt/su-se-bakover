package no.nav.su.se.bakover.domain.mottaker

import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface MottakerRepo {
    fun hentMottaker(
        mottakerIdentifikator: MottakerIdentifikator,
        transactionContext: TransactionContext? = null,
    ): MottakerDomain?

    fun lagreMottaker(mottaker: MottakerDomain)

    fun oppdaterMottaker(mottaker: MottakerDomain)

    fun slettMottaker(mottakerId: UUID)
}
