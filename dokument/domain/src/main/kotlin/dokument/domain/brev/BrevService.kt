package dokument.domain.brev

import arrow.core.Either
import dokument.domain.Dokument
import dokument.domain.GenererDokumentCommand
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.distribuering.DistribuerDokumentCommand
import dokument.domain.distribuering.KunneIkkeDistribuereJournalførtDokument
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface BrevService {

    fun lagDokument(
        command: GenererDokumentCommand,
        id: UUID = UUID.randomUUID(),
    ): Either<KunneIkkeLageDokument, Dokument.UtenMetadata>

    fun hentDokument(id: UUID): Either<FantIkkeDokument, Dokument.MedMetadata>

    /** Krever transactionContext siden vi gjør 2 inserts. */
    fun lagreDokument(dokument: Dokument.MedMetadata, transactionContext: TransactionContext? = null)
    fun hentDokumenterFor(hentDokumenterForIdType: HentDokumenterForIdType): List<Dokument>

    /**
     * Synkron måte å distribuere et allerede generert og journalført dokument.
     */
    fun distribuerDokument(command: DistribuerDokumentCommand): Either<KunneIkkeDistribuereJournalførtDokument, Dokument>
}

object FantIkkeDokument
