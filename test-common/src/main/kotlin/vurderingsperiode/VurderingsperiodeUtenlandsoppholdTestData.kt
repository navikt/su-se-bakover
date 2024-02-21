package no.nav.su.se.bakover.test.vurderingsperiode

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.grunnlag.nyUtenlandsoppholdgrunnlag
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.VurderingsperiodeUtenlandsopphold
import vilkår.common.domain.Vurdering
import java.util.UUID

fun nyVurderingsperiodeUtenlandsopphold(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    vurdering: Vurdering = Vurdering.Innvilget,
    grunnlag: Utenlandsoppholdgrunnlag = nyUtenlandsoppholdgrunnlag(),
    periode: Periode = år(2021),
): VurderingsperiodeUtenlandsopphold = VurderingsperiodeUtenlandsopphold.create(
    id = id,
    opprettet = opprettet,
    vurdering = vurdering,
    grunnlag = grunnlag,
    periode = periode,
)
