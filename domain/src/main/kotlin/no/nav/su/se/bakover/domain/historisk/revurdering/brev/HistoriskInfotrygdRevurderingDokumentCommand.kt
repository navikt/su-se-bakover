package no.nav.su.se.bakover.domain.historisk.revurdering.brev

import behandling.revurdering.domain.Opphørsgrunn
import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.Beregningsperiode

/**
 * Vedtaksbrev for historisk Infotrygd-revurdering. Holdes adskilt fra IverksettRevurderingDokumentCommand,
 * fordi historiske månedsresultater ikke er en ordinær Beregning og ordinær brevflyt ikke skal endres.
 */
sealed interface HistoriskInfotrygdRevurderingDokumentCommand : GenererDokumentCommand {
    val saksbehandler: NavIdentBruker.Saksbehandler
    val attestant: NavIdentBruker.Attestant?
    val beregningsperioder: List<Beregningsperiode>
    val fritekst: String
    val harEktefelle: Boolean
    val satsoversikt: Satsoversikt

    data class Inntekt(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant?,
        override val beregningsperioder: List<Beregningsperiode>,
        override val fritekst: String,
        override val harEktefelle: Boolean,
        override val satsoversikt: Satsoversikt,
    ) : HistoriskInfotrygdRevurderingDokumentCommand {
        override val sakstype: Sakstype = Sakstype.ALDER
    }

    data class Opphør(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant?,
        override val beregningsperioder: List<Beregningsperiode>,
        override val fritekst: String,
        override val harEktefelle: Boolean,
        override val satsoversikt: Satsoversikt,
        val opphørsgrunner: List<Opphørsgrunn>,
        val opphørsperiode: Periode,
        val halvtGrunnbeløp: Int,
    ) : HistoriskInfotrygdRevurderingDokumentCommand {
        override val sakstype: Sakstype = Sakstype.ALDER
    }
}
