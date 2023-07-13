package no.nav.su.se.bakover.domain.revurdering.beregning

import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import kotlin.reflect.KClass

sealed class KunneIkkeBeregneOgSimulereRevurdering {
    data object FantIkkeRevurdering : KunneIkkeBeregneOgSimulereRevurdering()
    data object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneOgSimulereRevurdering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeBeregneOgSimulereRevurdering()

    data class UgyldigBeregningsgrunnlag(
        val reason: no.nav.su.se.bakover.domain.beregning.UgyldigBeregningsgrunnlag,
    ) : KunneIkkeBeregneOgSimulereRevurdering()

    data object KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps : KunneIkkeBeregneOgSimulereRevurdering()

    data class KunneIkkeSimulere(val simuleringFeilet: SimulerUtbetalingFeilet) :
        KunneIkkeBeregneOgSimulereRevurdering()

    data object AvkortingErUfullstendig : KunneIkkeBeregneOgSimulereRevurdering()
    data object OpphørAvYtelseSomSkalAvkortes : KunneIkkeBeregneOgSimulereRevurdering()
}
