package no.nav.su.se.bakover.domain.kontrollnotat

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.bygger.maler.KontrollnotatPdfInnholdNy
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.domain.kontrollnotat.kontrollnotatInnhold.KontrollnotatInnhold
import person.domain.Person
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class KontrollnotatPdfInnhold private constructor(
    override val sakstype: Sakstype,
    val saksnummer: Saksnummer,
    val navn: Person.Navn,
    val dagensDatoOgTidspunkt: String,
    val kontrollnotat: KontrollnotatInnhold,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Kontrollnotat

    companion object {
        fun create(
            saksnummer: Saksnummer,
            sakstype: Sakstype,
            navn: Person.Navn,
            kontrollnotat: KontrollsamtaleNotat,
            clock: Clock,
        ): PdfInnhold {
            return KontrollnotatPdfInnhold(
                saksnummer = saksnummer,
                sakstype = sakstype,
                navn = navn,
                dagensDatoOgTidspunkt = LocalDateTime.now(clock.withZone(zoneIdOslo))
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
                kontrollnotat = KontrollnotatInnhold(
                    personligOppmøte = kontrollnotat.personligOppmøte,
                    fullmaktOgLegeerklæring = kontrollnotat.fullmaktOgLegeerklæring,
                    originalPass = kontrollnotat.originalPass,
                    gyldigPass = kontrollnotat.gyldigPass,
                    harVærtUtenlands = kontrollnotat.harVærtUtenlands,
                    utenlandsoppholdDatoer = kontrollnotat.utenlandsoppholdDatoer.map {
                        "${it.utreiseDato} - ${it.innreiseDato}"
                    },
                    harPlanerOmUtenlandsreise = kontrollnotat.harPlanerOmUtenlandsreise,
                    planlagteUtenlandsreiseDatoer = kontrollnotat.planlagteUtenlandsreiseDatoer.map {
                        "${it.utreiseDato} - ${it.innreiseDato}"
                    },
                    reiseDokumentasjon = kontrollnotat.reiseDokumentasjon,
                    økonomiskSituasjon = kontrollnotat.økonomiskSituasjon,
                    andreForhold = kontrollnotat.andreForhold,
                    skatteOpplysninger = kontrollnotat.skatteOpplysninger,
                    fritekst = kontrollnotat.fritekst,
                ),
            )
        }
    }
}

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
