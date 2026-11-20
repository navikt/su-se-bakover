package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.harFradrag
import dokument.domain.generer.bygger.maler.RevurderingAvInntektPdfInnhold
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.brev.toBrev
import no.nav.su.se.bakover.domain.historisk.revurdering.brev.HistoriskInfotrygdRevurderingDokumentCommand

fun IverksettRevurderingDokumentCommand.Inntekt.fromBrevCommand(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
    attestantNavn: String,
): RevurderingAvInntektPdfInnhold {
    val command = this
    val beregningsperioder = LagBrevinnholdForBeregning(command.beregning).brevInnhold

    return RevurderingAvInntektPdfInnhold(
        sakstype = command.sakstype,
        personalia = personalia,
        saksbehandlerNavn = saksbehandlerNavn,
        attestantNavn = attestantNavn,
        beregningsperioder = beregningsperioder,
        fritekst = command.fritekst,
        harEktefelle = command.harEktefelle,
        forventetInntektStørreEnn0 = command.forventetInntektStørreEnn0,
        satsoversikt = command.satsoversikt.toBrev(),
        harFradrag = beregningsperioder.harFradrag(),
    )
}

fun HistoriskInfotrygdRevurderingDokumentCommand.Inntekt.fromBrevCommand(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
    attestantNavn: String,
): RevurderingAvInntektPdfInnhold {
    val command = this

    return RevurderingAvInntektPdfInnhold(
        sakstype = command.sakstype,
        personalia = personalia,
        saksbehandlerNavn = saksbehandlerNavn,
        attestantNavn = attestantNavn,
        beregningsperioder = command.beregningsperioder,
        fritekst = command.fritekst,
        harEktefelle = command.harEktefelle,
        forventetInntektStørreEnn0 = false,
        satsoversikt = command.satsoversikt.toBrev(),
        harFradrag = command.beregningsperioder.harFradrag(),
    )
}
