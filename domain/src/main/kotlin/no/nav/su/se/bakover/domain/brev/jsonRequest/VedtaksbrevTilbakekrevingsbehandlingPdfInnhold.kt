package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.extensions.toBrevformat
import no.nav.su.se.bakover.domain.brev.jsonRequest.MånedsoversiktMedSum.Companion.månedsoversiktMedSum
import tilbakekreving.domain.forhåndsvarsel.VedtaksbrevTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.vurdert.PeriodevurderingMedKrav
import tilbakekreving.domain.vurdert.VurderingerMedKrav
import java.time.Clock
import java.time.LocalDate

internal data class VedtaksbrevTilbakekrevingsbehandlingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val attestantNavn: String?,
    val fritekst: String?,
    val dato: String,
    val månedsoversiktMedSum: MånedsoversiktMedSum,
) : PdfInnhold() {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.VedtaksbrevTilbakekrevingsbehandling

    companion object {
        fun fromBrevCommand(
            command: VedtaksbrevTilbakekrevingsbehandlingDokumentCommand,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
            attestantNavn: String?,
            clock: Clock,
        ): VedtaksbrevTilbakekrevingsbehandlingPdfInnhold {
            return VedtaksbrevTilbakekrevingsbehandlingPdfInnhold(
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                fritekst = command.fritekst,
                // Denne formateres annerledes enn i personalia, selvom begge deler er dagens dato. 2021-01-01 vil gi 01.01.2021 i personalia, mens 1. januar 2021 i dette feltet.
                // TODO jah: Kanskje vi kan bruke denne i su-pdfgen? https://github.com/navikt/pdfgen/blob/master/src/main/kotlin/no/nav/pdfgen/template/Helpers.kt
                dato = LocalDate.now(clock).toBrevformat(),
                månedsoversiktMedSum = command.vurderingerMedKrav.månedsoversiktMedSum(),
            )
        }
    }
}

internal data class MånedsoversiktMedSum private constructor(
    val sorterteMåneder: List<EnkeltMånedsoversikt>,
    val sumBruttoSkalTilbakekreve: Int,
    val sumNettoSkalTilbakekreve: Int,
) {
    companion object {
        fun VurderingerMedKrav.månedsoversiktMedSum(): MånedsoversiktMedSum {
            return MånedsoversiktMedSum(
                sorterteMåneder = this.perioder.map {
                    EnkeltMånedsoversikt(
                        periode = it.periode.ddMMyyyy(),
                        vurdering = when (it) {
                            is PeriodevurderingMedKrav.SkalIkkeTilbakekreve -> Vurdering.SkalIkkeTilbakekreve
                            is PeriodevurderingMedKrav.SkalTilbakekreve -> Vurdering.SkalTilbakekreve
                        },
                        bruttoSkalTilbakekreve = it.bruttoSkalTilbakekreve,
                        nettoSkalTilbakekreve = it.nettoSkalTilbakekreve,
                    )
                },
                sumBruttoSkalTilbakekreve = this.bruttoSkalTilbakekreveSummert,
                sumNettoSkalTilbakekreve = this.nettoSkalTilbakekreveSummert,
            )
        }
    }
}

internal enum class Vurdering {
    SkalTilbakekreve,
    SkalIkkeTilbakekreve,
}

internal data class EnkeltMånedsoversikt(
    val periode: String,
    val vurdering: Vurdering,
    val bruttoSkalTilbakekreve: Int,
    val nettoSkalTilbakekreve: Int,
)
