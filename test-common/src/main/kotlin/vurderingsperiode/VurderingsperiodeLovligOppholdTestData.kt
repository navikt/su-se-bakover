package no.nav.su.se.bakover.test.vurderingsperiode

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeLovligOpphold
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.domain.Vurdering
import java.util.UUID

fun vurderingsperiodeLovligOppholdInnvilget(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    vurdering: Vurdering = Vurdering.Innvilget,
    vurderingsperiode: Periode = år(2021),
) = VurderingsperiodeLovligOpphold.tryCreate(
    id = id,
    opprettet = opprettet,
    vurdering = vurdering,
    vurderingsperiode = vurderingsperiode,
)

fun vurderingsperiodeLovligOppholdAvslag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    vurdering: Vurdering = Vurdering.Avslag,
    vurderingsperiode: Periode = år(2021),
) = VurderingsperiodeLovligOpphold.tryCreate(
    id = id,
    opprettet = opprettet,
    vurdering = vurdering,
    vurderingsperiode = vurderingsperiode,
)
