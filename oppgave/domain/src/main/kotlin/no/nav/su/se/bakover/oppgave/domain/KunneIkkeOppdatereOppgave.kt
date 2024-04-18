package no.nav.su.se.bakover.oppgave.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt

sealed interface KunneIkkeOppdatereOppgave {
    data object FeilVedHentingAvOppgave : KunneIkkeOppdatereOppgave {
        override fun toSikkerloggString() = toString()
    }

    /**
     * @param jsonRequest ved GET vil den være null
     */
    data class OppgaveErFerdigstilt(
        val ferdigstiltTidspunkt: Tidspunkt,
        val ferdigstiltAv: NavIdentBruker.Saksbehandler,
        val jsonRequest: String?,
        val jsonResponse: String,
    ) : KunneIkkeOppdatereOppgave {
        override fun toSikkerloggString() = "OppgaveErFerdigstilt(ferdigstiltTidspunkt=$ferdigstiltTidspunkt,ferdigstiltAv=$ferdigstiltAv),jsonRequest=*****,jsonResponse=*****)"
        override fun toString() = "OppgaveErFerdigstilt(ferdigstiltTidspunkt=$ferdigstiltTidspunkt,ferdigstiltAv=$ferdigstiltAv),jsonRequest=$jsonRequest,jsonResponse=$jsonResponse)"
    }

    data object FeilVedRequest : KunneIkkeOppdatereOppgave {
        override fun toSikkerloggString() = toString()
    }
    data object FeilVedHentingAvToken : KunneIkkeOppdatereOppgave {
        override fun toSikkerloggString() = toString()
    }

    override fun toString(): String
    fun toSikkerloggString(): String
}
