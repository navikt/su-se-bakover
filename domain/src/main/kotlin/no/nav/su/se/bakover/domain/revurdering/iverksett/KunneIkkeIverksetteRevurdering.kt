package no.nav.su.se.bakover.domain.revurdering.iverksett

import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import kotlin.reflect.KClass

sealed interface KunneIkkeIverksetteRevurdering {
    data class IverksettelsestransaksjonFeilet(
        val feil: KunneIkkeFerdigstilleIverksettelsestransaksjon,
    ) : KunneIkkeIverksetteRevurdering

    sealed interface Saksfeil : KunneIkkeIverksetteRevurdering {
        data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : Saksfeil
        data class KontrollsimuleringFeilet(val feil: SimulerUtbetalingFeilet) : Saksfeil
        data object FantIkkeRevurdering : Saksfeil

        data object SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving : Saksfeil

        data class UgyldigTilstand(
            val fra: KClass<out AbstraktRevurdering>,
            val til: KClass<out AbstraktRevurdering>,
        ) : Saksfeil

        data class Revurderingsfeil(val underliggende: RevurderingTilAttestering.KunneIkkeIverksetteRevurdering) : Saksfeil

        data object DetHarKommetNyeOverlappendeVedtak : Saksfeil

        data class KunneIkkeGenerereDokument(val feil: KunneIkkeLageDokument) : Saksfeil
    }
}
