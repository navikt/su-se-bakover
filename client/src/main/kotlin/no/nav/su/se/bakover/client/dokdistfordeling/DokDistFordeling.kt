package no.nav.su.se.bakover.client.dokdistfordeling

import arrow.core.Either
import dokument.domain.brev.Distribusjonstidspunkt
import dokument.domain.brev.Distribusjonstype
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.brev.BrevbestillingId

interface DokDistFordeling {
    fun bestillDistribusjon(
        journalPostId: JournalpostId,
        distribusjonstype: Distribusjonstype,
        distribusjonstidspunkt: Distribusjonstidspunkt,
    ): Either<KunneIkkeBestilleDistribusjon, BrevbestillingId>
}

data object KunneIkkeBestilleDistribusjon
