package no.nav.su.se.bakover.test.vilkår

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.test.getOrFail
import vilkår.familiegjenforening.domain.FamiliegjenforeningVilkår
import vilkår.familiegjenforening.domain.VurderingsperiodeFamiliegjenforening
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningAvslag
import vurderingsperiode.vurderingsperiodeFamiliegjenforeningInnvilget

fun familiegjenforeningVilkårInnvilget(
    vurderingsperioder: Nel<VurderingsperiodeFamiliegjenforening> = nonEmptyListOf(vurderingsperiodeFamiliegjenforeningInnvilget()),
): FamiliegjenforeningVilkår.Vurdert =
    FamiliegjenforeningVilkår.Vurdert.create(vurderingsperioder = vurderingsperioder).getOrFail()

fun familiegjenforeningVilkårAvslag(
    vurderingsperioder: Nel<VurderingsperiodeFamiliegjenforening> = nonEmptyListOf(vurderingsperiodeFamiliegjenforeningAvslag()),
) = FamiliegjenforeningVilkår.Vurdert.create(vurderingsperioder = vurderingsperioder).getOrFail()
