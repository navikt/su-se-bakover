package no.nav.su.se.bakover.test.vilkår

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeInstitusjonsopphold
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeInstitusjonsoppholdAvslag
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeInstitusjonsoppholdInnvilget

fun insitusjonsoppholdvilkårInnvilget(
    vurderingsperioder: Nel<VurderingsperiodeInstitusjonsopphold> = nonEmptyListOf(
        vurderingsperiodeInstitusjonsoppholdInnvilget(),
    ),
) = InstitusjonsoppholdVilkår.Vurdert.tryCreate(vurderingsperioder = vurderingsperioder).getOrFail()

fun insitusjonsoppholdvilkårAvslag(
    vurderingsperioder: Nel<VurderingsperiodeInstitusjonsopphold> = nonEmptyListOf(
        vurderingsperiodeInstitusjonsoppholdAvslag(),
    ),
) = InstitusjonsoppholdVilkår.Vurdert.tryCreate(vurderingsperioder = vurderingsperioder).getOrFail()
