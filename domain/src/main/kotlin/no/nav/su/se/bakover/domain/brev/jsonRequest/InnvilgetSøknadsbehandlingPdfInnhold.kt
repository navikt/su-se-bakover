package no.nav.su.se.bakover.domain.brev.jsonRequest

import com.fasterxml.jackson.annotation.JsonInclude
import no.nav.su.se.bakover.domain.brev.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.sak.Sakstype

data class InnvilgetSøknadsbehandlingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val fradato: String,
    val tildato: String,
    val forventetInntektStørreEnn0: Boolean,
    val harEktefelle: Boolean,
    val beregningsperioder: List<Beregningsperiode>,
    val saksbehandlerNavn: String,
    val attestantNavn: String,
    val fritekst: String,
    val satsoversikt: Satsoversikt,
    override val sakstype: Sakstype,
) : PdfInnhold() {
    override val pdfTemplate: PdfTemplateMedDokumentNavn = PdfTemplateMedDokumentNavn.InnvilgetVedtak

    @Suppress("unused")
    @JsonInclude
    val harFradrag: Boolean = beregningsperioder.harFradrag()

    @Suppress("unused")
    @JsonInclude
    val harAvkorting: Boolean = beregningsperioder.harAvkorting()

    companion object {
        fun fromBrevCommand(
            command: IverksettSøknadsbehandlingDokumentCommand.Innvilgelse,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
            attestantNavn: String,
        ): InnvilgetSøknadsbehandlingPdfInnhold {
            return InnvilgetSøknadsbehandlingPdfInnhold(
                personalia = personalia,
                fradato = command.beregning.periode.fraOgMed.formatMonthYear(),
                tildato = command.beregning.periode.tilOgMed.formatMonthYear(),
                forventetInntektStørreEnn0 = command.forventetInntektStørreEnn0,
                harEktefelle = command.harEktefelle,
                beregningsperioder = LagBrevinnholdForBeregning(command.beregning).brevInnhold,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                fritekst = command.fritekst,
                satsoversikt = command.satsoversikt,
                sakstype = command.sakstype,
            )
        }
    }
}
