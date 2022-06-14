package no.nav.su.se.bakover.test.vilkår

import arrow.core.Nel
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.domain.vilkår.FamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFamiliegjenforening
import no.nav.su.se.bakover.test.getOrFail
import vurderingsperiodeFamiliegjenforening

fun familiegjenforeningVilkår(
    vurderingsperiode: Nel<VurderingsperiodeFamiliegjenforening> = nonEmptyListOf(vurderingsperiodeFamiliegjenforening()),
): FamiliegjenforeningVilkår.Vurdert =
    FamiliegjenforeningVilkår.Vurdert.create(vurderingsperioder = vurderingsperiode).getOrFail()
