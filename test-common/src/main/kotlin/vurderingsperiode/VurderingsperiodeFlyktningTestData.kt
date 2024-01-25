package no.nav.su.se.bakover.test.vurderingsperiode

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.domain.Vurdering
import vilkår.flyktning.domain.VurderingsperiodeFlyktning
import java.util.UUID

fun vurderingsperiodeFlyktning(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    vurdering: Vurdering = Vurdering.Innvilget,
    vurderingsperiode: Periode = år(2022),
) = VurderingsperiodeFlyktning.create(
    id = id,
    opprettet = opprettet,
    vurdering = vurdering,
    periode = vurderingsperiode,
)
