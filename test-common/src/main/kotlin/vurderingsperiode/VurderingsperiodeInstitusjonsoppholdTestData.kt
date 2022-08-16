package no.nav.su.se.bakover.test.vurderingsperiode

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeInstitusjonsopphold
import no.nav.su.se.bakover.test.fixedTidspunkt
import java.util.UUID

fun vurderingsperiodeInstitusjonsoppholdInnvilget(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    vurdering: Vurdering = Vurdering.Innvilget,
    vurderingsperiode: Periode = år(2021),
) = VurderingsperiodeInstitusjonsopphold.create(
    id = id,
    opprettet = opprettet,
    vurdering = vurdering,
    periode = vurderingsperiode,
)

fun vurderingsperiodeInstitusjonsoppholdAvslag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    vurdering: Vurdering = Vurdering.Avslag,
    vurderingsperiode: Periode = år(2021),
) = VurderingsperiodeInstitusjonsopphold.create(
    id = id,
    opprettet = opprettet,
    vurdering = vurdering,
    periode = vurderingsperiode,
)
