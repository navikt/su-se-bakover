package no.nav.su.se.bakover.domain.stønadsperiode

import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.jobcontext.JobContextId
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext

interface SendPåminnelseNyStønadsperiodeJobRepo {
    fun hent(id: JobContextId): SendPåminnelseNyStønadsperiodeContext?
    fun lagre(
        context: SendPåminnelseNyStønadsperiodeContext,
        transactionContext: TransactionContext,
    )
}
