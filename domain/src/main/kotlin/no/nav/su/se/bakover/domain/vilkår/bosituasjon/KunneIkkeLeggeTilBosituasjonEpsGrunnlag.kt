package no.nav.su.se.bakover.domain.vilkår.bosituasjon

import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag

sealed class KunneIkkeLeggeTilBosituasjonEpsGrunnlag {
    object FantIkkeBehandling : KunneIkkeLeggeTilBosituasjonEpsGrunnlag()

    object KlarteIkkeHentePersonIPdl : KunneIkkeLeggeTilBosituasjonEpsGrunnlag()

    data class KunneIkkeOppdatereBosituasjon(val feil: KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon) :
        KunneIkkeLeggeTilBosituasjonEpsGrunnlag()
}
