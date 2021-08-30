package no.nav.su.se.bakover.domain.oppgave

import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Behandlingstema
import no.nav.su.se.bakover.domain.Behandlingstype
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Oppgavetype
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.time.LocalDate
import java.util.UUID

sealed class OppgaveConfig {
    abstract val journalpostId: JournalpostId?
    abstract val saksreferanse: String
    abstract val aktørId: AktørId
    abstract val behandlingstema: Behandlingstema?
    abstract val oppgavetype: Oppgavetype
    abstract val behandlingstype: Behandlingstype
    abstract val tilordnetRessurs: NavIdentBruker?
    abstract val fristFerdigstillelse: LocalDate?

    data class Saksbehandling(
        override val journalpostId: JournalpostId,
        val søknadId: UUID,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker? = null,
    ) : OppgaveConfig() {
        override val saksreferanse = søknadId.toString()
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val behandlingstype = Behandlingstype.FØRSTEGANGSSØKNAD
        override val oppgavetype = Oppgavetype.BEHANDLE_SAK
        override val fristFerdigstillelse: LocalDate? = null
    }

    data class Attestering(
        val søknadId: UUID,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker? = null,
    ) : OppgaveConfig() {
        override val saksreferanse = søknadId.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val behandlingstype = Behandlingstype.FØRSTEGANGSSØKNAD
        override val oppgavetype = Oppgavetype.ATTESTERING
        override val fristFerdigstillelse: LocalDate? = null
    }

    data class Revurderingsbehandling(
        val saksnummer: Saksnummer,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker? = null
    ) : OppgaveConfig() {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.BEHANDLE_SAK
        override val fristFerdigstillelse: LocalDate? = null
    }

    data class AttesterRevurdering(
        val saksnummer: Saksnummer,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker? = null
    ) : OppgaveConfig() {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.ATTESTERING
        override val fristFerdigstillelse: LocalDate? = null
    }

    data class Personhendelse(
        val saksnummer: Saksnummer,
        val beskrivelse: String,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker? = null,
    ) : OppgaveConfig() {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
        override val fristFerdigstillelse: LocalDate = LocalDate.now().plusDays(7)
    }
}
