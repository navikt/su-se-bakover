package dokument.domain.brev

import arrow.core.Either
import dokument.domain.Dokument
import dokument.domain.GenererDokumentCommand
import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface BrevService {
    // TODO: endre navn på denne til lagDokumentPdf
    fun lagDokumentPdf(
        command: GenererDokumentCommand,
        id: UUID = UUID.randomUUID(),
    ): Either<KunneIkkeLageDokument, Dokument.UtenMetadata>

    fun hentDokument(id: UUID): Either<FantIkkeDokument, Dokument.MedMetadata>

    /** Krever transactionContext siden vi gjør 2 inserts. */
    fun lagreDokument(dokument: Dokument.MedMetadata, transactionContext: TransactionContext? = null)
    fun hentDokumenterFor(hentDokumenterForIdType: HentDokumenterForIdType): List<Dokument>
}

object FantIkkeDokument
