package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.grunnlag.GrunnlagRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Uføregrunnlag
import no.nav.su.se.bakover.domain.grunnlag.UføregrunnlagTidslinje
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.GrunnlagsdataService.KunneIkkeLeggeTilGrunnlagsdata
import java.time.Clock
import java.util.UUID

interface GrunnlagsdataService {
    /** Denne brukes både fra Søknadsbehandling og Revurdering **/
    fun leggTilUføregrunnlag(
        behandlingId: UUID,
        uføregrunnlag: List<Uføregrunnlag>
    ): Either<KunneIkkeLeggeTilGrunnlagsdata, Behandling>

    sealed class KunneIkkeLeggeTilGrunnlagsdata {
        object FantIkkeBehandling : KunneIkkeLeggeTilGrunnlagsdata()
        object UgyldigTilstand : KunneIkkeLeggeTilGrunnlagsdata()
    }
}

internal class GrunnlagsdataServiceImpl(
    private val behandlingService: BehandlingService,
    private val grunnlagRepo: GrunnlagRepo,
) : GrunnlagsdataService {
    override fun leggTilUføregrunnlag(
        behandlingId: UUID,
        uføregrunnlag: List<Uføregrunnlag>
    ): Either<KunneIkkeLeggeTilGrunnlagsdata, Behandling> {
        // TODO: Make frontend's main path call this endpoint instead of the patch one.
        val behandling: Behandling = behandlingService.hentBehandling(behandlingId).getOrHandle {
            return KunneIkkeLeggeTilGrunnlagsdata.FantIkkeBehandling.left()
        }
        when (behandling) {
            is RevurderingTilAttestering,
            is IverksattRevurdering,
            is Søknadsbehandling.TilAttestering,
            is Søknadsbehandling.Iverksatt -> return KunneIkkeLeggeTilGrunnlagsdata.UgyldigTilstand.left()
            // TODO jah jm: Fullfør denne lista eller gjør det på en annen måte.
        }

        grunnlagRepo.lagre(behandlingId, uføregrunnlag)
        return behandlingService.hentBehandling(behandlingId).orNull()!!.right()
    }
}

internal class OpprettGrunnlagForRevurdering(
    private val sakId: UUID,
    private val periode: Periode,
    private val vedtakRepo: VedtakRepo,
    private val clock: Clock
) {
    val grunnlag = opprettGrunnlag()

    private fun opprettGrunnlag(): Grunnlagsdata {
        val vedtakIPeriode = vedtakRepo.hentForSakId(sakId)
            .filterIsInstance<Vedtak.InnvilgetStønad>() // TODO this must surely change at some point, needed to perserve information added by i.e revurdering below 10% or avslag.
            .filter { it.periode overlapper periode }

        val uføregrunnlagIPeriode = vedtakIPeriode
            .map { it.behandling.grunnlagsdata.copy() }
            .let { grunnlag ->
                UføregrunnlagTidslinje(
                    periode = periode,
                    objekter = grunnlag.flatMap { it.uføregrunnlag },
                    clock = clock
                )
            }.tidslinje

        return Grunnlagsdata(
            uføregrunnlag = uføregrunnlagIPeriode
        )
    }
}
