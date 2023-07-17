package no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkår

import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.VilkårsfeilVedSøknadsbehandling
import no.nav.su.se.bakover.web.routes.revurdering.tilResultat

fun VilkårsfeilVedSøknadsbehandling.tilResultat(): Resultat {
    return when (this) {
        VilkårsfeilVedSøknadsbehandling.AlleVurderingsperioderMåHaSammeResultat -> Feilresponser.alleVurderingsperioderMåHaSammeResultat
        VilkårsfeilVedSøknadsbehandling.MåVurdereHelePerioden -> Feilresponser.måVurdereHelePerioden
        VilkårsfeilVedSøknadsbehandling.VurderingsperiodeUtenforBehandlingsperiode -> Feilresponser.vurderingsperioderKanIkkeVæreUtenforBehandlingsperiode
    }
}

fun SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.tilResultat(): Resultat {
    return when (this) {
        is SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
        is SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.UgyldigInput -> this.underliggende.tilResultat()
        is SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.Domenefeil -> this.underliggende.tilResultat()
        is SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår.UgyldigTilstand -> {
            Feilresponser.ugyldigTilstand(fra = fra, til = til)
        }
    }
}

fun KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår.Vilkårsfeil -> this.underliggende.tilResultat()
    }
}
