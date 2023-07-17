package no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold

import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.fantIkkeBehandling
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.KunneIkkeLeggeTilInstitusjonsoppholdVilkår

internal fun KunneIkkeLeggeTilInstitusjonsoppholdVilkår.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLeggeTilInstitusjonsoppholdVilkår.FantIkkeBehandling -> fantIkkeBehandling
        is KunneIkkeLeggeTilInstitusjonsoppholdVilkår.Revurdering -> TODO()
        is KunneIkkeLeggeTilInstitusjonsoppholdVilkår.Søknadsbehandling -> TODO()
    }
}
