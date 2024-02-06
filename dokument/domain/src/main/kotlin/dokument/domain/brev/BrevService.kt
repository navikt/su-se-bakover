package dokument.domain.brev

import arrow.core.Either
import dokument.domain.Dokument
import dokument.domain.GenererDokumentCommand
import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface BrevService {

    fun lagDokument(
        command: GenererDokumentCommand,
        id: UUID = UUID.randomUUID(),
    ): Either<KunneIkkeLageDokument, Dokument.UtenMetadata>

    fun lagreDokument(dokument: Dokument.MedMetadata)
    fun lagreDokument(dokument: Dokument.MedMetadata, transactionContext: TransactionContext)
    fun hentDokumenterFor(hentDokumenterForIdType: HentDokumenterForIdType): List<Dokument>
}
