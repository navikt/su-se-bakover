package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
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
    val oppfylt: Behandlingsinformasjon.Uførhet.Status,
    val begrunnelse: String?,
) {
    sealed class UgyldigUførevurdering {
        object UføregradOgForventetInntektMangler : UgyldigUførevurdering()
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigUførevurdering()
        object OverlappendeVurderingsperioder : UgyldigUførevurdering()
        object VurderingsperiodeMangler : UgyldigUførevurdering()
    }

    fun toVurderingsperiode(): Either<UgyldigUførevurdering, Vurderingsperiode<Grunnlag.Uføregrunnlag>> {
        return when (oppfylt) {
            Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt -> Vurderingsperiode.Manuell.tryCreate(
                resultat = Resultat.Innvilget,
                grunnlag = if (uføregrad == null || forventetInntekt == null) return UgyldigUførevurdering.UføregradOgForventetInntektMangler.left() else {
                    Grunnlag.Uføregrunnlag(
                        periode = periode,
                        uføregrad = uføregrad,
                        forventetInntekt = forventetInntekt,
                    )
                },
                periode = periode,
                begrunnelse = begrunnelse,
            )
            Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt -> Vurderingsperiode.Manuell.tryCreate(
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
            )
            Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling -> Vurderingsperiode.Manuell.tryCreate(
                resultat = Resultat.Uavklart,
                grunnlag = if (uføregrad == null || forventetInntekt == null) null else {
                    Grunnlag.Uføregrunnlag(
                        periode = periode,
                        uføregrad = uføregrad,
                        forventetInntekt = forventetInntekt,
                    )
                },
                periode = periode,
                begrunnelse = begrunnelse,
            )
        }.mapLeft {
            when (it) {
                Vurderingsperiode.Manuell.UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig
            }
        }
    }

    fun toVilkår(): Either<UgyldigUførevurdering, Vilkår.Vurdert.Uførhet> {
        return toVurderingsperiode()
            .flatMap { vurderingsperiode ->
                Vilkår.Vurdert.Uførhet.tryCreate(listOf(vurderingsperiode))
                    .mapLeft {
                        when (it) {
                            Vilkår.Vurdert.Uførhet.UgyldigUførevilkår.OverlappendeVurderingsperioder -> UgyldigUførevurdering.OverlappendeVurderingsperioder
                            Vilkår.Vurdert.Uførhet.UgyldigUførevilkår.VurderingsperioderMangler -> UgyldigUførevurdering.VurderingsperiodeMangler
                        }
                    }
            }
    }
}
