package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.vilkår.OppholdIUtlandetVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat

internal data class OppholdIUtlandetVilkårJson(
    val vilkår: String,
    val status: Behandlingsinformasjon.OppholdIUtlandet.Status,
    val begrunnelse: String?,
)

internal fun OppholdIUtlandetVilkår.toJson(): OppholdIUtlandetVilkårJson {
    return OppholdIUtlandetVilkårJson(
        vilkår = vilkår.toJson(),
        status = when (this) {
            is OppholdIUtlandetVilkår.IkkeVurdert -> Behandlingsinformasjon.OppholdIUtlandet.Status.Uavklart
            is OppholdIUtlandetVilkår.Vurdert -> when (this.vurderingsperioder.first().resultat) {
                Resultat.Avslag -> Behandlingsinformasjon.OppholdIUtlandet.Status.SkalVæreMerEnn90DagerIUtlandet
                Resultat.Innvilget -> Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge
                Resultat.Uavklart -> Behandlingsinformasjon.OppholdIUtlandet.Status.Uavklart
            }
        },
        begrunnelse = when (this) {
            is OppholdIUtlandetVilkår.Vurdert -> this.vurderingsperioder.first().begrunnelse
            is OppholdIUtlandetVilkår.IkkeVurdert -> null
        }
    )
}
