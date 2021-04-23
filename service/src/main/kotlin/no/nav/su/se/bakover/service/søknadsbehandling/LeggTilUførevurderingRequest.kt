package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import java.util.UUID

data class LeggTilUførevurderingRequest(
    val behandlingId: UUID,
    val periode: Periode,
    val uføregrad: Uføregrad?,
    val forventetInntekt: Int?,
    val oppfylt: SøknadsbehandlingService.Oppfylt,
    val begrunnelse: String,
) {
    object UføregradOgForventetInntektMangler

    fun toVilkår(): Either<UføregradOgForventetInntektMangler, Vilkår<Grunnlag.Uføregrunnlag>> {
        return when (oppfylt) {
            SøknadsbehandlingService.Oppfylt.JA -> Vilkår.Vurdert.Uførhet(
                vurdering = listOf(
                    Vurderingsperiode.Manuell(
                        resultat = Resultat.Innvilget,
                        grunnlag = if (uføregrad == null || forventetInntekt == null) return UføregradOgForventetInntektMangler.left() else {
                            Grunnlag.Uføregrunnlag(
                                periode = periode,
                                uføregrad = uføregrad,
                                forventetInntekt = forventetInntekt,
                            )
                        },
                        periode = periode,
                        begrunnelse = begrunnelse,
                    ),
                ),
            )
            SøknadsbehandlingService.Oppfylt.NEI -> Vilkår.Vurdert.Uførhet(
                vurdering = listOf(
                    Vurderingsperiode.Manuell(
                        resultat = Resultat.Avslag,
                        grunnlag = if (uføregrad == null || forventetInntekt == null) null else {
                            Grunnlag.Uføregrunnlag(
                                periode = periode,
                                uføregrad = uføregrad,
                                forventetInntekt = forventetInntekt,
                            )
                        },
                        periode = periode,
                        begrunnelse = begrunnelse,
                    ),
                ),
            )
            SøknadsbehandlingService.Oppfylt.UAVKLART -> Vilkår.IkkeVurdertUføregrunnlag
        }.right()
    }
}
