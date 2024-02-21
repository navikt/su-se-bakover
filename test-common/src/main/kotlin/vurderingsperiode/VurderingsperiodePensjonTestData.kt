package no.nav.su.se.bakover.test.vurderingsperiode

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.grunnlag.nyPensjonsgrunnlag
import vilkår.common.domain.Vurdering
import vilkår.pensjon.domain.Pensjonsgrunnlag
import vilkår.pensjon.domain.VurderingsperiodePensjon
import java.util.UUID

fun nyVurderingsperiodePensjon(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    vurdering: Vurdering = Vurdering.Innvilget,
    pensjonsgrunnlag: Pensjonsgrunnlag = nyPensjonsgrunnlag(),
): VurderingsperiodePensjon = VurderingsperiodePensjon.create(
    id = id,
    opprettet = opprettet,
    periode = periode,
    vurdering = vurdering,
    grunnlag = pensjonsgrunnlag,
)
