package no.nav.su.se.bakover.domain.vilkår.uføre

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUføre
import java.time.Clock
import java.util.UUID

enum class UførevilkårStatus {
    VilkårOppfylt,
    VilkårIkkeOppfylt,
    HarUføresakTilBehandling,
}

data class LeggTilUførevilkårRequest(
    val behandlingId: UUID,
    val periode: Periode,
    val uføregrad: Uføregrad?,
    val forventetInntekt: Int?,
    val oppfylt: UførevilkårStatus,
    val begrunnelse: String?,
) {
    sealed class UgyldigUførevurdering {
        object UføregradOgForventetInntektMangler : UgyldigUførevurdering()
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigUførevurdering()
        object OverlappendeVurderingsperioder : UgyldigUførevurdering()
        object VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden : UgyldigUførevurdering()
    }

    /**
     * @param behandlingsperiode Ved en søknadsbehandling kan det være støndadsperiode. Ved en revurdering kan det være revurderingsperioden.
     */
    fun toVurderingsperiode(
        clock: Clock,
    ): Either<UgyldigUførevurdering, VurderingsperiodeUføre> {
        return when (oppfylt) {
            UførevilkårStatus.VilkårOppfylt -> {
                if (uføregrad == null || forventetInntekt == null) return UgyldigUførevurdering.UføregradOgForventetInntektMangler.left()
                VurderingsperiodeUføre.tryCreate(
                    vurdering = Vurdering.Innvilget,
                    grunnlag = Grunnlag.Uføregrunnlag(
                        periode = periode,
                        uføregrad = uføregrad,
                        forventetInntekt = forventetInntekt,
                        opprettet = Tidspunkt.now(clock),
                    ),
                    vurderingsperiode = periode,
                    opprettet = Tidspunkt.now(clock),
                )
            }
            UførevilkårStatus.VilkårIkkeOppfylt -> VurderingsperiodeUføre.tryCreate(
                vurdering = Vurdering.Avslag,
                grunnlag = null,
                vurderingsperiode = periode,
                opprettet = Tidspunkt.now(clock),
            )
            UførevilkårStatus.HarUføresakTilBehandling -> VurderingsperiodeUføre.tryCreate(
                vurdering = Vurdering.Uavklart,
                grunnlag = null,
                vurderingsperiode = periode,
                opprettet = Tidspunkt.now(clock),
            )
        }.mapLeft {
            when (it) {
                VurderingsperiodeUføre.UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig
            }
        }
    }

    fun toVilkår(clock: Clock): Either<UgyldigUførevurdering, UføreVilkår.Vurdert> {
        return toVurderingsperiode(clock)
            .flatMap { vurderingsperiode ->
                UføreVilkår.Vurdert.tryCreate(nonEmptyListOf(vurderingsperiode))
                    .mapLeft {
                        when (it) {
                            UføreVilkår.Vurdert.UgyldigUførevilkår.OverlappendeVurderingsperioder -> UgyldigUførevurdering.OverlappendeVurderingsperioder
                        }
                    }
            }
    }
}
