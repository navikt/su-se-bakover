package no.nav.su.se.bakover.web.routes.vilkår.lovligopphold

import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeLovligOpphold
import no.nav.su.se.bakover.service.vilkår.LovligOppholdVilkårStatus
import no.nav.su.se.bakover.web.routes.vilkår.lovligopphold.VurderingsperiodeLovligOppholdJson.Companion.toJson

data class LovligOppholdVilkårJson(
    val vurderinger: List<VurderingsperiodeLovligOppholdJson>,
    val resultat: LovligOppholdVilkårStatus,
) {
    companion object {
        fun LovligOppholdVilkår.toJson() = when (this) {
            LovligOppholdVilkår.IkkeVurdert -> null
            is LovligOppholdVilkår.Vurdert -> LovligOppholdVilkårJson(
                this.vurderingsperioder.map { it.toJson() },
                resultat.tilLovligOppholdVilkårstatus(),
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
            resultat = resultat.tilLovligOppholdVilkårstatus(),
        )
    }
}

private fun Resultat.tilLovligOppholdVilkårstatus() = when (this) {
    Resultat.Avslag -> LovligOppholdVilkårStatus.VilkårIkkeOppfylt
    Resultat.Innvilget -> LovligOppholdVilkårStatus.VilkårOppfylt
    Resultat.Uavklart -> LovligOppholdVilkårStatus.Uavklart
}
