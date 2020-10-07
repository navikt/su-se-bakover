package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel

/**
 * de-serialisering Mapping-klasse for å konvertere mellom gammelt og nytt database json-format
 */
data class OppdragsmeldingJson(
    val status: Oppdragsmelding.Oppdragsmeldingstatus,
    val originalMelding: String,
    val avstemmingsnøkkel: Avstemmingsnøkkel?,
    val tidspunkt: Tidspunkt?
) {
    fun toOppdragsmelding(): Oppdragsmelding =
        if (avstemmingsnøkkel != null) Oppdragsmelding(status, originalMelding, avstemmingsnøkkel) else Oppdragsmelding(
            status, originalMelding,
            Avstemmingsnøkkel(tidspunkt!!) // Dersom vi får nullpointer her er det et ukjent format.
        )
}
