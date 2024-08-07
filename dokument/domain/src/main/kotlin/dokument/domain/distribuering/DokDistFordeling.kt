package dokument.domain.distribuering

import arrow.core.Either
import dokument.domain.Distribusjonstidspunkt
import dokument.domain.Distribusjonstype
import dokument.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.common.journal.JournalpostId

interface DokDistFordeling {
    fun bestillDistribusjon(
        journalpostId: JournalpostId,
        distribusjonstype: Distribusjonstype,
        distribusjonstidspunkt: Distribusjonstidspunkt,
        distribueringsadresse: Distribueringsadresse? = null,
    ): Either<KunneIkkeBestilleDistribusjon, BrevbestillingId>
}
