package no.nav.su.se.bakover.test

import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.client.pdf.SkattegrunnlagsPdfInnhold
import no.nav.su.se.bakover.client.pdf.ÅrsgrunnlagForPdf
import no.nav.su.se.bakover.client.pdf.ÅrsgrunnlagMedFnr
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.test.skatt.nySamletSkattegrunnlagForÅrOgStadieOppgjør
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import person.domain.Person
import vilkår.skatt.domain.Skattegrunnlag
import java.time.Clock
import java.util.UUID

fun pdfATom(): PdfA = PdfA("".toByteArray())

fun nySkattegrunnlagsPdfInnhold(): SkattegrunnlagsPdfInnhold {
    return SkattegrunnlagsPdfInnhold.lagSkattegrunnlagsPdf(
        saksnummer = saksnummer,
        hentet = fixedTidspunkt,
        søknadsbehandlingId = SøknadsbehandlingId.generer(),
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

fun nySkattegrunnlagsPdfInnholdForFrioppslag(
    fagsystemId: String = "12AB34",
    sakstype: Sakstype = Sakstype.ALDER,
    begrunnelse: String? = "Dette er en begrunnelse",
    skattegrunnlagSøker: Skattegrunnlag? = nySkattegrunnlag(),
    skattegrunnlagEps: Skattegrunnlag? = null,
    navn: Person.Navn = Person.Navn("Sir Gideon ofnir", "THE ALL", "KNOWING"),
    clock: Clock = fixedClock,
): SkattegrunnlagsPdfInnhold {
    return SkattegrunnlagsPdfInnhold.lagSkattegrunnlagsPdfInnholdFraFrioppslag(
        fagsystemId = fagsystemId,
        sakstype = sakstype,
        begrunnelse = begrunnelse,
        skattegrunnlagSøker = skattegrunnlagSøker,
        skattegrunnlagEps = skattegrunnlagEps,
        hentNavn = { _ -> navn.right() },
        clock = clock,
    ).getOrFail()
}
