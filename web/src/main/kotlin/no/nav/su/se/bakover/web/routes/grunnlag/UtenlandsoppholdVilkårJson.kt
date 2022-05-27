package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUtenlandsopphold
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus

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
        status = resultat.tilUtenlandsoppholdStatus(),
    )
}

internal fun VurderingsperiodeUtenlandsopphold.toJson(): VurderingsperiodeUtenlandsoppholdJson {
    return VurderingsperiodeUtenlandsoppholdJson(
        status = resultat.tilUtenlandsoppholdStatus(),
        periode = periode.toJson(),
    )
}

internal fun Resultat.tilUtenlandsoppholdStatus(): UtenlandsoppholdStatus {
    return when (this) {
        Resultat.Avslag -> UtenlandsoppholdStatus.SkalVæreMerEnn90DagerIUtlandet
        Resultat.Innvilget -> UtenlandsoppholdStatus.SkalHoldeSegINorge
        Resultat.Uavklart -> UtenlandsoppholdStatus.Uavklart
    }
}

internal data class VurderingsperiodeUtenlandsoppholdJson(
    val status: UtenlandsoppholdStatus,
    val periode: PeriodeJson,
)
