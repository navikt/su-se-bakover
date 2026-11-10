package no.nav.su.se.bakover.domain.brev.jsonRequest

import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.InnvilgetSøknadsbehandlingPdfInnhold
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.brev.toBrev
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))
