package no.nav.su.se.bakover.service.søknadsbehandling

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.grunnlag.GrunnlagRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.Uføregrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.UføregrunnlagTidslinje
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import java.time.Clock
import java.util.UUID

interface GrunnlagsdataService {
    fun leggTilUførerunnlag(sakId: UUID, behandlingId: UUID, uføregrunnlag: List<Uføregrunnlag>)
}

internal class GrunnlagsdataServiceImpl(
    private val grunnlagRepo: GrunnlagRepo
) : GrunnlagsdataService {
    override fun leggTilUførerunnlag(sakId: UUID, behandlingId: UUID, uføregrunnlag: List<Uføregrunnlag>) {
        // TODO: Accept a list. Return an Either. insert into table(s). Call søknadbehandlingsservice. Make frontend call this endpoint instead of the patch one.
        grunnlagRepo.lagre(behandlingId, uføregrunnlag)
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
