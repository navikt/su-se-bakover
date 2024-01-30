package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeGenerereSkattedokument
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import vilkår.skatt.domain.Skattedokument

interface SkattDokumentService {
    fun genererOgLagre(
        vedtak: Stønadsvedtak,
        txc: TransactionContext,
    ): Either<KunneIkkeGenerereSkattedokument, Skattedokument>

    fun lagre(skattedokument: Skattedokument, txc: TransactionContext)

    fun genererSkattePdf(request: GenererSkattPdfRequest): Either<KunneIkkeHenteOgLagePdfAvSkattegrunnlag, PdfA>
    fun genererSkattePdfOgJournalfør(request: GenererSkattPdfRequest): Either<KunneIkkeGenerereSkattePdfOgJournalføre, PdfA>
}
