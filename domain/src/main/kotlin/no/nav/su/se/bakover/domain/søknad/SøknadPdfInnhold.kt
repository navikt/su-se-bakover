package no.nav.su.se.bakover.domain.søknad

import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.SøknadInnhold
import java.time.LocalDate

data class SøknadPdfInnhold(
    val saksnummer: Saksnummer,
    val navn: Person.Navn,
    val dagensDato: String = LocalDate.now().ddMMyyyy(),
    val søknadOpprettet: String,
    val søknadInnhold: SøknadInnhold
)
