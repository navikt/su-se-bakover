package no.nav.su.se.bakover.domain.revurdering.beregning

sealed class KunneIkkeBeregneRevurdering {
    object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneRevurdering()

    data class UgyldigBeregningsgrunnlag(
        val reason: no.nav.su.se.bakover.domain.beregning.UgyldigBeregningsgrunnlag,
    ) : KunneIkkeBeregneRevurdering()

    object KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps : KunneIkkeBeregneRevurdering()
    object AvkortingErUfullstendig : KunneIkkeBeregneRevurdering()
    object OpphørAvYtelseSomSkalAvkortes : KunneIkkeBeregneRevurdering()
}
