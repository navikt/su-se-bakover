package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest

internal data class UtenlandsoppholdVilkårJsonTest(
    val status: LeggTilUtenlandsoppholdRequest.Status,
    val begrunnelse: String?,
)

internal fun UtenlandsoppholdVilkår.toJson(): UtenlandsoppholdVilkårJsonTest {
    return UtenlandsoppholdVilkårJsonTest(
        status = when (this) {
            is UtenlandsoppholdVilkår.IkkeVurdert -> LeggTilUtenlandsoppholdRequest.Status.Uavklart
            is UtenlandsoppholdVilkår.Vurdert -> when (this.vurderingsperioder.first().resultat) {
                Resultat.Avslag -> LeggTilUtenlandsoppholdRequest.Status.SkalVæreMerEnn90DagerIUtlandet
                Resultat.Innvilget -> LeggTilUtenlandsoppholdRequest.Status.SkalHoldeSegINorge
                Resultat.Uavklart -> LeggTilUtenlandsoppholdRequest.Status.Uavklart
            }
        },
        begrunnelse = when (this) {
            is UtenlandsoppholdVilkår.Vurdert -> this.vurderingsperioder.first().begrunnelse
            is UtenlandsoppholdVilkår.IkkeVurdert -> null
        }
    )
}
