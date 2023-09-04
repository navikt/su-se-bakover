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
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.time.Clock
import java.util.UUID

data class SkattegrunnlagsPdfInnhold private constructor(
    val saksnummer: String?,
    val behandlingstype: BehandlingstypeForSkattemelding,
    val behandlingsId: UUID?,
    val vedtaksId: UUID?,
    val hentet: Tidspunkt,
    val opprettet: Tidspunkt,
    val søkers: SkattPdfDataJson,
    val eps: SkattPdfDataJson?,
    val begrunnelse: String?,
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
        ): SkattegrunnlagsPdfInnhold = SkattegrunnlagsPdfInnhold(
            saksnummer = saksnummer.toString(),
            behandlingstype = BehandlingstypeForSkattemelding.Søknadsbehandling,
            behandlingsId = søknadsbehandlingsId,
            vedtaksId = vedtaksId,
            hentet = hentet,
            opprettet = Tidspunkt.now(clock),
            søkers = SkattPdfDataJson(
                skatt.søkers.fnr,
                hentNavn(skatt.søkers.fnr),
                skatt.søkers.årsgrunnlag.tilPdfJson(),
            ),
            eps = skatt.eps?.let { SkattPdfDataJson(it.fnr, hentNavn(it.fnr), it.årsgrunnlag.tilPdfJson()) },
            begrunnelse = null,
        )

        fun Skattegrunnlag.lagPdfInnhold(
            begrunnelse: String?,
            navn: Person.Navn,
            clock: Clock,
        ): SkattegrunnlagsPdfInnhold = SkattegrunnlagsPdfInnhold(
            saksnummer = null,
            behandlingstype = BehandlingstypeForSkattemelding.Frioppslag,
            behandlingsId = null,
            vedtaksId = null,
            hentet = this.hentetTidspunkt,
            opprettet = Tidspunkt.now(clock),
            søkers = SkattPdfDataJson(
                fnr = this.fnr,
                navn = navn,
                årsgrunnlag = this.årsgrunnlag.tilPdfJson(),
            ),
            eps = null,
            begrunnelse = begrunnelse,
        )
    }
}

enum class BehandlingstypeForSkattemelding {
    Søknadsbehandling,
    Frioppslag,
}

data class ÅrsgrunnlagMedFnr(
    val fnr: Fnr,
    val årsgrunnlag: NonEmptyList<SamletSkattegrunnlagForÅrOgStadie>,
)

data class ÅrsgrunnlagForPdf(
    val søkers: ÅrsgrunnlagMedFnr,
    val eps: ÅrsgrunnlagMedFnr?,
)
