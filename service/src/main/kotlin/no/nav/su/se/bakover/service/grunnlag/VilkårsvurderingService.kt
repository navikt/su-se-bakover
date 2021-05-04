package no.nav.su.se.bakover.service.grunnlag

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.grunnlag.VilkårsvurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
import java.util.UUID

interface VilkårsvurderingService {
    fun lagre(behandlingId: UUID, vilkårsvurderinger: Vilkårsvurderinger)
    fun opprettVilkårsvurderinger(sakId: UUID, periode: Periode): Vilkårsvurderinger
}

internal class VilkårsvurderingServiceImpl(
    private val vilkårsvurderingRepo: VilkårsvurderingRepo,
    private val vedtakRepo: VedtakRepo,
    private val clock: Clock,
) : VilkårsvurderingService {
    override fun lagre(behandlingId: UUID, vilkårsvurderinger: Vilkårsvurderinger) {
        vilkårsvurderingRepo.lagre(behandlingId, vilkårsvurderinger.uføre)
    }

    override fun opprettVilkårsvurderinger(sakId: UUID, periode: Periode): Vilkårsvurderinger {
        val vedtakIPeriode = vedtakRepo.hentForSakId(sakId)
            .filterIsInstance<Vedtak.EndringIYtelse>() // TODO this must surely change at some point, needed to perserve information added by i.e revurdering below 10% or avslag.
            .filter { it.periode overlapper periode }

        val uføreVilkårsvurderingerIPeriode = vedtakIPeriode
            .map { it.behandling.vilkårsvurderinger.copy() }
            .let { vilkår ->
                Tidslinje(
                    periode = periode,
                    objekter = vilkår.map { it.uføre }
                        .filterIsInstance<Vilkår.Vurdert<Grunnlag.Uføregrunnlag>>()
                        .flatMap { it.vurderingsperioder },
                    clock = clock,
                )
            }.tidslinje

        return Vilkårsvurderinger(
            uføre = Vilkår.Vurdert.Uførhet.create(uføreVilkårsvurderingerIPeriode),
        )
    }
}
