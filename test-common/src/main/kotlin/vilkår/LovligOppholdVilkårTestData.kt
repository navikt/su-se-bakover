package no.nav.su.se.bakover.test.vilkår

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeLovligOpphold
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeLovligOppholdAvslag
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeLovligOppholdInnvilget

fun lovligOppholdVilkårInnvilget(
    vurderingsperioder: Nel<VurderingsperiodeLovligOpphold> = nonEmptyListOf(vurderingsperiodeLovligOppholdInnvilget()),
) = LovligOppholdVilkår.Vurdert.tryCreate(vurderingsperioder = vurderingsperioder).getOrFail()

fun lovligOppholdVilkårAvslag(
    vurderingsperioder: Nel<VurderingsperiodeLovligOpphold> = nonEmptyListOf(vurderingsperiodeLovligOppholdAvslag()),
) = LovligOppholdVilkår.Vurdert.tryCreate(vurderingsperioder = vurderingsperioder).getOrFail()
