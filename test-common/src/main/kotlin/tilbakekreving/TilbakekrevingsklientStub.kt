package no.nav.su.se.bakover.test.tilbakekreving

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.domain.vedtak.KunneIkkeAnnullerePåbegynteVedtak
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

    override fun annullerKravgrunnlag(
        annullertAv: NavIdentBruker.Saksbehandler,
        kravgrunnlagSomSkalAnnulleres: Kravgrunnlag,
    ): Either<KunneIkkeAnnullerePåbegynteVedtak, RåTilbakekrevingsvedtakForsendelse> =
        RåTilbakekrevingsvedtakForsendelse(
            requestXml = "{\"requestJson\": \"stubbed\"}",
            tidspunkt = Tidspunkt.now(clock),
            responseXml = "{\"responseJson\": \"stubbed\"}",
        ).right()
}
