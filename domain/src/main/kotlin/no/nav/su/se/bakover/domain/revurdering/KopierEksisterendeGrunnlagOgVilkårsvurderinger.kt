package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.lagTidslinje
import no.nav.su.se.bakover.domain.vedtak.vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger

data class KopierEksisterendeGrunnlagOgVilkårsvurderinger(
    private val periode: Periode,
    private val vedtakListe: List<Vedtak>,
) {
    val grunnlagsdata: Grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger

    private val vedtakstidslinje = vedtakListe
        .filterIsInstance<Vedtak.EndringIYtelse>()
        .lagTidslinje(periode)

    private val vilkårsvurderingerFraTidslinje = vedtakstidslinje.vilkårsvurderinger()

    // Utleder grunnlagstyper som kan knyttes til vilkår via deres respektive vilkårsvurderinger
    private val uføreGrunnlagOgVilkår = when (val uførevilkår = vilkårsvurderingerFraTidslinje.uføre) {
        Vilkår.IkkeVurdert.Uførhet -> throw IllegalStateException("Kan ikke opprette grunnlag og vilkårsvurderinger fra ikke-vurderte vilkår")
        is Vilkår.Vurdert.Uførhet -> Pair(uførevilkår.grunnlag, uførevilkår)
    }

    init {
        grunnlagsdata = Grunnlagsdata(
            uføregrunnlag = uføreGrunnlagOgVilkår.first,
        )
        vilkårsvurderinger = Vilkårsvurderinger(
            uføre = uføreGrunnlagOgVilkår.second,
        )
    }
}
