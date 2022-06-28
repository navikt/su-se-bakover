package no.nav.su.se.bakover.test.vilkår

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUtenlandsopphold
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import java.util.UUID

fun utenlandsoppholdInnvilget(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
    grunnlag: Utenlandsoppholdgrunnlag? = null,
): UtenlandsoppholdVilkår.Vurdert {
    return UtenlandsoppholdVilkår.Vurdert.tryCreate(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeUtenlandsopphold.create(
                id = id,
                opprettet = opprettet,
                vurdering = Vurdering.Innvilget,
                grunnlag = grunnlag,
                periode = periode,
            ),
        ),
    ).getOrFail()
}

fun utenlandsoppholdAvslag(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    periode: Periode = år(2021),
): UtenlandsoppholdVilkår.Vurdert {
    return UtenlandsoppholdVilkår.Vurdert.tryCreate(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeUtenlandsopphold.create(
                id = id,
                opprettet = opprettet,
                vurdering = Vurdering.Avslag,
                grunnlag = null,
                periode = periode,
            ),
        ),
    ).getOrFail()
}
