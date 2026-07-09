package no.nav.su.se.bakover.domain.kontrollnotat

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
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
    private val clock: Clock,

    val dagensDatoOgTidspunkt: String = LocalDateTime.now(clock)
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
    val kontrollnotat: KontrollnotatInnhold,
) : PdfInnhold {
    override val pdfTemplate: PdfTemplateMedDokumentNavn =
        PdfTemplateMedDokumentNavn.Kontrollnotat

    companion object {
        fun create(
            saksnummer: Saksnummer,
            sakstype: Sakstype,
            navn: Person.Navn,
            kontrollnotat: KontrollnotatInnhold,
            clock: Clock,
        ): KontrollnotatPdfInnhold = KontrollnotatPdfInnhold(
            saksnummer = saksnummer,
            sakstype = sakstype,
            navn = navn,
            kontrollnotat = kontrollnotat,
            dagensDatoOgTidspunkt = LocalDateTime.now(clock.withZone(zoneIdOslo))
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
            clock = clock,
        )
    }
}
