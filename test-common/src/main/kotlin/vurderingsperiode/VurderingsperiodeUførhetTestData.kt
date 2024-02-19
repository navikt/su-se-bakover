package no.nav.su.se.bakover.test.vurderingsperiode

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlagForventetInntekt0
import vilkår.common.domain.Vurdering
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.uføre.domain.VurderingsperiodeUføre
import java.util.UUID

fun nyVurderingsperiodeUførhet(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    vurdering: Vurdering = Vurdering.Innvilget,
    grunnlag: Uføregrunnlag = uføregrunnlagForventetInntekt0(),
    periode: Periode = år(2021),
): VurderingsperiodeUføre = VurderingsperiodeUføre.create(
    id = id,
    opprettet = opprettet,
    vurdering = vurdering,
    grunnlag = grunnlag,
    periode = periode,
)
