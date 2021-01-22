package no.nav.su.se.bakover.domain.behandling.avslag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.beregning.Beregning

data class Avslag(
    val opprettet: Tidspunkt,
    val avslagsgrunner: List<Avslagsgrunn>,
    val harEktefelle: Boolean,
    val beregning: Beregning?,
) {
    val halvGrunnbeløp: Double = Grunnbeløp.`0,5G`.fraDato(opprettet.toLocalDate(zoneIdOslo))
}
