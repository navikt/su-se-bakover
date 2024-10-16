package tilbakekreving.domain.vedtak

import arrow.core.Either
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.domain.vurdering.VurderingerMedKrav

/**
 * Har ansvaret for å sende (svare ut) et tilbakekrevingsvedtak til økonomisystemet.
 * Dette er et synkront kall.
 */
interface Tilbakekrevingsklient {

    fun sendTilbakekrevingsvedtak(
        vurderingerMedKrav: VurderingerMedKrav,
        attestertAv: NavIdentBruker.Attestant,
    ): Either<KunneIkkeSendeTilbakekrevingsvedtak, RåTilbakekrevingsvedtakForsendelse>

    fun annullerKravgrunnlag(
        annullertAv: NavIdentBruker.Saksbehandler,
        kravgrunnlagSomSkalAnnulleres: Kravgrunnlag,
    ): Either<KunneIkkeAnnullerePåbegynteVedtak, RåTilbakekrevingsvedtakForsendelse>
}

sealed interface KunneIkkeAnnullerePåbegynteVedtak {
    data object FeilStatusFraOppdrag : KunneIkkeAnnullerePåbegynteVedtak
    data object UkjentFeil : KunneIkkeAnnullerePåbegynteVedtak
}
