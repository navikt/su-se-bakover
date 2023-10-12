package no.nav.su.se.bakover.domain.brev.jsonRequest

import com.fasterxml.jackson.annotation.JsonInclude
import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.beregning.harAvkorting
import no.nav.su.se.bakover.domain.brev.beregning.harFradrag
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))
