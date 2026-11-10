package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.InnvilgetSøknadsbehandlingPdfInnhold
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.brev.toBrev
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
/*
data class InnvilgetSøknadsbehandlingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val fradato: String,
    val tildato: String,
    val søkedato: String,
    val forventetInntektStørreEnn0: Boolean,
    val harEktefelle: Boolean,
    val beregningsperioder: List<Beregningsperiode>,
    val saksbehandlerNavn: String,
    val attestantNavn: String,
    val fritekst: String,
    val satsoversikt: Satsoversikt,
    override val sakstype: Sakstype,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.InnvilgetVedtak

    @Suppress("unused")
    @JsonInclude
    val harFradrag: Boolean = beregningsperioder.harFradrag()

    companion object {
        fun fromBrevCommand(
            command: IverksettSøknadsbehandlingDokumentCommand.Innvilgelse,
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
            attestantNavn: String,
        ): InnvilgetSøknadsbehandlingPdfInnhold {
            val tildato = command.beregning.periode.tilOgMed
            return InnvilgetSøknadsbehandlingPdfInnhold(
                personalia = personalia,
                fradato = command.beregning.periode.fraOgMed.formatMonthYear(),
                tildato = tildato.formatMonthYear(),
                søkedato = tildato.minusMonths(1).formatMonthYear(),
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
*/

fun IverksettSøknadsbehandlingDokumentCommand.Innvilgelse.fromBrevCommand(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
    attestantNavn: String,
): InnvilgetSøknadsbehandlingPdfInnhold {
    val tildato = beregning.periode.tilOgMed
    return InnvilgetSøknadsbehandlingPdfInnhold(
        personalia = personalia,
        fradato = beregning.periode.fraOgMed.formatMonthYear(),
        tildato = tildato.formatMonthYear(),
        søkedato = tildato.minusMonths(1).formatMonthYear(),
        forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
        harEktefelle = harEktefelle,
        beregningsperioder = LagBrevinnholdForBeregning(beregning).brevInnhold,
        saksbehandlerNavn = saksbehandlerNavn,
        attestantNavn = attestantNavn,
        fritekst = fritekst,
        satsoversikt = satsoversikt.toBrev(),
        sakstype = sakstype,
    )
}

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))
