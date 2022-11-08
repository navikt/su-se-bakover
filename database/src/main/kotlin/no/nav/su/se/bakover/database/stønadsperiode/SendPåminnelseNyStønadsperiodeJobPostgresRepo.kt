package no.nav.su.se.bakover.database.stønadsperiode

import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.database.jobcontext.JobContextPostgresRepo
import no.nav.su.se.bakover.database.stønadsperiode.SendPåminnelseNyStønadsperiodeContextDb.Companion.toDb
import no.nav.su.se.bakover.domain.jobcontext.JobContextId
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.domain.stønadsperiode.SendPåminnelseNyStønadsperiodeJobRepo

class SendPåminnelseNyStønadsperiodeJobPostgresRepo(
    private val repo: JobContextPostgresRepo,
) : SendPåminnelseNyStønadsperiodeJobRepo {
    override fun hent(id: JobContextId): SendPåminnelseNyStønadsperiodeContext? {
        return repo.hent(
            id = id,
            deserialize = { SendPåminnelseNyStønadsperiodeContextDb.fromDbJson(it) },
        )
    }

    override fun lagre(
        context: SendPåminnelseNyStønadsperiodeContext,
        transactionContext: TransactionContext,
    ) {
        repo.lagre(
            jobContext = context,
            serialize = { context.toDb().toJson() },
            transactionContext = transactionContext,
        )
    }
}
