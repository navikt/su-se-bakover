package no.nav.su.se.bakover.domain.brev.jsonRequest

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.domain.brev.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand

data class RevurderingAvInntektPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val attestantNavn: String,
    val beregningsperioder: List<Beregningsperiode>,
    val fritekst: String,
    val harEktefelle: Boolean,
    val forventetInntektStørreEnn0: Boolean,
    val satsoversikt: Satsoversikt,
) : PdfInnhold() {
    override val pdfTemplate = PdfTemplateMedDokumentNavn.Revurdering.Inntekt

    @Suppress("unused")
    @JsonInclude
    val harFradrag: Boolean = beregningsperioder.harFradrag()

    @Suppress("unused")
    @JsonInclude
    val harAvkorting: Boolean = beregningsperioder.harAvkorting()

    companion object {
        fun fromBrevCommand(
            command: IverksettRevurderingDokumentCommand.Inntekt,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
            attestantNavn: String,
        ): RevurderingAvInntektPdfInnhold {
            return RevurderingAvInntektPdfInnhold(
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                beregningsperioder = LagBrevinnholdForBeregning(command.beregning).brevInnhold,
                fritekst = command.fritekst,
                harEktefelle = command.harEktefelle,
                forventetInntektStørreEnn0 = command.forventetInntektStørreEnn0,
                satsoversikt = command.satsoversikt,
            )
        }
    }
}
