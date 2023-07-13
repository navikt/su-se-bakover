package no.nav.su.se.bakover.domain.revurdering.beregning

sealed class KunneIkkeBeregneRevurdering {
    data object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneRevurdering()

    data class UgyldigBeregningsgrunnlag(
        val reason: no.nav.su.se.bakover.domain.beregning.UgyldigBeregningsgrunnlag,
    ) : KunneIkkeBeregneRevurdering()

    data object KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps : KunneIkkeBeregneRevurdering()
    data object AvkortingErUfullstendig : KunneIkkeBeregneRevurdering()
    data object OpphørAvYtelseSomSkalAvkortes : KunneIkkeBeregneRevurdering()
}
