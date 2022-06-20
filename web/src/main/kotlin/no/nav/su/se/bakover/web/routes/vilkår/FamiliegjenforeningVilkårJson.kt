package no.nav.su.se.bakover.web.routes.vilkår

import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vilkår.FamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFamiliegjenforening
import no.nav.su.se.bakover.service.vilkår.FamiliegjenforeningvilkårStatus
import no.nav.su.se.bakover.web.routes.vilkår.VurderingsperiodeFamiliegjenforeningJson.Companion.toJson

data class FamiliegjenforeningVilkårJson(
    val vurderinger: List<VurderingsperiodeFamiliegjenforeningJson>,
    val resultat: FamiliegjenforeningvilkårStatus
) {
    companion object {
        fun FamiliegjenforeningVilkår.toJson() = when (this) {
            FamiliegjenforeningVilkår.IkkeVurdert -> null
            is FamiliegjenforeningVilkår.Vurdert -> FamiliegjenforeningVilkårJson(vurderinger = vurderingsperioder.map { it.toJson() }, this.resultat.tilFamiliegjenforeningVilkårStatus())
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
            resultat = resultat.tilFamiliegjenforeningVilkårStatus(),
        )
    }
}

private fun Resultat.tilFamiliegjenforeningVilkårStatus() = when (this) {
    Resultat.Avslag -> FamiliegjenforeningvilkårStatus.VilkårIkkeOppfylt
    Resultat.Innvilget -> FamiliegjenforeningvilkårStatus.VilkårOppfylt
    Resultat.Uavklart -> FamiliegjenforeningvilkårStatus.Uavklart
}
