package no.nav.su.se.bakover.test.vurderingsperiode

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.grunnlag.formueGrunnlagMedEps0Innvilget
import vilkår.common.domain.Vurdering
import vilkår.formue.domain.Formuegrunnlag
import vilkår.formue.domain.VurderingsperiodeFormue
import java.util.UUID

fun nyVurderingsperiodeFormue(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    vurdering: Vurdering = Vurdering.Innvilget,
    periode: Periode = år(2021),
    grunnlag: Formuegrunnlag = formueGrunnlagMedEps0Innvilget(),
): VurderingsperiodeFormue = VurderingsperiodeFormue.create(
    id = id,
    opprettet = opprettet,
    vurdering = vurdering,
    periode = periode,
    grunnlag = grunnlag,
)
