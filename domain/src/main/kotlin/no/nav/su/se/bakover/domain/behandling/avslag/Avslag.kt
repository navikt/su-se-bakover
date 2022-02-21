package no.nav.su.se.bakover.domain.behandling.avslag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag

data class Avslag(
    val opprettet: Tidspunkt,
    val avslagsgrunner: List<Avslagsgrunn>,
    val harEktefelle: Boolean,
    val beregning: Beregning?,
    val formuegrunnlag: Formuegrunnlag?
) {
    val halvGrunnbeløp: Double = Grunnbeløp.`0,5G`.påDato(opprettet.toLocalDate(zoneIdOslo))
}
