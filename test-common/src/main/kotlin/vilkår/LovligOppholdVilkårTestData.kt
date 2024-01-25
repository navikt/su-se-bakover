package no.nav.su.se.bakover.test.vilkår

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeLovligOppholdAvslag
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeLovligOppholdInnvilget
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.lovligopphold.domain.VurderingsperiodeLovligOpphold

fun lovligOppholdVilkårInnvilget(
    vurderingsperioder: Nel<VurderingsperiodeLovligOpphold> = nonEmptyListOf(vurderingsperiodeLovligOppholdInnvilget()),
) = LovligOppholdVilkår.Vurdert.tryCreate(vurderingsperioder = vurderingsperioder).getOrFail()

fun lovligOppholdVilkårAvslag(
    vurderingsperioder: Nel<VurderingsperiodeLovligOpphold> = nonEmptyListOf(vurderingsperiodeLovligOppholdAvslag()),
) = LovligOppholdVilkår.Vurdert.tryCreate(vurderingsperioder = vurderingsperioder).getOrFail()
