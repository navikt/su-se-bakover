package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår

fun List<VedtakPåTidslinje>.institusjonsoppholdVilkår(): InstitusjonsoppholdVilkår {
    return map { it.vilkårsvurderinger.institusjonsopphold }
        .filterIsInstance<InstitusjonsoppholdVilkår.Vurdert>()
        .flatMap { it.vurderingsperioder }
        .let {
            if (it.isNotEmpty()) {
                InstitusjonsoppholdVilkår.Vurdert.create(
                    it.toNonEmptyList(),
                ).slåSammenLikePerioder()
            } else {
                InstitusjonsoppholdVilkår.IkkeVurdert
            }
        }
}
