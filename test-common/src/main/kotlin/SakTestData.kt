package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
import java.util.UUID

val sakId: UUID = UUID.randomUUID()

fun Sak.hentGjeldendeVilkårOgGrunnlag(
    periode: Periode,
    clock: Clock,
): GrunnlagsdataOgVilkårsvurderinger.Revurdering {
    return hentGjeldendeVedtaksdata(
        periode = periode,
        clock = clock,
    ).fold(
        {
            GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                Grunnlagsdata.IkkeVurdert,
                Vilkårsvurderinger.Revurdering.ikkeVurdert(),
            )
        },
        {
            GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                grunnlagsdata = it.grunnlagsdata,
                vilkårsvurderinger = it.vilkårsvurderinger,
            )
        },
    )
}
