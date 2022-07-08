package no.nav.su.se.bakover.test.vilkår

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFastOppholdINorge
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import java.util.UUID

fun fastOppholdVilkårInnvilget(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): FastOppholdINorgeVilkår.Vurdert {
    return FastOppholdINorgeVilkår.Vurdert.tryCreate(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeFastOppholdINorge.create(
                id = id,
                opprettet = opprettet,
                periode = periode,
                vurdering = Vurdering.Innvilget,
            ),
        ),
    ).getOrFail()
}

fun fastOppholdVilkårAvslag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): FastOppholdINorgeVilkår.Vurdert {
    return FastOppholdINorgeVilkår.Vurdert.tryCreate(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeFastOppholdINorge.create(
                id = id,
                opprettet = opprettet,
                periode = periode,
                vurdering = Vurdering.Avslag,
            ),
        ),
    ).getOrFail()
}
