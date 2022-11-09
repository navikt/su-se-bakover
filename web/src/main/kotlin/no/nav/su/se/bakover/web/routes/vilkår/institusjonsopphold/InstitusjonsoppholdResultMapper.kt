package no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold

import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeBehandling
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.heleBehandlingsperiodenMåHaVurderinger
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.utenforBehandlingsperioden
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.KunneIkkeLeggeTilInstitusjonsoppholdVilkår

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
