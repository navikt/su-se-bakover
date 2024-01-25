package no.nav.su.se.bakover.test.vilkår

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedTidspunkt
import vilkår.domain.Vurdering
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.flyktning.domain.VurderingsperiodeFlyktning
import java.util.UUID

fun flyktningVilkårInnvilget(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): FlyktningVilkår.Vurdert {
    return FlyktningVilkår.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeFlyktning.create(
                id = id,
                opprettet = opprettet,
                periode = periode,
                vurdering = Vurdering.Innvilget,
            ),
        ),
    )
}

fun flyktningVilkårAvslått(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): FlyktningVilkår.Vurdert {
    return FlyktningVilkår.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeFlyktning.create(
                id = id,
                opprettet = opprettet,
                periode = periode,
                vurdering = Vurdering.Avslag,
            ),
        ),
    )
}
