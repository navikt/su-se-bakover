package no.nav.su.se.bakover.client.stubs.dokdistfordeling

import arrow.core.Either
import arrow.core.right
import dokument.domain.Distribusjonstidspunkt
import dokument.domain.Distribusjonstype
import dokument.domain.brev.BrevbestillingId
import dokument.domain.distribuering.Distribueringsadresse
import dokument.domain.distribuering.DokDistFordeling
import dokument.domain.distribuering.KunneIkkeBestilleDistribusjon
import no.nav.su.se.bakover.common.journal.JournalpostId

data object DokDistFordelingStub : DokDistFordeling {
    override fun bestillDistribusjon(
        journalpostId: JournalpostId,
        distribusjonstype: Distribusjonstype,
        distribusjonstidspunkt: Distribusjonstidspunkt,
        distribueringsadresse: Distribueringsadresse?,
    ): Either<KunneIkkeBestilleDistribusjon, BrevbestillingId> = BrevbestillingId("51be490f-5a21-44dd-8d38-d762491d6b22").right()
}
