package no.nav.su.se.bakover.service.dokument

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.pdf.SkattePdf
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeGenerereSkattedokument
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import java.util.UUID

class DokumentServiceImpl(
    private val pdfGenerator: PdfGenerator
) : DokumentService {
    override fun lagSkattedokumentFor(vedtak: Stønadsvedtak): Either<KunneIkkeGenerereSkattedokument, Skattedokument> {
        TODO()
    }
}
