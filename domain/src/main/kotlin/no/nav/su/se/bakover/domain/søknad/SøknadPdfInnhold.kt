package no.nav.su.se.bakover.domain.søknad

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.SøknadInnhold
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class SøknadPdfInnhold(
    val saksnummer: Saksnummer,
    val søknadsId: UUID,
    val navn: Person.Navn,
    val dagensDatoOgTidspunkt: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
    val søknadOpprettet: String,
    val søknadInnhold: SøknadInnhold
)
