package no.nav.su.se.bakover.domain.brev.jsonRequest

import com.fasterxml.jackson.annotation.JsonInclude
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.BeregningsperiodeBrev
import dokument.domain.generer.bygger.harFradrag
import dokument.domain.generer.bygger.maler.RevurderingAvInntektPdfInnholdNy
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.brev.toBrev

data class RevurderingAvInntektPdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val attestantNavn: String,
    val beregningsperioder: List<BeregningsperiodeBrev>,
    val fritekst: String?,
    val harEktefelle: Boolean,
    val forventetInntektStørreEnn0: Boolean,
    val satsoversikt: Satsoversikt,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Revurdering.Inntekt

    @Suppress("unused")
    @JsonInclude
    val harFradrag: Boolean = beregningsperioder.harFradrag()

    companion object {
        fun fromBrevCommand(
            command: IverksettRevurderingDokumentCommand.Inntekt,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
            attestantNavn: String,
        ): RevurderingAvInntektPdfInnhold {
            return RevurderingAvInntektPdfInnhold(
                sakstype = command.sakstype,
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

        fun RevurderingAvInntektPdfInnhold.tilPdfByggerInnhold() = RevurderingAvInntektPdfInnholdNy(
            sakstype = sakstype,
            personalia = personalia,
            saksbehandlerNavn = saksbehandlerNavn,
            attestantNavn = attestantNavn,
            beregningsperioder = beregningsperioder,
            fritekst = fritekst,
            harEktefelle = harEktefelle,
            forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
            satsoversikt = satsoversikt.toBrev(),
            harFradrag = harFradrag,
        )
    }
}
