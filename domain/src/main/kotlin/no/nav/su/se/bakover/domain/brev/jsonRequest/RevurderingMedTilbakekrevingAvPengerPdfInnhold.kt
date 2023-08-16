package no.nav.su.se.bakover.domain.brev.jsonRequest

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.domain.brev.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.BrevTilbakekrevingInfo
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand

data class RevurderingMedTilbakekrevingAvPengerPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val attestantNavn: String,
    val beregningsperioder: List<Beregningsperiode>,
    val fritekst: String,
    val harEktefelle: Boolean,
    val forventetInntektStørreEnn0: Boolean,
    val tilbakekreving: List<BrevTilbakekrevingInfo>,
    val periodeStart: String,
    val periodeSlutt: String,
    val satsoversikt: Satsoversikt,
) : PdfInnhold() {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.Revurdering.MedTilbakekreving

    @Suppress("unused")
    @JsonInclude
    val harFradrag: Boolean = beregningsperioder.harFradrag()

    @Suppress("unused")
    @JsonInclude
    val harAvkorting: Boolean = beregningsperioder.harAvkorting()

    companion object {
        fun fromBrevCommand(
            command: IverksettRevurderingDokumentCommand.TilbakekrevingAvPenger,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
            attestantNavn: String,
        ): RevurderingMedTilbakekrevingAvPengerPdfInnhold {
            return RevurderingMedTilbakekrevingAvPengerPdfInnhold(
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                beregningsperioder = LagBrevinnholdForBeregning(command.beregning).brevInnhold,
                fritekst = command.fritekst,
                harEktefelle = command.harEktefelle,
                forventetInntektStørreEnn0 = command.forventetInntektStørreEnn0,
                tilbakekreving = command.tilbakekreving.tilbakekrevingavdrag,
                periodeStart = command.tilbakekreving.periodeStart,
                periodeSlutt = command.tilbakekreving.periodeSlutt,
                satsoversikt = command.satsoversikt,
            )
        }
    }
}
