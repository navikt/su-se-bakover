package no.nav.su.se.bakover.kontrollsamtale.domain

import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.jobcontext.JobContextId

interface KontrollsamtaleJobRepo {
    fun hent(id: JobContextId): UtløptFristForKontrollsamtaleContext?
    fun lagre(
        context: UtløptFristForKontrollsamtaleContext,
        transactionContext: TransactionContext,
    )
}
