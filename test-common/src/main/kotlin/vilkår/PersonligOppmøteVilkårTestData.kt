package vilkår

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodePersonligOppmøte
import no.nav.su.se.bakover.test.getOrFail
import vurderingsperiode.vurderingsperiodePersonligOppmøteAvslag
import vurderingsperiode.vurderingsperiodePersonligOppmøteInnvilget

fun personligOppmøtevilkårInnvilget(
    vurderingsperioder: Nel<VurderingsperiodePersonligOppmøte> = nonEmptyListOf(
        vurderingsperiodePersonligOppmøteInnvilget(),
    ),
) = PersonligOppmøteVilkår.Vurdert.tryCreate(vurderingsperioder = vurderingsperioder).getOrFail()

fun personligOppmøtevilkårAvslag(
    vurderingsperioder: Nel<VurderingsperiodePersonligOppmøte> = nonEmptyListOf(
        vurderingsperiodePersonligOppmøteAvslag(),
    ),
) = PersonligOppmøteVilkår.Vurdert.tryCreate(vurderingsperioder = vurderingsperioder).getOrFail()
