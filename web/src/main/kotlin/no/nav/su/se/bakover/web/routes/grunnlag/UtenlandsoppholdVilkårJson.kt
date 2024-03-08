package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.UtenlandsoppholdStatus
import vilkår.common.domain.Vurdering
import vilkår.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import vilkår.utenlandsopphold.domain.vilkår.VurderingsperiodeUtenlandsopphold

internal data class UtenlandsoppholdVilkårJson(
    val vurderinger: List<VurderingsperiodeUtenlandsoppholdJson>,
    val status: UtenlandsoppholdStatus,
)

internal fun UtenlandsoppholdVilkår.toJson(): UtenlandsoppholdVilkårJson? {
    return when (this) {
        UtenlandsoppholdVilkår.IkkeVurdert -> null
        is UtenlandsoppholdVilkår.Vurdert -> this.toJson()
    }
}

internal fun UtenlandsoppholdVilkår.Vurdert.toJson(): UtenlandsoppholdVilkårJson {
    return UtenlandsoppholdVilkårJson(
        vurderinger = vurderingsperioder.map { it.toJson() },
        status = vurdering.tilUtenlandsoppholdStatus(),
    )
}

internal fun VurderingsperiodeUtenlandsopphold.toJson(): VurderingsperiodeUtenlandsoppholdJson {
    return VurderingsperiodeUtenlandsoppholdJson(
        status = vurdering.tilUtenlandsoppholdStatus(),
        periode = periode.toJson(),
    )
}

internal fun Vurdering.tilUtenlandsoppholdStatus(): UtenlandsoppholdStatus {
    return when (this) {
        Vurdering.Avslag -> UtenlandsoppholdStatus.SkalVæreMerEnn90DagerIUtlandet
        Vurdering.Innvilget -> UtenlandsoppholdStatus.SkalHoldeSegINorge
        Vurdering.Uavklart -> UtenlandsoppholdStatus.Uavklart
    }
}

internal data class VurderingsperiodeUtenlandsoppholdJson(
    val status: UtenlandsoppholdStatus,
    val periode: PeriodeJson,
)
