package no.nav.su.se.bakover.domain.søknadsbehandling.beregn

import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilGrunnlag

sealed interface KunneIkkeBeregne {

    data class UgyldigTilstandForEndringAvFradrag(val feil: KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag) :
        KunneIkkeBeregne
}
