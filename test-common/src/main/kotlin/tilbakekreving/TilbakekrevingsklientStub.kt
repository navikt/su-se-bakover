package no.nav.su.se.bakover.test.tilbakekreving

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.kravgrunnlag.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.domain.vedtak.KunneIkkeSendeTilbakekrevingsvedtak
import tilbakekreving.domain.vedtak.Tilbakekrevingsklient
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import java.time.Clock

data class TilbakekrevingsklientStub(
    val clock: Clock,
) : Tilbakekrevingsklient {

    override fun sendTilbakekrevingsvedtak(
        vurderingerMedKrav: VurderingerMedKrav,
        attestertAv: NavIdentBruker.Attestant,
    ): Either<KunneIkkeSendeTilbakekrevingsvedtak, RåTilbakekrevingsvedtakForsendelse> {
        return RåTilbakekrevingsvedtakForsendelse(
            requestXml = "{\"requestJson\": \"stubbed\"}",
            tidspunkt = Tidspunkt.now(clock),
            responseXml = "{\"responseJson\": \"stubbed\"}",
        ).right()
    }
}
