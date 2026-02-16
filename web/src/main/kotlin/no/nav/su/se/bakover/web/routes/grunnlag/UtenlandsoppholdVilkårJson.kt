package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.UtenlandsoppholdStatus
import vilkår.common.domain.Vurdering
import vilkår.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import vilkår.utenlandsopphold.domain.vilkår.VurderingsperiodeUtenlandsopphold

data class UtenlandsoppholdVilkårJson(
    val vurderinger: List<VurderingsperiodeUtenlandsoppholdJson>,
    val status: UtenlandsoppholdStatus,
)

fun UtenlandsoppholdVilkår.toJson(): UtenlandsoppholdVilkårJson? {
    return when (this) {
        UtenlandsoppholdVilkår.IkkeVurdert -> null
        is UtenlandsoppholdVilkår.Vurdert -> this.toJson()
    }
}

fun UtenlandsoppholdVilkår.Vurdert.toJson(): UtenlandsoppholdVilkårJson {
    return UtenlandsoppholdVilkårJson(
        vurderinger = vurderingsperioder.map { it.toJson() },
        status = vurdering.tilUtenlandsoppholdStatus(),
    )
}

fun VurderingsperiodeUtenlandsopphold.toJson(): VurderingsperiodeUtenlandsoppholdJson {
    return VurderingsperiodeUtenlandsoppholdJson(
        status = vurdering.tilUtenlandsoppholdStatus(),
        periode = periode.toJson(),
    )
}

fun Vurdering.tilUtenlandsoppholdStatus(): UtenlandsoppholdStatus {
    return when (this) {
        Vurdering.Avslag -> UtenlandsoppholdStatus.SkalVæreMerEnn90DagerIUtlandet
        Vurdering.Innvilget -> UtenlandsoppholdStatus.SkalHoldeSegINorge
        Vurdering.Uavklart -> UtenlandsoppholdStatus.Uavklart
    }
}

data class VurderingsperiodeUtenlandsoppholdJson(
    val status: UtenlandsoppholdStatus,
    val periode: PeriodeJson,
)
