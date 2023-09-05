package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeGenerereSkattedokument
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak

interface SkattDokumentService {
    fun genererOgLagre(
        vedtak: Stønadsvedtak,
        txc: TransactionContext,
    ): Either<KunneIkkeGenerereSkattedokument, Skattedokument>

    fun genererSkattePdf(
        begrunnelse: String,
        skattegrunnlag: Skattegrunnlag,
    ): Either<KunneIkkeHenteOgLagePdfAvSkattegrunnlag, PdfA>

    fun lagre(skattedokument: Skattedokument, txc: TransactionContext)
}
