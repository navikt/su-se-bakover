package no.nav.su.se.bakover.test.vurderingsperiode

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.stønadsperiode2021
import vilkår.common.domain.Vurdering
import vilkår.fastopphold.domain.VurderingsperiodeFastOppholdINorge
import java.util.UUID

fun nyVurderingsperiodeFastOppholdINorge(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    vurdering: Vurdering = Vurdering.Innvilget,
    periode: Periode = stønadsperiode2021.periode,
): VurderingsperiodeFastOppholdINorge = VurderingsperiodeFastOppholdINorge.tryCreate(
    id = id,
    opprettet = opprettet,
    vurdering = vurdering,
    vurderingsperiode = periode,
).getOrFail()
