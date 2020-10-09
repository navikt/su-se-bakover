package no.nav.su.se.bakover.client.stubs.pdf

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.pdf.Vedtakstype
import no.nav.su.se.bakover.domain.AvsluttSøknadsBehandlingBody
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.VedtakInnhold

object PdfGeneratorStub : PdfGenerator {

    val pdf =
        """%PDF-1.0
                1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/MediaBox[0 0 3 3]>>endobj
                xref
                0 4
                0000000000 65535 f
                0000000010 00000 n
                0000000053 00000 n
                0000000102 00000 n
                trailer<</Size 4/Root 1 0 R>>
                startxref
                149
                %EOF
        """.trimIndent()

    override fun genererPdf(søknad: SøknadInnhold): Either<ClientError, ByteArray> {
        return pdf.toByteArray().right()
    }

    override fun genererPdf(vedtak: VedtakInnhold, vedtakstype: Vedtakstype): Either<ClientError, ByteArray> {
        return pdf.toByteArray().right()
    }

    override fun genererAvsluttetSøknaddsBehandlingPdf(
        avsluttSøknadsBehandlingBody: AvsluttSøknadsBehandlingBody,
        vedtakstype: Vedtakstype
    ): Either<ClientError, ByteArray> {
        return pdf.toByteArray().right()
    }
}
