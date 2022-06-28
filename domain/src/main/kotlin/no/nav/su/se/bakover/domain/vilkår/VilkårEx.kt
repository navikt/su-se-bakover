package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Nel

fun Set<Vilkår>.erLik(other: Set<Vilkår>): Boolean {
    return count() == other.count() &&
        mapIndexed { index, vilkår -> other.toList()[index].erLik(vilkår) }
            .all { it }
}

fun Nel<Vurderingsperiode>.erLik(other: Nel<Vurderingsperiode>): Boolean {
    return count() == other.count() &&
        mapIndexed { index, vurderingsperiode -> other[index].erLik(vurderingsperiode) }
            .all { it }
}
