package no.nav.su.se.bakover.web.routes.vilkår

import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.FamiliegjenforeningVurderinger
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.FamiliegjenforeningvilkårStatus
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.web.routes.vilkår.VurderingsperiodeFamiliegjenforeningJson.Companion.toJson
import vilkår.common.domain.Vurdering
import vilkår.familiegjenforening.domain.FamiliegjenforeningVilkår
import vilkår.familiegjenforening.domain.VurderingsperiodeFamiliegjenforening

data class FamiliegjenforeningVilkårJson(
    val vurderinger: List<VurderingsperiodeFamiliegjenforeningJson>,
) {

    fun toLeggTilFamiliegjenforeningRequest(behandlingId: BehandlingsId) =
        LeggTilFamiliegjenforeningRequest(
            behandlingId = behandlingId,
            vurderinger = vurderinger.map {
                FamiliegjenforeningVurderinger(
                    periode = it.periode.toPeriode(),
                    status = it.status,
                )
            },
        )

    companion object {
        fun FamiliegjenforeningVilkår.toJson() = when (this) {
            FamiliegjenforeningVilkår.IkkeVurdert -> null
            is FamiliegjenforeningVilkår.Vurdert -> FamiliegjenforeningVilkårJson(
                vurderinger = vurderingsperioder.map { it.toJson() },
            )
        }
    }
}

data class VurderingsperiodeFamiliegjenforeningJson(
    val periode: PeriodeJson,
    val status: FamiliegjenforeningvilkårStatus,
) {
    companion object {
        fun VurderingsperiodeFamiliegjenforening.toJson() = VurderingsperiodeFamiliegjenforeningJson(
            periode = this.periode.toJson(),
            status = vurdering.tilFamiliegjenforeningVilkårStatus(),
        )
    }
}

private fun Vurdering.tilFamiliegjenforeningVilkårStatus() = when (this) {
    Vurdering.Avslag -> FamiliegjenforeningvilkårStatus.VilkårIkkeOppfylt
    Vurdering.Innvilget -> FamiliegjenforeningvilkårStatus.VilkårOppfylt
    Vurdering.Uavklart -> FamiliegjenforeningvilkårStatus.Uavklart
}
