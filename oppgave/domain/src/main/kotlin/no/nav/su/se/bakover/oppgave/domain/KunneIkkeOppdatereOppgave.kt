package no.nav.su.se.bakover.oppgave.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt

sealed interface KunneIkkeOppdatereOppgave {
    data object FeilVedHentingAvOppgave : KunneIkkeOppdatereOppgave
    data class OppgaveErFerdigstilt(
        val ferdigstiltTidspunkt: Tidspunkt,
        val ferdigstiltAv: NavIdentBruker.Saksbehandler,
    ) : KunneIkkeOppdatereOppgave

    data object FeilVedRequest : KunneIkkeOppdatereOppgave
    data object FeilVedHentingAvToken : KunneIkkeOppdatereOppgave
}
