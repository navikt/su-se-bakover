package no.nav.su.se.bakover.domain.søknad

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
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

data class SøknadPdfInnhold private constructor(
    override val sakstype: Sakstype,
    val saksnummer: Saksnummer,
    val søknadsId: UUID,
    val navn: Person.Navn,
    private val clock: Clock,
    val dagensDatoOgTidspunkt: String = LocalDateTime.now(clock)
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
    val søknadOpprettet: String,
    val søknadInnhold: SøknadInnhold,
) : PdfInnhold {

    override val pdfTemplate: PdfTemplateMedDokumentNavn = PdfTemplateMedDokumentNavn.Søknad

    companion object {
        fun create(
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
    }
}
