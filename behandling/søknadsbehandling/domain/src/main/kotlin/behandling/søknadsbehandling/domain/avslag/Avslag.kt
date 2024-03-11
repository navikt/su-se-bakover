package behandling.søknadsbehandling.domain.avslag

import beregning.domain.Beregning
import vilkår.common.domain.Avslagsgrunn
import vilkår.formue.domain.Formuegrunnlag

data class Avslag(
    val avslagsgrunner: List<Avslagsgrunn>,
    val harEktefelle: Boolean,
    val beregning: Beregning?,
    val formuegrunnlag: Formuegrunnlag?,
    val halvtGrunnbeløpPerÅr: Int,
)
