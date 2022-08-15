package no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold

import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeTilInstitusjonsoppholdVilkår
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.Feilresponser.fantIkkeBehandling
import no.nav.su.se.bakover.web.routes.Feilresponser.heleBehandlingsperiodenMåHaVurderinger
import no.nav.su.se.bakover.web.routes.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.routes.Feilresponser.utenforBehandlingsperioden

internal fun KunneIkkeLeggeTilInstitusjonsoppholdVilkår.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLeggeTilInstitusjonsoppholdVilkår.FantIkkeBehandling -> fantIkkeBehandling
        is KunneIkkeLeggeTilInstitusjonsoppholdVilkår.Revurdering -> when (val f = this.feil) {
            Revurdering.KunneIkkeLeggeTilInstitusjonsoppholdVilkår.HeleBehandlingsperiodenErIkkeVurdert -> heleBehandlingsperiodenMåHaVurderinger
            is Revurdering.KunneIkkeLeggeTilInstitusjonsoppholdVilkår.UgyldigTilstand -> ugyldigTilstand(
                f.fra,
                f.til,
            )
        }
        is KunneIkkeLeggeTilInstitusjonsoppholdVilkår.Søknadsbehandling -> when (val f = this.feil) {
            is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilInstitusjonsoppholdVilkår.UgyldigTilstand -> ugyldigTilstand(
                f.fra,
                f.til,
            )
            KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilInstitusjonsoppholdVilkår.BehandlingsperiodeOgVurderingsperiodeMåVæreLik -> utenforBehandlingsperioden
        }
    }
}
