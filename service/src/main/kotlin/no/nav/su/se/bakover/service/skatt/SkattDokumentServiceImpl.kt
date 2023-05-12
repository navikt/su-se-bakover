package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeGenerereSkattedokument
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.dokument.DokumentService

class SkattDokumentServiceImpl(
    private val dokumentservice: DokumentService
) : SkattDokumentService {
    override fun generer(vedtak: Vedtak): Either<KunneIkkeGenerereSkattedokument, Skattedokument> {
        return dokumentservice.lagSkattedokumentFor(vedtak)
    }
}
