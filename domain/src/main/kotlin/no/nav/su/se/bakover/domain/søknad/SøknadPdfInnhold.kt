package no.nav.su.se.bakover.domain.søknad

import dokument.domain.generer.bygger.maler.SøknadPdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import person.domain.Person
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

fun lagSøknadPdfInnhold(
    saksnummer: Saksnummer,
    sakstype: Sakstype,
    søknadsId: UUID,
    navn: Person.Navn,
    søknadOpprettet: Tidspunkt,
    søknadInnhold: SøknadInnhold,
    clock: Clock,
) = SøknadPdfInnhold(
    sakstype = sakstype,
    saksnummer = saksnummer,
    søknadsId = søknadsId,
    navn = navn,
    dagensDatoOgTidspunkt = LocalDateTime.now(clock.withZone(zoneIdOslo))
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
    søknadOpprettet = søknadOpprettet.toLocalDate(zoneIdOslo).ddMMyyyy(),
    søknadInnhold = søknadInnhold,
    clock = clock,
)
