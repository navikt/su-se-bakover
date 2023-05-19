package no.nav.su.se.bakover.domain.behandling.avslag

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag

data class Avslag(
    val opprettet: Tidspunkt,
    val avslagsgrunner: List<Avslagsgrunn>,
    val harEktefelle: Boolean,
    val beregning: Beregning?,
    val formuegrunnlag: Formuegrunnlag?,
    val halvtGrunnbeløpPerÅr: Int,
)
