package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.uføre.domain.Uføregrunnlag

data class ReguleringGrunnlagsdata(
    val uføreFraGjeldendeVedtak: List<Uføregrunnlag>,
    val fradragFraGjeldendeVedtak: List<Fradrag>,

    val uføreUnderRegulering: List<Uføregrunnlag>?,
    val fradragUnderRegulering: List<Fradrag>?,
) {
    companion object {
        fun create(
            gjeldendeVedtaksdata: GjeldendeVedtaksdata,
            regulering: Regulering,
        ): ReguleringGrunnlagsdata {
            val grunnlagOgVilkår = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger

            val lok = regulering.grunnlagsdataOgVilkårsvurderinger
            return ReguleringGrunnlagsdata(
                uføreFraGjeldendeVedtak = grunnlagOgVilkår.vilkårsvurderinger.uføreVilkår().getOrNull()?.grunnlag
                    ?: emptyList(),
                fradragFraGjeldendeVedtak = grunnlagOgVilkår.grunnlagsdata.fradragsgrunnlag.map { it.fradrag },
                uføreUnderRegulering = null, // TODO bjg fylle ut når det persisteres på reguleringsbehandling
                fradragUnderRegulering = null, // TODO
            )
        }
    }
}
