package no.nav.su.se.bakover.kontrollsamtale.domain

import no.nav.su.se.bakover.common.domain.job.JobContextId
import no.nav.su.se.bakover.common.persistence.TransactionContext

interface KontrollsamtaleJobRepo {
    fun hent(id: JobContextId): UtløptFristForKontrollsamtaleContext?
    fun lagre(
        context: UtløptFristForKontrollsamtaleContext,
        transactionContext: TransactionContext,
    )
}
