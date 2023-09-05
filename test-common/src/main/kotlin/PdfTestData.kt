package no.nav.su.se.bakover.test

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagsPdfInnhold
import no.nav.su.se.bakover.client.pdf.ÅrsgrunnlagForPdf
import no.nav.su.se.bakover.client.pdf.ÅrsgrunnlagMedFnr
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.test.skatt.nySamletSkattegrunnlagForÅrOgStadieOppgjør
import java.util.UUID

fun pdfATom(): PdfA = PdfA("".toByteArray())

fun nySkattegrunnlagsPdfInnhold(): SkattegrunnlagsPdfInnhold {
    return SkattegrunnlagsPdfInnhold.lagSkattegrunnlagsPdf(
        saksnummer = saksnummer,
        hentet = fixedTidspunkt,
        søknadsbehandlingsId = UUID.randomUUID(),
        vedtaksId = UUID.randomUUID(),
        skatt = ÅrsgrunnlagForPdf(
            søkers = ÅrsgrunnlagMedFnr(
                fnr = fnr,
                årsgrunnlag = nonEmptyListOf(nySamletSkattegrunnlagForÅrOgStadieOppgjør()),
            ),
            eps = null,

        ),
        hentNavn = { Person.Navn("fornavn", "mellomnavn", "etternavn") },
        clock = fixedClock,
    )
}
