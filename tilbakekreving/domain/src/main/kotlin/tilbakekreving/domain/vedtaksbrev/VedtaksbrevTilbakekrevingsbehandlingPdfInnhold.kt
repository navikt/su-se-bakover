package tilbakekreving.domain.vedtaksbrev

import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.toBrevformat
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import tilbakekreving.domain.vedtaksbrev.MånedsoversiktMedSum.Companion.månedsoversiktMedSum
import tilbakekreving.domain.vurdering.PeriodevurderingMedKrav
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import java.time.Clock
import java.time.LocalDate
import javax.jms.IllegalStateException

sealed interface VedtaksbrevTilbakekrevingsbehandlingPdfInnhold : PdfInnhold {

    data class Vanlig(
        override val sakstype: Sakstype,
        val personalia: PersonaliaPdfInnhold,
        val saksbehandlerNavn: String,
        val attestantNavn: String?,
        val fritekst: String?,
        val dato: String,
        val månedsoversiktMedSum: MånedsoversiktMedSum,
        val skalTilbakekreve: Boolean,
        /**
         * Summen av brutto beløp som skal ikke tilbakekreves. Dette vises dersom `skalTilbakekreve` er false.
         */
        val bruttoSkalIkkeTilbakekreveSummert: Int,
    ) : VedtaksbrevTilbakekrevingsbehandlingPdfInnhold {
        override val pdfTemplate = PdfTemplateMedDokumentNavn.VedtaksbrevTilbakekrevingsbehandling
    }

    data class Dødsbo(
        override val sakstype: Sakstype,
        val personalia: PersonaliaPdfInnhold,
        val saksbehandlerNavn: String,
        val attestantNavn: String?,
        val fritekst: String?,
        val dato: String,
        val månedsoversiktMedSum: MånedsoversiktMedSum,
        val skalTilbakekreve: Boolean,
        /**
         * Summen av brutto beløp som skal ikke tilbakekreves. Dette vises dersom `skalTilbakekreve` er false.
         */
        val bruttoSkalIkkeTilbakekreveSummert: Int,
        val fraOgMed: String,
        val tilOgMed: String,
        val varselDato: String,
    ) : VedtaksbrevTilbakekrevingsbehandlingPdfInnhold {
        override val pdfTemplate = PdfTemplateMedDokumentNavn.VedtaksbrevTilbakekrevingsbehandlingDødsbo
    }

    companion object {
        fun fromBrevCommand(
            command: VedtaksbrevTilbakekrevingsbehandlingDokumentCommand,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
            attestantNavn: String?,
            clock: Clock,
        ): VedtaksbrevTilbakekrevingsbehandlingPdfInnhold {
            return if (command.dødsbo) {
                // val kravgrunnlagPeriode = command.kr?: throw IllegalStateException("Kan ikke forhåndsvarsle tilbakekreving dødsbo uten kravgrunnlag.")
                val forhåndsvarsel = command.forhåndsvarselsInfo.maxByOrNull { it.hendelsestidspunkt } ?: throw IllegalStateException("Kan ikke forhåndsvarsle tilbakekreving dødsbo uten forhåndsvarsel.")
                Dødsbo(
                    sakstype = command.sakstype,
                    personalia = personalia,
                    saksbehandlerNavn = saksbehandlerNavn,
                    attestantNavn = attestantNavn,
                    fritekst = command.fritekst,
                    // Denne formateres annerledes enn i personalia, selvom begge deler er dagens dato. 2021-01-01 vil gi 01.01.2021 i personalia, mens 1. januar 2021 i dette feltet.
                    // TODO jah: Kanskje vi kan bruke denne i su-pdfgen? https://github.com/navikt/pdfgen/blob/master/src/main/kotlin/no/nav/pdfgen/template/Helpers.kt
                    dato = LocalDate.now(clock).toBrevformat(),
                    månedsoversiktMedSum = command.vurderingerMedKrav.månedsoversiktMedSum(),
                    skalTilbakekreve = command.skalTilbakekreve,
                    bruttoSkalIkkeTilbakekreveSummert = command.vurderingerMedKrav.bruttoSkalIkkeTilbakekreveSummert,
                    fraOgMed = command.periode.fraOgMed.toString(),
                    tilOgMed = command.periode.tilOgMed.toString(),
                    varselDato = forhåndsvarsel.hendelsestidspunkt.toLocalDate(zoneIdOslo).toString(),
                )
            } else {
                Vanlig(
                    sakstype = command.sakstype,
                    personalia = personalia,
                    saksbehandlerNavn = saksbehandlerNavn,
                    attestantNavn = attestantNavn,
                    fritekst = command.fritekst,
                    // Denne formateres annerledes enn i personalia, selvom begge deler er dagens dato. 2021-01-01 vil gi 01.01.2021 i personalia, mens 1. januar 2021 i dette feltet.
                    // TODO jah: Kanskje vi kan bruke denne i su-pdfgen? https://github.com/navikt/pdfgen/blob/master/src/main/kotlin/no/nav/pdfgen/template/Helpers.kt
                    dato = LocalDate.now(clock).toBrevformat(),
                    månedsoversiktMedSum = command.vurderingerMedKrav.månedsoversiktMedSum(),
                    skalTilbakekreve = command.skalTilbakekreve,
                    bruttoSkalIkkeTilbakekreveSummert = command.vurderingerMedKrav.bruttoSkalIkkeTilbakekreveSummert,
                )
            }
        }
    }
}

data class MånedsoversiktMedSum private constructor(
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

enum class Vurdering {
    SkalTilbakekreve,
    SkalIkkeTilbakekreve,
}

data class EnkeltMånedsoversikt(
    val periode: String,
    val vurdering: Vurdering,
    val bruttoSkalTilbakekreve: Int,
    val nettoSkalTilbakekreve: Int,
)
