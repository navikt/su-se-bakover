package no.nav.su.se.bakover.domain.søknad.søknadinnhold

import java.time.LocalDate

data class Utenlandsopphold(
    val registrertePerioder: List<UtenlandsoppholdPeriode>? = null,
    val planlagtePerioder: List<UtenlandsoppholdPeriode>? = null,
)

data class UtenlandsoppholdPeriode(
    val utreisedato: LocalDate,
    val innreisedato: LocalDate,
)
