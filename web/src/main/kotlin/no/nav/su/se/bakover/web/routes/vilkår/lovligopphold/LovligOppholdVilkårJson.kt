package no.nav.su.se.bakover.web.routes.vilkår.lovligopphold

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LovligOppholdVilkårStatus
import no.nav.su.se.bakover.web.routes.vilkår.lovligopphold.VurderingsperiodeLovligOppholdJson.Companion.toJson
import vilkår.domain.Vurdering
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.lovligopphold.domain.VurderingsperiodeLovligOpphold

data class LovligOppholdVilkårJson(
    val vurderinger: List<VurderingsperiodeLovligOppholdJson>,
    val resultat: LovligOppholdVilkårStatus,
) {
    companion object {
        fun LovligOppholdVilkår.toJson() = when (this) {
            LovligOppholdVilkår.IkkeVurdert -> null
            is LovligOppholdVilkår.Vurdert -> LovligOppholdVilkårJson(
                this.vurderingsperioder.map { it.toJson() },
                vurdering.tilLovligOppholdVilkårstatus(),
            )
        }
    }
}

data class VurderingsperiodeLovligOppholdJson(
    val periode: PeriodeJson,
    val resultat: LovligOppholdVilkårStatus,
) {
    companion object {
        fun VurderingsperiodeLovligOpphold.toJson() = VurderingsperiodeLovligOppholdJson(
            periode = this.periode.toJson(),
            resultat = vurdering.tilLovligOppholdVilkårstatus(),
        )
    }
}

private fun Vurdering.tilLovligOppholdVilkårstatus() = when (this) {
    Vurdering.Avslag -> LovligOppholdVilkårStatus.VilkårIkkeOppfylt
    Vurdering.Innvilget -> LovligOppholdVilkårStatus.VilkårOppfylt
    Vurdering.Uavklart -> LovligOppholdVilkårStatus.Uavklart
}
