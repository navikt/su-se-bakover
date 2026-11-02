package dokument.domain.brev

import arrow.core.Either
import dokument.domain.Dokument
import dokument.domain.DokumentPdf
import dokument.domain.GenererDokumentCommand
import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.util.UUID

interface BrevService {
    fun lagDokumentPdf(
        command: GenererDokumentCommand,
        id: UUID = UUID.randomUUID(),
    ): Either<KunneIkkeLageDokument, Dokument.UtenMetadata>

    fun lagDokumentMedForsideSomVedlegg(
        command: GenererDokumentCommand,
        id: UUID = UUID.randomUUID(),
    ): Either<KunneIkkeLageDokument, Dokument.UtenMetadata>

    fun leggTilForsideSomVedleggPåOpplastetPdf(
        pdf: PdfA,
        brukerId: String,
        sakstype: Sakstype,
    ): Either<KunneIkkeLageDokument, PdfA>

    fun hentDokumentPdf(id: UUID): Either<FantIkkeDokument, DokumentPdf>

    /** Krever transactionContext siden vi gjør 2 inserts. */
    fun lagreDokument(dokument: Dokument.MedMetadata, transactionContext: TransactionContext? = null)
    fun hentDokumenterFor(hentDokumenterForIdType: HentDokumenterForIdType): List<Dokument>
}

object FantIkkeDokument
