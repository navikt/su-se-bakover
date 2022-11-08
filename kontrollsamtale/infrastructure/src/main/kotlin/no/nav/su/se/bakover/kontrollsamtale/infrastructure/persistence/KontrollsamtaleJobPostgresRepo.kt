package no.nav.su.se.bakover.kontrollsamtale.infrastructure.persistence

import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.database.jobcontext.JobContextPostgresRepo
import no.nav.su.se.bakover.domain.jobcontext.JobContextId
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleJobRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleContext
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.persistence.UtløptFristForKontrollsamtaleDb.Companion.toDb

class KontrollsamtaleJobPostgresRepo(
    private val repo: JobContextPostgresRepo,
) : KontrollsamtaleJobRepo {

    override fun hent(id: JobContextId): UtløptFristForKontrollsamtaleContext? {
        return repo.hent(
            id = id,
            deserialize = { UtløptFristForKontrollsamtaleDb.fromDbJson(it) },
        )
    }

    override fun lagre(
        context: UtløptFristForKontrollsamtaleContext,
        transactionContext: TransactionContext,
    ) {
        repo.lagre(
            jobContext = context,
            serialize = { context.toDb().toJson() },
            transactionContext = transactionContext,
        )
    }
}
