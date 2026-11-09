package no.nav.su.se.bakover.domain.kontrollnotat

import dokument.domain.generer.bygger.maler.KontrollnotatPdfInnholdNy
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import person.domain.Person
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun KontrollsamtaleNotat.toPdfInnhold(sakstype: Sakstype, saksnummer: Saksnummer, navn: Person.Navn, clock: Clock) =
    KontrollnotatPdfInnholdNy(
        sakstype = sakstype,
        saksnummer = saksnummer,
        navn = navn,
        dagensDatoOgTidspunkt = LocalDateTime.now(clock.withZone(zoneIdOslo))
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
        personligOppmøte = personligOppmøte,
        fullmaktOgLegeerklæring = fullmaktOgLegeerklæring,
        originalPass = originalPass,
        gyldigPass = gyldigPass,
        harVærtUtenlands = harVærtUtenlands,
        utenlandsoppholdDatoer = utenlandsoppholdDatoer.map { it.tilPeriodeStreng() },
        harPlanerOmUtenlandsreise = harPlanerOmUtenlandsreise,
        planlagteUtenlandsreiseDatoer = planlagteUtenlandsreiseDatoer.map { it.tilPeriodeStreng() },
        reiseDokumentasjon = reiseDokumentasjon,
        økonomiskSituasjon = økonomiskSituasjon,
        andreForhold = andreForhold,
        skatteOpplysninger = skatteOpplysninger,
        fritekst = fritekst,
    )
