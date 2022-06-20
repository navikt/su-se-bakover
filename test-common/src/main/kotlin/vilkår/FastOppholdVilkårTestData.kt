package no.nav.su.se.bakover.test.vilkår

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFastOppholdINorge
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeFastOppholdAvslag
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeFastOppholdInnvilget

fun fastOppholdVilkårInnvilget(
    vurderingsperioder: Nel<VurderingsperiodeFastOppholdINorge> = nonEmptyListOf(vurderingsperiodeFastOppholdInnvilget()),
) = FastOppholdINorgeVilkår.Vurdert.tryCreate(vurderingsperioder = vurderingsperioder).getOrFail()

fun fastOppholdVilkårAvslag(
    vurderingsperioder: Nel<VurderingsperiodeFastOppholdINorge> = nonEmptyListOf(vurderingsperiodeFastOppholdAvslag()),
) = FastOppholdINorgeVilkår.Vurdert.tryCreate(vurderingsperioder = vurderingsperioder).getOrFail()
