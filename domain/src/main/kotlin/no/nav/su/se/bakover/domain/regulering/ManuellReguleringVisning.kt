package no.nav.su.se.bakover.domain.regulering

import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import beregning.domain.Beregning
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.uføre.domain.Uføregrunnlag
import økonomi.domain.simulering.Simulering

// Modell tiltenkt frontend, utvider derfor ikke Regulering.kt
data class ManuellReguleringVisning(
    val eksisterendeGrunnlagsdata: ReguleringGrunnlagsdata,
    val nyGrunnlagsdata: ReguleringGrunnlagsdata,
    val beregning: Beregning?,
    val simulering: Simulering?,

) {
    companion object {

        fun create(
            gjeldendeVedtaksdata: GjeldendeVedtaksdata,
            regulering: Regulering,
        ): ManuellReguleringVisning {
            return ManuellReguleringVisning(
                eksisterendeGrunnlagsdata = uføreOgFradragGrunnlag(gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger),
                nyGrunnlagsdata = uføreOgFradragGrunnlag(regulering.grunnlagsdataOgVilkårsvurderinger),
                beregning = regulering.beregning,
                simulering = regulering.simulering,
            )
        }

        private fun uføreOgFradragGrunnlag(
            grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering,
        ) = with(grunnlagsdataOgVilkårsvurderinger) {
            val uføregrunnlag = vilkårsvurderinger.uføreVilkår().getOrNull()?.grunnlag
            val fradrag = grunnlagsdata.fradragsgrunnlag.map { it.fradrag }
            ReguleringGrunnlagsdata(uføregrunnlag, fradrag)
        }
    }
}

data class ReguleringGrunnlagsdata(
    val uføregrunnlag: List<Uføregrunnlag>?,
    val fradrag: List<Fradrag>,

    // Tod eksternt grunnlag??
    // skatt
)
