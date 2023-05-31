package no.nav.su.se.bakover.client.pdf

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.client.pdf.SkattPdfData.ÅrsgrunnlagPdfJson.Companion.tilPdfJson
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.brev.PdfInnhold
import no.nav.su.se.bakover.domain.brev.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.domain.brev.SkattegrunnlagPdfTemplate
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.Saksnummer
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
    val søkers: SkattPdfData?,
    val eps: SkattPdfData?,
) : PdfInnhold() {
    override val pdfTemplate: PdfTemplateMedDokumentNavn = SkattegrunnlagPdfTemplate

    companion object {
        fun lagSkattemeldingsPdf(
            saksnummer: Saksnummer,
            søknadsbehandlingsId: UUID,
            vedtaksId: UUID,
            hentet: Tidspunkt,
            skatt: ÅrsgrunnlagForPdf,
            hentNavn: (Fnr) -> Person.Navn,
            clock: Clock,
        ): SkattegrunnlagsPdf {
            return SkattegrunnlagsPdf(
                saksnummer = saksnummer,
                behandlingsId = søknadsbehandlingsId,
                vedtaksId = vedtaksId,
                hentet = hentet,
                opprettet = Tidspunkt.now(clock),
                søkers = skatt.søkers?.let {
                    SkattPdfData(it.fnr, hentNavn(it.fnr), it.årsgrunnlag.tilPdfJson(vedtaksId))
                },
                eps = skatt.eps?.let { SkattPdfData(it.fnr, hentNavn(it.fnr), it.årsgrunnlag.tilPdfJson(vedtaksId)) },
            )
        }
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
    val søkers: ÅrsgrunnlagMedFnr?,
    val eps: ÅrsgrunnlagMedFnr?,
) {
    init {
        require(søkers != null || eps != null) {
            "Både søkers & eps var null. Minst et av dem må suppleres"
        }
    }
}
