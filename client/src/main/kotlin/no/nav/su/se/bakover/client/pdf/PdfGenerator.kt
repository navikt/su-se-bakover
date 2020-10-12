package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.VedtakInnhold
import java.util.UUID

enum class Vedtakstype(val template: String) {
    AVSLAG("vedtakAvslag"),
    INNVILGELSE("vedtakInnvilgelse"),
    TREKK_SØKNAD("trekkSøknad")
}

interface PdfGenerator {
    fun genererPdf(søknad: SøknadInnhold): Either<ClientError, ByteArray>
    fun genererPdf(vedtak: VedtakInnhold, vedtakstype: Vedtakstype): Either<ClientError, ByteArray>
    fun genererTrukketSøknadPdf(sakId: UUID, søknadId: UUID, vedtakstype: Vedtakstype): Either<ClientError, ByteArray>
}
