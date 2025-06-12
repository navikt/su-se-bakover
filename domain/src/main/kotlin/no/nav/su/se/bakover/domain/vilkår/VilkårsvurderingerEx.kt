package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import behandling.søknadsbehandling.domain.VilkårsvurderingerSøknadsbehandling
import vilkår.familiegjenforening.domain.FamiliegjenforeningVilkår
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.vurderinger.domain.VilkårEksistererIkke
import vilkår.vurderinger.domain.Vilkårsvurderinger

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
 * @throws NotImplementedError for alder
 */
fun Vilkårsvurderinger.hentUføregrunnlag(): List<Uføregrunnlag> = when (this) {
    is VilkårsvurderingerRevurdering.Uføre -> this.uføre.grunnlag
    is VilkårsvurderingerSøknadsbehandling.Uføre -> this.uføre.grunnlag
    is VilkårsvurderingerRevurdering.Alder, is VilkårsvurderingerSøknadsbehandling.Alder -> throw IllegalStateException(
        "Prøver å hente uføregrunnlag for alder $this",
    )

    else -> throw IllegalStateException("Ukjent vilkårsvurderings-implementasjon: $this")
}
