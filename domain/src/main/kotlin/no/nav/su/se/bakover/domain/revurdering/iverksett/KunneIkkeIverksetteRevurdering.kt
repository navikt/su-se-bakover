package no.nav.su.se.bakover.domain.revurdering.iverksett

import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import kotlin.reflect.KClass

sealed interface KunneIkkeIverksetteRevurdering {
    data class IverksettelsestransaksjonFeilet(
        val feil: KunneIkkeFerdigstilleIverksettelsestransaksjon,
    ) : KunneIkkeIverksetteRevurdering

    sealed interface Saksfeil : KunneIkkeIverksetteRevurdering {
        data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : Saksfeil
        object FantIkkeRevurdering : Saksfeil

        object SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving : Saksfeil

        data class UgyldigTilstand(
            val fra: KClass<out AbstraktRevurdering>,
            val til: KClass<out AbstraktRevurdering>,
        ) : Saksfeil

        data class Revurderingsfeil(val underliggende: RevurderingTilAttestering.KunneIkkeIverksetteRevurdering) : Saksfeil

        object DetHarKommetNyeOverlappendeVedtak : Saksfeil
    }
}
