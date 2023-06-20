package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Nel

sealed interface VurdertVilkår : Vilkår {
    val vurderingsperioder: Nel<Vurderingsperiode>
}
