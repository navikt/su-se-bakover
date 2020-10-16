package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel

data class Oppdragsmelding(
    val originalMelding: String,
    val avstemmingsnøkkel: Avstemmingsnøkkel
)
