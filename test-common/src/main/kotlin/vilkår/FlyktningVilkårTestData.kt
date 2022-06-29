package no.nav.su.se.bakover.test.vilkår

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFlyktning
import no.nav.su.se.bakover.test.fixedTidspunkt
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
