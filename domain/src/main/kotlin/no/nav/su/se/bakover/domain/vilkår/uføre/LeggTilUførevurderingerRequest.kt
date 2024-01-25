package no.nav.su.se.bakover.domain.vilkår.uføre

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.uføre.domain.UføreVilkår
import java.time.Clock
import java.util.UUID

data class LeggTilUførevurderingerRequest(
    /** Dekker både søknadsbehandlingId og revurderingId */
    val behandlingId: UUID,
    val vurderinger: Nel<LeggTilUførevilkårRequest>,
) {
    sealed interface UgyldigUførevurdering {
        data object UføregradOgForventetInntektMangler : UgyldigUførevurdering
        data object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigUførevurdering
        data object OverlappendeVurderingsperioder : UgyldigUførevurdering
        data object VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden : UgyldigUførevurdering
        data object AlleVurderingeneMåHaSammeResultat : UgyldigUførevurdering
        data object HeleBehandlingsperiodenMåHaVurderinger : UgyldigUførevurdering
    }

    fun toVilkår(
        behandlingsperiode: Periode,
        clock: Clock,
    ): Either<UgyldigUførevurdering, UføreVilkår.Vurdert> {
        return vurderinger.map { vurdering ->
            vurdering.toVurderingsperiode(clock).getOrElse {
                return when (it) {
                    LeggTilUførevilkårRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler -> {
                        UgyldigUførevurdering.UføregradOgForventetInntektMangler
                    }

                    LeggTilUførevilkårRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig -> {
                        UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig
                    }

                    LeggTilUførevilkårRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder -> {
                        UgyldigUførevurdering.OverlappendeVurderingsperioder
                    }

                    LeggTilUførevilkårRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> {
                        UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden
                    }
                }.left()
            }
        }.let { vurderingsperioder ->
            UføreVilkår.Vurdert.tryCreate(
                vurderingsperioder.toNonEmptyList(),

            )
                .mapLeft {
                    when (it) {
                        UføreVilkår.Vurdert.UgyldigUførevilkår.OverlappendeVurderingsperioder -> {
                            UgyldigUførevurdering.OverlappendeVurderingsperioder
                        }
                    }
                }.map {
                    // Denne sjekken vil og fange opp: VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden, derfor kjører vi den etterpå.
                    if (!(behandlingsperiode fullstendigOverlapp vurderinger.map { it.periode })) {
                        return UgyldigUførevurdering.HeleBehandlingsperiodenMåHaVurderinger.left()
                    }
                    it
                }
        }.also {
            if (vurderinger.any { it.oppfylt != vurderinger.first().oppfylt }) {
                return UgyldigUførevurdering.AlleVurderingeneMåHaSammeResultat.left()
            }
        }
    }
}
