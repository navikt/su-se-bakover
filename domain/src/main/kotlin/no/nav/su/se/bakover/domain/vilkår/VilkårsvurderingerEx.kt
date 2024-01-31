package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import vilkår.familiegjenforening.domain.FamiliegjenforeningVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.vurderinger.domain.VilkårEksistererIkke
import vilkår.vurderinger.domain.Vilkårsvurderinger

fun Vilkårsvurderinger.uføreVilkår(): Either<VilkårEksistererIkke, UføreVilkår> = when (this) {
    is VilkårsvurderingerRevurdering.Alder -> VilkårEksistererIkke.left()
    is VilkårsvurderingerRevurdering.Uføre -> uføre.right()
    is VilkårsvurderingerSøknadsbehandling.Uføre -> uføre.right()
    is VilkårsvurderingerSøknadsbehandling.Alder -> VilkårEksistererIkke.left()
    else -> throw IllegalStateException("Ukjent vilkårsvurderings-implementasjon: $this")
}

fun Vilkårsvurderinger.uføreVilkårKastHvisAlder(): UføreVilkår = when (this) {
    is VilkårsvurderingerRevurdering.Alder -> TODO("Kan ikke hente uføre vilkår. Vilkårsvurderinger for alder.")
    is VilkårsvurderingerRevurdering.Uføre -> uføre
    is VilkårsvurderingerSøknadsbehandling.Uføre -> uføre
    is VilkårsvurderingerSøknadsbehandling.Alder -> TODO("Kan ikke hente uføre vilkår. Vilkårsvurderinger for alder.")
    else -> throw IllegalStateException("Ukjent vilkårsvurderings-implementasjon: $this")
}

fun Vilkårsvurderinger.flyktningVilkår(): Either<VilkårEksistererIkke, FlyktningVilkår> = when (this) {
    is VilkårsvurderingerRevurdering.Alder -> VilkårEksistererIkke.left()
    is VilkårsvurderingerRevurdering.Uføre -> flyktning.right()
    is VilkårsvurderingerSøknadsbehandling.Alder -> VilkårEksistererIkke.left()
    is VilkårsvurderingerSøknadsbehandling.Uføre -> flyktning.right()
    else -> throw IllegalStateException("Ukjent vilkårsvurderings-implementasjon: $this")
}

fun Vilkårsvurderinger.pensjonsVilkår(): Either<VilkårEksistererIkke, PensjonsVilkår> = when (this) {
    is VilkårsvurderingerRevurdering.Alder -> pensjon.right()
    is VilkårsvurderingerRevurdering.Uføre -> VilkårEksistererIkke.left()
    is VilkårsvurderingerSøknadsbehandling.Alder -> pensjon.right()
    is VilkårsvurderingerSøknadsbehandling.Uføre -> VilkårEksistererIkke.left()
    else -> throw IllegalStateException("Ukjent vilkårsvurderings-implementasjon: $this")
}

fun Vilkårsvurderinger.familiegjenforening(): Either<VilkårEksistererIkke, FamiliegjenforeningVilkår> = when (this) {
    is VilkårsvurderingerRevurdering.Alder -> familiegjenforening.right()
    is VilkårsvurderingerRevurdering.Uføre -> VilkårEksistererIkke.left()
    is VilkårsvurderingerSøknadsbehandling.Alder -> familiegjenforening.right()
    is VilkårsvurderingerSøknadsbehandling.Uføre -> VilkårEksistererIkke.left()
    else -> throw IllegalStateException("Ukjent vilkårsvurderings-implementasjon: $this")
}

/**
 * Skal kun kalles fra undertyper av [Vilkårsvurderinger].
 */
internal fun Vilkårsvurderinger.kastHvisPerioderErUlike() {
    // Merk at hvert enkelt [Vilkår] passer på sine egne data (som f.eks. at periodene er sorterte og uten duplikater)
    vilkår.map { Pair(it.vilkår, it.perioder) }.zipWithNext { a, b ->
        // Vilkår med tomme perioder har ikke blitt vurdert enda.
        if (a.second.isNotEmpty() && b.second.isNotEmpty()) {
            require(a.second == b.second) {
                "Periodene til Vilkårsvurderinger er ulike. $a vs $b."
            }
        }
    }
}

/**
 * @throws NotImplementedError for alder
 */
fun Vilkårsvurderinger.hentUføregrunnlag(): List<Uføregrunnlag> = when (this) {
    is VilkårsvurderingerRevurdering.Uføre -> this.uføre.grunnlag
    is VilkårsvurderingerSøknadsbehandling.Uføre -> this.uføre.grunnlag
    is VilkårsvurderingerRevurdering.Alder -> TODO("vilkårsvurdering_alder brev for alder ikke implementert enda")
    is VilkårsvurderingerSøknadsbehandling.Alder -> emptyList() // TODO("vilkårsvurdering_alder brev for alder ikke implementert enda")
    else -> throw IllegalStateException("Ukjent vilkårsvurderings-implementasjon: $this")
}
