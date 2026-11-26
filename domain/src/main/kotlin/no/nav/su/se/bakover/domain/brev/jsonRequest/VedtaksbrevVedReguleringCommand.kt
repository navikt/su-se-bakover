package no.nav.su.se.bakover.domain.brev.jsonRequest

import beregning.domain.Beregning
import dokument.domain.Brevtype
import dokument.domain.generer.Brevmal
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.Dokumenttype
import dokument.domain.generer.Felles
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.VedtakVedReguleringPdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyyFormatter
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.toBrev
import java.time.LocalDate

data class VedtaksbrevVedReguleringCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    val fraOgMed: LocalDate,
    override val saksbehandler: NavIdentBruker,
    override val attestant: NavIdentBruker.Attestant?,
    val beregning: Beregning,
    val satsoversikt: Satsoversikt,
) : BrevmalCommand {
    override val mal: Brevmal<VedtaksbrevVedReguleringCommand> get() = reguleringBrev
    override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
}

val reguleringBrev = Brevmal<VedtaksbrevVedReguleringCommand>(
    dokumenttype = Dokumenttype.VEDTAK,
    brevtype = Brevtype.VEDTAK,
) { felles, kommando ->
    kommando.fromBrevCommand(
        personalia = felles.personalia,
        saksbehandlerNavn = felles.saksbehandlerNavn,
        attestantNavn = felles.attestantNavn ?: "-",
    )
}

fun VedtaksbrevVedReguleringCommand.fromBrevCommand(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
    attestantNavn: String,
): VedtakVedReguleringPdfInnhold {
    return VedtakVedReguleringPdfInnhold(
        sakstype = sakstype,
        fraOgMed = fraOgMed.format(ddMMyyyyFormatter),
        alderEndretFraOgMed = fraOgMed.minusMonths(1).formatMonthYear(),
        personalia = personalia,
        saksbehandlerNavn = saksbehandlerNavn,
        beregningsperioder = LagBrevinnholdForBeregning(beregning).brevInnhold,
        satsoversikt = satsoversikt.toBrev(),
        attestantNavn = attestantNavn,
    )
}
