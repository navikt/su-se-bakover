package no.nav.su.se.bakover.test.vilkår

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeLovligOppholdAvslag
import no.nav.su.se.bakover.test.vurderingsperiode.vurderingsperiodeLovligOppholdInnvilget
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.lovligopphold.domain.VurderingsperiodeLovligOpphold

fun lovligOppholdVilkårInnvilget(
    vurderingsperioder: Nel<VurderingsperiodeLovligOpphold> = nonEmptyListOf(vurderingsperiodeLovligOppholdInnvilget()),
) = LovligOppholdVilkår.Vurdert.tryCreate(vurderingsperioder = vurderingsperioder).getOrFail()

fun lovligOppholdVilkårInnvilget(
    vurderingsperiode: Periode = år(2021),
    vurderingsperioder: Nel<VurderingsperiodeLovligOpphold> = nonEmptyListOf(
        vurderingsperiodeLovligOppholdInnvilget(
            vurderingsperiode = vurderingsperiode,
        ),
    ),
) = LovligOppholdVilkår.Vurdert.tryCreate(vurderingsperioder = vurderingsperioder).getOrFail()

fun lovligOppholdVilkårAvslag(
    vurderingsperioder: Nel<VurderingsperiodeLovligOpphold> = nonEmptyListOf(vurderingsperiodeLovligOppholdAvslag()),
) = LovligOppholdVilkår.Vurdert.tryCreate(vurderingsperioder = vurderingsperioder).getOrFail()
