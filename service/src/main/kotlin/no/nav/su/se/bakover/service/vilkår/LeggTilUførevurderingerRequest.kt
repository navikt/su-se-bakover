package no.nav.su.se.bakover.service.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import java.util.UUID

data class LeggTilUførevurderingerRequest(
    /** Dekker både søknadsbehandlingId og revurderingId */
    val behandlingId: UUID,
    val vurderinger: Nel<LeggTilUførevurderingRequest>,
) {
    sealed class UgyldigUførevurdering {
        object UføregradOgForventetInntektMangler : UgyldigUførevurdering()
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigUførevurdering()
        object OverlappendeVurderingsperioder : UgyldigUførevurdering()
        object VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden : UgyldigUførevurdering()
        object AlleVurderingeneMåHaSammeResultat : UgyldigUførevurdering()
        object HeleBehandlingsperiodenMåHaVurderinger : UgyldigUførevurdering()
    }

    fun toVilkår(behandlingsperiode: Periode): Either<UgyldigUførevurdering, Vilkår.Uførhet.Vurdert> {
        return vurderinger.map { vurdering ->
            vurdering.toVurderingsperiode(behandlingsperiode).getOrHandle {
                return when (it) {
                    LeggTilUførevurderingRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler -> UgyldigUførevurdering.UføregradOgForventetInntektMangler.left()
                    LeggTilUførevurderingRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig -> UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                    LeggTilUførevurderingRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder -> UgyldigUførevurdering.OverlappendeVurderingsperioder.left()
                    LeggTilUførevurderingRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden.left()
                }
            }
        }.let { vurderingsperioder ->
            Vilkår.Uførhet.Vurdert.tryCreate(Nel.fromListUnsafe(vurderingsperioder))
                .mapLeft {
                    when (it) {
                        Vilkår.Uførhet.Vurdert.UgyldigUførevilkår.OverlappendeVurderingsperioder -> UgyldigUførevurdering.OverlappendeVurderingsperioder
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
