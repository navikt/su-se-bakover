package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.vilkår.OppholdIUtlandetVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.service.vilkår.LeggTilOppholdIUtlandetRequest

internal data class OppholdIUtlandetVilkårJson(
    val vilkår: String,
    val status: LeggTilOppholdIUtlandetRequest.Status,
    val begrunnelse: String?,
)

internal fun OppholdIUtlandetVilkår.toJson(): OppholdIUtlandetVilkårJson {
    return OppholdIUtlandetVilkårJson(
        vilkår = vilkår.toJson(),
        status = when (this) {
            is OppholdIUtlandetVilkår.IkkeVurdert -> LeggTilOppholdIUtlandetRequest.Status.Uavklart
            is OppholdIUtlandetVilkår.Vurdert -> when (this.vurderingsperioder.first().resultat) {
                Resultat.Avslag -> LeggTilOppholdIUtlandetRequest.Status.SkalVæreMerEnn90DagerIUtlandet
                Resultat.Innvilget -> LeggTilOppholdIUtlandetRequest.Status.SkalHoldeSegINorge
                Resultat.Uavklart -> LeggTilOppholdIUtlandetRequest.Status.Uavklart
            }
        },
        begrunnelse = when (this) {
            is OppholdIUtlandetVilkår.Vurdert -> this.vurderingsperioder.first().begrunnelse
            is OppholdIUtlandetVilkår.IkkeVurdert -> null
        }
    )
}
