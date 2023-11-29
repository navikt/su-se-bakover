package no.nav.su.se.bakover.domain.behandling.avslag

import beregning.domain.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag

data class Avslag(
    val avslagsgrunner: List<Avslagsgrunn>,
    val harEktefelle: Boolean,
    val beregning: Beregning?,
    val formuegrunnlag: Formuegrunnlag?,
    val halvtGrunnbeløpPerÅr: Int,
)
