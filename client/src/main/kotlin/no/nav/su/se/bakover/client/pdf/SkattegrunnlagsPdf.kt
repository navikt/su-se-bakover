package no.nav.su.se.bakover.client.pdf

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.client.pdf.SamletÅrsgrunnlagPdfJson.Companion.tilPdfJson
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.brev.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.domain.brev.SkattegrunnlagPdfTemplate
import no.nav.su.se.bakover.domain.brev.jsonRequest.PdfInnhold
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagForÅrOgStadie
import java.time.Clock
import java.util.UUID

data class SkattegrunnlagsPdf private constructor(
    val saksnummer: Saksnummer,
    // TODO: Denne må vi ta inn når vi begynner med revurdering
    val behandlingstype: BehandlingstypeForSkattemelding = BehandlingstypeForSkattemelding.Søknadsbehandling,
    val behandlingsId: UUID,
    val vedtaksId: UUID,
    val hentet: Tidspunkt,
    val opprettet: Tidspunkt,
    val søkers: SkattPdfDataJson,
    val eps: SkattPdfDataJson?,
) : PdfInnhold() {
    override val pdfTemplate: PdfTemplateMedDokumentNavn = SkattegrunnlagPdfTemplate

    companion object {
        fun lagSkattegrunnlagsPdf(
            saksnummer: Saksnummer,
            søknadsbehandlingsId: UUID,
            vedtaksId: UUID,
            hentet: Tidspunkt,
            skatt: ÅrsgrunnlagForPdf,
            hentNavn: (Fnr) -> Person.Navn,
            clock: Clock,
        ): SkattegrunnlagsPdf = SkattegrunnlagsPdf(
            saksnummer = saksnummer,
            behandlingsId = søknadsbehandlingsId,
            vedtaksId = vedtaksId,
            hentet = hentet,
            opprettet = Tidspunkt.now(clock),
            søkers = SkattPdfDataJson(skatt.søkers.fnr, hentNavn(skatt.søkers.fnr), skatt.søkers.årsgrunnlag.tilPdfJson()),
            eps = skatt.eps?.let { SkattPdfDataJson(it.fnr, hentNavn(it.fnr), it.årsgrunnlag.tilPdfJson()) },
        )
    }
}

enum class BehandlingstypeForSkattemelding {
    Søknadsbehandling,
}

data class ÅrsgrunnlagMedFnr(
    val fnr: Fnr,
    val årsgrunnlag: NonEmptyList<SamletSkattegrunnlagForÅrOgStadie>,
)

data class ÅrsgrunnlagForPdf(
    val søkers: ÅrsgrunnlagMedFnr,
    val eps: ÅrsgrunnlagMedFnr?,
)
