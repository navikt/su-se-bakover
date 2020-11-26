package no.nav.su.se.bakover.domain.søknad

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.SøknadInnhold
import java.time.LocalDate
import java.util.UUID

data class SøknadPdfInnhold(
    val sakId: UUID,
    val navn: Person.Navn,
    val søknadOpprettet: LocalDate,
    val søknadInnhold: SøknadInnhold
)



