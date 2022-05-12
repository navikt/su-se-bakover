package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import java.time.Clock
import java.util.UUID

enum class UførevilkårStatus {
    VilkårOppfylt,
    VilkårIkkeOppfylt,
    HarUføresakTilBehandling
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
    ): Either<UgyldigUførevurdering, Vurderingsperiode.Uføre> {
        return when (oppfylt) {
            UførevilkårStatus.VilkårOppfylt -> {
                if (uføregrad == null || forventetInntekt == null) return UgyldigUførevurdering.UføregradOgForventetInntektMangler.left()
                Vurderingsperiode.Uføre.tryCreate(
                    resultat = Resultat.Innvilget,
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
            UførevilkårStatus.VilkårIkkeOppfylt -> Vurderingsperiode.Uføre.tryCreate(
                resultat = Resultat.Avslag,
                grunnlag = null,
                vurderingsperiode = periode,
                opprettet = Tidspunkt.now(clock),
            )
            UførevilkårStatus.HarUføresakTilBehandling -> Vurderingsperiode.Uføre.tryCreate(
                resultat = Resultat.Uavklart,
                grunnlag = null,
                vurderingsperiode = periode,
                opprettet = Tidspunkt.now(clock),
            )
        }.mapLeft {
            when (it) {
                Vurderingsperiode.Uføre.UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig
            }
        }
    }

    fun toVilkår(clock: Clock): Either<UgyldigUførevurdering, Vilkår.Uførhet.Vurdert> {
        return toVurderingsperiode(clock)
            .flatMap { vurderingsperiode ->
                Vilkår.Uførhet.Vurdert.tryCreate(nonEmptyListOf(vurderingsperiode))
                    .mapLeft {
                        when (it) {
                            Vilkår.Uførhet.Vurdert.UgyldigUførevilkår.OverlappendeVurderingsperioder -> UgyldigUførevurdering.OverlappendeVurderingsperioder
                        }
                    }
            }
    }
}
