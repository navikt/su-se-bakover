package no.nav.su.se.bakover.domain.søknadsbehandling.vilkår

sealed interface VilkårsfeilVedSøknadsbehandling {
    data object MåVurdereHelePerioden : VilkårsfeilVedSøknadsbehandling

    data object VurderingsperiodeUtenforBehandlingsperiode : VilkårsfeilVedSøknadsbehandling

    data object AlleVurderingsperioderMåHaSammeResultat : VilkårsfeilVedSøknadsbehandling
}
