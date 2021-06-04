package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.lagTidslinje
import no.nav.su.se.bakover.domain.vedtak.vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

data class KopierGjeldendeGrunnlagsdataOgVilkårsvurderinger(
    private val periode: Periode,
    private val vedtakListe: List<Vedtak>,
) {
    val grunnlagsdata: Grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger

    private val vedtakstidslinje = vedtakListe
        .filterIsInstance<VedtakSomKanRevurderes>()
        .lagTidslinje(periode)

    private val vilkårsvurderingerFraTidslinje = vedtakstidslinje.vilkårsvurderinger()

    // Utleder grunnlagstyper som kan knyttes til vilkår via deres respektive vilkårsvurderinger
    private val uføreGrunnlagOgVilkår = when (val uførevilkår = vilkårsvurderingerFraTidslinje.uføre) {
        Vilkår.IkkeVurdert.Uførhet -> throw IllegalStateException("Kan ikke opprette vilkårsvurdering fra ikke-vurderte vilkår")
        is Vilkår.Vurdert.Uførhet -> Pair(uførevilkår.grunnlag, uførevilkår)
    }

    private val fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag> = vedtakstidslinje.flatMap { it.fradrag }.map {
        Grunnlag.Fradragsgrunnlag(id = UUID.randomUUID(), opprettet = Tidspunkt.now(), fradrag = it)
    }

    init {
        grunnlagsdata = Grunnlagsdata(
            uføregrunnlag = uføreGrunnlagOgVilkår.first,
            fradragsgrunnlag = fradragsgrunnlag,
        )
        vilkårsvurderinger = Vilkårsvurderinger(
            uføre = uføreGrunnlagOgVilkår.second,
        )
    }
}
