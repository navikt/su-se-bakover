package no.nav.su.se.bakover.domain.sak.iverksett

import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import kotlin.reflect.KClass

sealed interface KunneIkkeIverksetteRevurdering {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteRevurdering
    data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : KunneIkkeIverksetteRevurdering
    object FantIkkeRevurdering : KunneIkkeIverksetteRevurdering
    object HarAlleredeBlittAvkortetAvEnAnnen : KunneIkkeIverksetteRevurdering
    object SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving : KunneIkkeIverksetteRevurdering

    data class UgyldigTilstand(
        val fra: KClass<out AbstraktRevurdering>,
        val til: KClass<out AbstraktRevurdering>,
    ) : KunneIkkeIverksetteRevurdering
}
