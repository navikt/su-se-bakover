package no.nav.su.se.bakover.test.vilkår

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeInstitusjonsoppholdAvslag
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeInstitusjonsoppholdInnvilget

fun institusjonsoppholdvilkårInnvilget(
    periode: Periode = år(2021),
) = InstitusjonsoppholdVilkår.Vurdert.tryCreate(
    vurderingsperioder = nonEmptyListOf(vurderingsperiodeInstitusjonsoppholdInnvilget(vurderingsperiode = periode)),
).getOrFail()

fun institusjonsoppholdvilkårAvslag(
    periode: Periode = år(2021),
) = InstitusjonsoppholdVilkår.Vurdert.tryCreate(
    vurderingsperioder = nonEmptyListOf(vurderingsperiodeInstitusjonsoppholdAvslag(vurderingsperiode = periode)),
).getOrFail()
