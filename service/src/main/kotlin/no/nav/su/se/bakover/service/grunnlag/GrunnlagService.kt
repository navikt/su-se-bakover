package no.nav.su.se.bakover.service.grunnlag

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.grunnlag.GrunnlagRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Uføregrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import java.time.Clock
import java.util.UUID

interface GrunnlagService {
    fun opprettGrunnlagsdata(sakId: UUID, periode: Periode): Grunnlagsdata

    fun lagreFradragsgrunnlag(behandlingId: UUID, fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>)
    fun hentFradragsgrunnlag(behandlingId: UUID): List<Grunnlag.Fradragsgrunnlag>

    sealed class KunneIkkeLeggeTilGrunnlagsdata {
        object FantIkkeBehandling : KunneIkkeLeggeTilGrunnlagsdata()
        object UgyldigTilstand : KunneIkkeLeggeTilGrunnlagsdata()
    }
}

internal class GrunnlagServiceImpl(
    private val vedtakRepo: VedtakRepo,
    private val grunnlagRepo: GrunnlagRepo,
    private val clock: Clock,
) : GrunnlagService {
    override fun opprettGrunnlagsdata(sakId: UUID, periode: Periode): Grunnlagsdata {
        return OpprettGrunnlagsdataForPeriode(
            sakId = sakId,
            periode = periode,
            vedtakRepo = vedtakRepo,
            clock = clock,
        ).grunnlag
    }

    override fun lagreFradragsgrunnlag(behandlingId: UUID, fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) {
        return grunnlagRepo.lagreFradragsgrunnlag(behandlingId, fradragsgrunnlag)
    }

    override fun hentFradragsgrunnlag(behandlingId: UUID): List<Grunnlag.Fradragsgrunnlag> {
        return grunnlagRepo.hentFradragsgrunnlag(behandlingId)
    }
}

internal class OpprettGrunnlagsdataForPeriode(
    private val sakId: UUID,
    private val periode: Periode,
    private val vedtakRepo: VedtakRepo,
    private val clock: Clock,
) {
    val grunnlag by lazy {
        val vedtakIPeriode = vedtakRepo.hentForSakId(sakId)
            .filterIsInstance<Vedtak.EndringIYtelse>() // TODO jacob: this must surely change at some point, needed to perserve information added by i.e revurdering below 10% or avslag.
            .filter { it.periode overlapper periode }

        val uføregrunnlagIPeriode = vedtakIPeriode
            .map { it.behandling.grunnlagsdata.copy() }
            .let { grunnlag ->
                Tidslinje<Uføregrunnlag>(
                    periode = periode,
                    objekter = grunnlag.flatMap { it.uføregrunnlag },
                    clock = clock,
                )
            }.tidslinje

        Grunnlagsdata(uføregrunnlag = uføregrunnlagIPeriode)
    }
}
