package no.nav.su.se.bakover.service.grunnlag

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.grunnlag.GrunnlagRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Uføregrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import java.time.Clock
import java.util.UUID

interface GrunnlagService {
    /** Denne brukes både fra Søknadsbehandling og Revurdering **/
    fun leggTilUføregrunnlag(behandlingId: UUID, uføregrunnlag: List<Uføregrunnlag>)
    fun opprettGrunnlag(sakId: UUID, periode: Periode): Grunnlagsdata
    fun simulerEndretGrunnlag(sakId: UUID, periode: Periode, endring: Grunnlagsdata): SimulertEndringGrunnlag

    sealed class KunneIkkeLeggeTilGrunnlagsdata {
        object FantIkkeBehandling : KunneIkkeLeggeTilGrunnlagsdata()
        object UgyldigTilstand : KunneIkkeLeggeTilGrunnlagsdata()
    }

    data class SimulertEndringGrunnlag(
        /** Sammensmelting av vedtakene før revurderingen. Det som lå til grunn for revurderingen */
        val førBehandling: Grunnlagsdata,
        /** De endringene som er lagt til i revurderingen (denne oppdateres ved lagring) */
        val endring: Grunnlagsdata,
        /** Sammensmeltinga av førBehandling og endring - denne er ikke persistert  */
        val resultat: Grunnlagsdata,
    )
}

internal class GrunnlagServiceImpl(
    private val grunnlagRepo: GrunnlagRepo,
    private val vedtakRepo: VedtakRepo,
    private val clock: Clock,
) : GrunnlagService {
    override fun leggTilUføregrunnlag(behandlingId: UUID, uføregrunnlag: List<Uføregrunnlag>) = grunnlagRepo.lagre(behandlingId, uføregrunnlag)

    override fun simulerEndretGrunnlag(sakId: UUID, periode: Periode, endring: Grunnlagsdata): GrunnlagService.SimulertEndringGrunnlag {
        // TODO jah: Vil ikke dette grunnlaget endre seg over tid for en revurdering, dersom andre revurderinger gjøres i mellomtiden?
        // Vil da ordet nåværendeGrunnlag være mer dekkende?
        val originaltGrunnlag = opprettGrunnlag(sakId, periode)

        val simulertEndringUføregrunnlag = Grunnlagsdata(
            uføregrunnlag = Tidslinje<Uføregrunnlag>(
                periode,
                originaltGrunnlag.uføregrunnlag + endring.uføregrunnlag,
                clock
            ).tidslinje
        )

        return GrunnlagService.SimulertEndringGrunnlag(
            førBehandling = originaltGrunnlag,
            endring = endring,
            resultat = simulertEndringUføregrunnlag
        )
    }

    override fun opprettGrunnlag(sakId: UUID, periode: Periode): Grunnlagsdata {
        return OpprettGrunnlagForRevurdering(
            sakId = sakId,
            periode = periode,
            vedtakRepo = vedtakRepo,
            clock = clock
        ).grunnlag
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
            .filterIsInstance<Vedtak.EndringIYtelse>() // TODO this must surely change at some point, needed to perserve information added by i.e revurdering below 10% or avslag.
            .filter { it.periode overlapper periode }

        val uføregrunnlagIPeriode = vedtakIPeriode
            .map { it.behandling.grunnlagsdata.copy() }
            .let { grunnlag ->
                Tidslinje<Uføregrunnlag>(
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
