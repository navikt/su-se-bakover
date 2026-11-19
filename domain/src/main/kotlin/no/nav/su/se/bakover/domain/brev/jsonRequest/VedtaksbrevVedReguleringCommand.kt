package no.nav.su.se.bakover.domain.brev.jsonRequest

import beregning.domain.Beregning
import dokument.domain.GenererDokumentCommand
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
    val saksbehandler: NavIdentBruker,
    val attestant: NavIdentBruker.Attestant?,
    val beregning: Beregning,
    val satsoversikt: Satsoversikt,
) : GenererDokumentCommand

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
