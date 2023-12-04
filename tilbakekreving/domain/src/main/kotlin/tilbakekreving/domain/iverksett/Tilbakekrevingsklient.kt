package tilbakekreving.domain.iverksett

import arrow.core.Either
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import tilbakekreving.domain.kravgrunnlag.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.domain.vurdert.VurderingerMedKrav

/**
 * Har ansvaret for å sende (svare ut) et tilbakekrevingsvedtak til økonomisystemet.
 * Dette er et synkront kall.
 */
interface Tilbakekrevingsklient {

    fun sendTilbakekrevingsvedtak(
        vurderingerMedKrav: VurderingerMedKrav,
        attestertAv: NavIdentBruker.Attestant,
    ): Either<KunneIkkeSendeTilbakekrevingsvedtak, RåTilbakekrevingsvedtakForsendelse>
}
