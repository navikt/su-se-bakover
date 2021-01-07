package no.nav.su.se.bakover.domain.oppgave

import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Behandlingstema
import no.nav.su.se.bakover.domain.Behandlingstype
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Oppgavetype
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.util.UUID

sealed class OppgaveConfig {
    abstract val journalpostId: JournalpostId?
    abstract val søknadId: UUID
    abstract val aktørId: AktørId
    abstract val behandlingstema: Behandlingstema?
    abstract val oppgavetype: Oppgavetype
    abstract val behandlingstype: Behandlingstype
    abstract val tilordnetRessurs: NavIdentBruker?

    data class Saksbehandling(
        override val journalpostId: JournalpostId,
        override val søknadId: UUID,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker? = null
    ) : OppgaveConfig() {
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val behandlingstype = Behandlingstype.FØRSTEGANGSSØKNAD
        override val oppgavetype = Oppgavetype.BEHANDLE_SAK
    }

    data class Attestering(
        override val søknadId: UUID,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker? = null
    ) : OppgaveConfig() {
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val behandlingstype = Behandlingstype.FØRSTEGANGSSØKNAD
        override val oppgavetype = Oppgavetype.ATTESTERING
    }
}
