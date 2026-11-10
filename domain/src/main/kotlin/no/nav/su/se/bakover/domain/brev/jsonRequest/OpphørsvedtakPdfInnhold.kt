package no.nav.su.se.bakover.domain.brev.jsonRequest

import behandling.revurdering.domain.Opphørsgrunn
import behandling.revurdering.domain.getDistinkteParagrafer
import behandling.revurdering.domain.slåSammenForHøyInntektOgSuUnderMinstegrense
import com.fasterxml.jackson.annotation.JsonInclude
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.BeregningsperiodeBrev
import dokument.domain.generer.bygger.BrevPeriode
import dokument.domain.generer.bygger.harFradrag
import dokument.domain.generer.bygger.tilBrevperiode
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand

data class OpphørsvedtakPdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val opphørsgrunner: List<Opphørsgrunn>,
    val avslagsparagrafer: List<Int>,
    val harEktefelle: Boolean,
    val beregningsperioder: List<BeregningsperiodeBrev>,
    val saksbehandlerNavn: String,
    val attestantNavn: String,
    val fritekst: String?,
    val forventetInntektStørreEnn0: Boolean,
    val halvGrunnbeløp: Int?,
    val opphørsperiode: BrevPeriode,
    val satsoversikt: Satsoversikt,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Opphørsvedtak

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
                sakstype = command.sakstype,
                personalia = personalia,
                // Hvis både FOR_HØY_INNTEKT og SU_UNDER_MINSTEGRENSE, ønsker vi kun FOR_HØY_INNTEKT
                opphørsgrunner = command.opphørsgrunner.slåSammenForHøyInntektOgSuUnderMinstegrense(),
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
