package no.nav.su.se.bakover.web.routes.vilkår

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vilkår.FamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFamiliegjenforening
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.FamiliegjenforeningvilkårStatus
import no.nav.su.se.bakover.web.routes.vilkår.VurderingsperiodeFamiliegjenforeningJson.Companion.toJson

data class FamiliegjenforeningVilkårJson(
    val vurderinger: List<VurderingsperiodeFamiliegjenforeningJson>,
    val resultat: FamiliegjenforeningvilkårStatus,
) {
    companion object {
        fun FamiliegjenforeningVilkår.toJson() = when (this) {
            FamiliegjenforeningVilkår.IkkeVurdert -> null
            is FamiliegjenforeningVilkår.Vurdert -> FamiliegjenforeningVilkårJson(vurderinger = vurderingsperioder.map { it.toJson() }, this.vurdering.tilFamiliegjenforeningVilkårStatus())
        }
    }
}

data class VurderingsperiodeFamiliegjenforeningJson(
    val periode: PeriodeJson,
    val resultat: FamiliegjenforeningvilkårStatus,
) {
    companion object {
        fun VurderingsperiodeFamiliegjenforening.toJson() = VurderingsperiodeFamiliegjenforeningJson(
            periode = this.periode.toJson(),
            resultat = vurdering.tilFamiliegjenforeningVilkårStatus(),
        )
    }
}

private fun Vurdering.tilFamiliegjenforeningVilkårStatus() = when (this) {
    Vurdering.Avslag -> FamiliegjenforeningvilkårStatus.VilkårIkkeOppfylt
    Vurdering.Innvilget -> FamiliegjenforeningvilkårStatus.VilkårOppfylt
    Vurdering.Uavklart -> FamiliegjenforeningvilkårStatus.Uavklart
}
