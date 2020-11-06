package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

data class Søknad(
    val sakId: UUID,
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = now(),
    val søknadInnhold: SøknadInnhold,
    val lukket: Lukket? = null,
    val oppgaveId: OppgaveId?,
    val journalpostId: JournalpostId?
) {
    data class Lukket(
        val tidspunkt: Tidspunkt,
        val saksbehandler: String,
        val type: LukketType,
    )

    enum class LukketType(val value: String) {
        TRUKKET("TRUKKET"),
        BORTFALT("BORTFALT"),
        AVVIST("AVVIST")
    }
}
