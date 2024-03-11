package no.nav.su.se.bakover.domain.revurdering.beregning

sealed interface KunneIkkeBeregneRevurdering {
    data object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneRevurdering

    data class UgyldigBeregningsgrunnlag(
        val reason: beregning.domain.UgyldigBeregningsgrunnlag,
    ) : KunneIkkeBeregneRevurdering

    data object KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps : KunneIkkeBeregneRevurdering
}
