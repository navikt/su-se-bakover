package no.nav.su.se.bakover.domain.brev.jsonRequest

import com.fasterxml.jackson.annotation.JsonInclude
import dokument.domain.pdf.PdfInnhold
import dokument.domain.pdf.PdfTemplateMedDokumentNavn
import dokument.domain.pdf.PersonaliaPdfInnhold
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.behandling.avslag.getDistinkteParagrafer
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.brev.beregning.BrevPeriode
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.beregning.harFradrag
import no.nav.su.se.bakover.domain.brev.beregning.tilBrevperiode
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand

data class OpphørsvedtakPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val opphørsgrunner: List<Opphørsgrunn>,
    val avslagsparagrafer: List<Int>,
    val harEktefelle: Boolean,
    val beregningsperioder: List<Beregningsperiode>,
    val saksbehandlerNavn: String,
    val attestantNavn: String,
    val fritekst: String?,
    val forventetInntektStørreEnn0: Boolean,
    val halvGrunnbeløp: Int?,
    val opphørsperiode: BrevPeriode,
    val satsoversikt: Satsoversikt,
) : PdfInnhold {
    override val pdfTemplate: PdfTemplateMedDokumentNavn = PdfTemplateMedDokumentNavn.Opphør.Opphørsvedtak

    @Suppress("unused")
    @JsonInclude
    val harFradrag: Boolean = beregningsperioder.harFradrag()

    companion object {
        fun fromBrevCommand(
            command: IverksettRevurderingDokumentCommand.Opphør,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
            attestantNavn: String,
        ): OpphørsvedtakPdfInnhold {
            return OpphørsvedtakPdfInnhold(
                personalia = personalia,
                opphørsgrunner = command.opphørsgrunner,
                avslagsparagrafer = command.opphørsgrunner.getDistinkteParagrafer(),
                harEktefelle = command.harEktefelle,
                beregningsperioder = if (
                    command.opphørsgrunner.contains(Opphørsgrunn.FOR_HØY_INNTEKT) ||
                    command.opphørsgrunner.contains(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
                ) {
                    LagBrevinnholdForBeregning(command.beregning).brevInnhold
                } else {
                    emptyList()
                },
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                fritekst = command.fritekst,
                forventetInntektStørreEnn0 = command.forventetInntektStørreEnn0,
                halvGrunnbeløp = command.halvtGrunnbeløp,
                opphørsperiode = command.opphørsperiode.tilBrevperiode(),
                satsoversikt = command.satsoversikt,
            )
        }
    }
}
