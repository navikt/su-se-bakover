package no.nav.su.se.bakover.domain.brev.jsonRequest

import behandling.revurdering.domain.Opphørsgrunn
import behandling.revurdering.domain.getDistinkteParagrafer
import behandling.revurdering.domain.slåSammenForHøyInntektOgSuUnderMinstegrense
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.harFradrag
import dokument.domain.generer.bygger.maler.OpphørsgrunnBrev
import dokument.domain.generer.bygger.maler.OpphørsvedtakPdfInnhold
import dokument.domain.generer.bygger.tilBrevperiode
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.brev.toBrev
import no.nav.su.se.bakover.domain.historisk.revurdering.brev.HistoriskInfotrygdRevurderingDokumentCommand

fun IverksettRevurderingDokumentCommand.Opphør.fromBrevCommand(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
    attestantNavn: String,
): OpphørsvedtakPdfInnhold {
    val command = this

    val beregningsperioder = if (
        command.opphørsgrunner.contains(Opphørsgrunn.FOR_HØY_INNTEKT) ||
        command.opphørsgrunner.contains(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
    ) {
        LagBrevinnholdForBeregning(command.beregning).brevInnhold
    } else {
        emptyList()
    }

    return OpphørsvedtakPdfInnhold(

        sakstype = command.sakstype,
        personalia = personalia,
        // Hvis både FOR_HØY_INNTEKT og SU_UNDER_MINSTEGRENSE, ønsker vi kun FOR_HØY_INNTEKT
        opphørsgrunner = command.opphørsgrunner.slåSammenForHøyInntektOgSuUnderMinstegrense().map { opphørsgrunn -> opphørsgrunn.tilBrev() },
        avslagsparagrafer = command.opphørsgrunner.getDistinkteParagrafer(),
        harEktefelle = command.harEktefelle,
        beregningsperioder = beregningsperioder,
        harFradrag = beregningsperioder.harFradrag(),
        saksbehandlerNavn = saksbehandlerNavn,
        attestantNavn = attestantNavn,
        fritekst = command.fritekst,
        forventetInntektStørreEnn0 = command.forventetInntektStørreEnn0,
        halvGrunnbeløp = command.halvtGrunnbeløp,
        opphørsperiode = command.opphørsperiode.tilBrevperiode(),
        satsoversikt = command.satsoversikt.toBrev(),
    )
}

fun HistoriskInfotrygdRevurderingDokumentCommand.Opphør.fromBrevCommand(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
    attestantNavn: String,
): OpphørsvedtakPdfInnhold {
    val command = this

    val beregningsperioder = if (
        command.opphørsgrunner.contains(Opphørsgrunn.FOR_HØY_INNTEKT) ||
        command.opphørsgrunner.contains(Opphørsgrunn.SU_UNDER_MINSTEGRENSE)
    ) {
        command.beregningsperioder
    } else {
        emptyList()
    }

    return OpphørsvedtakPdfInnhold(
        sakstype = command.sakstype,
        personalia = personalia,
        opphørsgrunner = command.opphørsgrunner.slåSammenForHøyInntektOgSuUnderMinstegrense().map { opphørsgrunn -> opphørsgrunn.tilBrev() },
        avslagsparagrafer = command.opphørsgrunner.getDistinkteParagrafer(),
        harEktefelle = command.harEktefelle,
        beregningsperioder = beregningsperioder,
        harFradrag = beregningsperioder.harFradrag(),
        saksbehandlerNavn = saksbehandlerNavn,
        attestantNavn = attestantNavn,
        fritekst = command.fritekst,
        forventetInntektStørreEnn0 = false,
        halvGrunnbeløp = command.halvtGrunnbeløp,
        opphørsperiode = command.opphørsperiode.tilBrevperiode(),
        satsoversikt = command.satsoversikt.toBrev(),
    )
}

fun Opphørsgrunn.tilBrev(): OpphørsgrunnBrev {
    return when (this) {
        Opphørsgrunn.FOR_HØY_INNTEKT -> OpphørsgrunnBrev.FOR_HØY_INNTEKT
        Opphørsgrunn.SU_UNDER_MINSTEGRENSE -> OpphørsgrunnBrev.SU_UNDER_MINSTEGRENSE
        Opphørsgrunn.UTENLANDSOPPHOLD -> OpphørsgrunnBrev.UTENLANDSOPPHOLD
        Opphørsgrunn.MANGLENDE_DOKUMENTASJON -> OpphørsgrunnBrev.MANGLENDE_DOKUMENTASJON
        Opphørsgrunn.OPPHOLDSTILLATELSE -> OpphørsgrunnBrev.OPPHOLDSTILLATELSE
        Opphørsgrunn.FLYKTNING -> OpphørsgrunnBrev.FLYKTNING
        Opphørsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE -> OpphørsgrunnBrev.BOR_OG_OPPHOLDER_SEG_I_NORGE
        Opphørsgrunn.PERSONLIG_OPPMØTE -> OpphørsgrunnBrev.PERSONLIG_OPPMØTE
        Opphørsgrunn.INNLAGT_PÅ_INSTITUSJON -> OpphørsgrunnBrev.INNLAGT_PÅ_INSTITUSJON
        Opphørsgrunn.ALDERSPENSJON -> OpphørsgrunnBrev.ALDERSPENSJON
        Opphørsgrunn.FAMILIEGJENFORENING -> OpphørsgrunnBrev.FAMILIEGJENFORENING
        Opphørsgrunn.UFØRHET -> OpphørsgrunnBrev.UFØRHET
        Opphørsgrunn.FORMUE -> OpphørsgrunnBrev.FORMUE
    }
}
