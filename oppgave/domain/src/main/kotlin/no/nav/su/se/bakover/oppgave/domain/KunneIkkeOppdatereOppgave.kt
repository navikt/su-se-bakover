package no.nav.su.se.bakover.oppgave.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt

sealed interface KunneIkkeOppdatereOppgave {
    data object FeilVedHentingAvOppgave : KunneIkkeOppdatereOppgave

    /**
     * @param jsonRequest ved GET vil den være null
     */
    data class OppgaveErFerdigstilt(
        val ferdigstiltTidspunkt: Tidspunkt,
        val ferdigstiltAv: NavIdentBruker.Saksbehandler,
        val jsonRequest: String?,
        val jsonResponse: String,
    ) : KunneIkkeOppdatereOppgave

    data object FeilVedRequest : KunneIkkeOppdatereOppgave
    data object FeilVedHentingAvToken : KunneIkkeOppdatereOppgave
}
