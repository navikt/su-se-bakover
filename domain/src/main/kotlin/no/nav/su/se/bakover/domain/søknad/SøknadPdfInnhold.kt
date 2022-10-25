package no.nav.su.se.bakover.domain.søknad

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadInnhold
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class SøknadPdfInnhold private constructor(
    val saksnummer: Saksnummer,
    val søknadsId: UUID,
    val navn: Person.Navn,
    private val clock: Clock,
    val dagensDatoOgTidspunkt: String = LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
    val søknadOpprettet: String,
    val søknadInnhold: SøknadInnhold,
) {
    companion object {
        fun create(
            saksnummer: Saksnummer,
            søknadsId: UUID,
            navn: Person.Navn,
            søknadOpprettet: Tidspunkt,
            søknadInnhold: SøknadInnhold,
            clock: Clock,
        ) = SøknadPdfInnhold(
            saksnummer = saksnummer,
            søknadsId = søknadsId,
            navn = navn,
            dagensDatoOgTidspunkt = LocalDateTime.now(clock.withZone(zoneIdOslo)).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
            søknadOpprettet = søknadOpprettet.toLocalDate(zoneIdOslo).ddMMyyyy(),
            søknadInnhold = søknadInnhold,
            clock = clock,
        )
    }
}
