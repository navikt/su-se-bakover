package no.nav.su.se.bakover.domain.regulering

import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata

data class ManuellReguleringVisning(
    val gjeldendeVedtaksdata: GrunnlagsdataOgVilkårsvurderingerRevurdering,
    val regulering: Regulering,
) {
    companion object {
        fun create(
            gjeldendeVedtaksdata: GjeldendeVedtaksdata,
            regulering: Regulering,
        ): ManuellReguleringVisning {
            return ManuellReguleringVisning(
                gjeldendeVedtaksdata = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
                regulering = regulering,
            )
        }
    }
}
