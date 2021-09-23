package no.nav.su.se.bakover.domain.oppgave

import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Behandlingstema
import no.nav.su.se.bakover.domain.Behandlingstype
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Oppgavetype
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadstype
import java.time.Clock
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
    abstract val clock: Clock
    abstract val aktivDato: LocalDate
    abstract val fristFerdigstillelse: LocalDate

    /**
     * Denne er knyttet til mottak av søknad (både førstegang og ny periode), men brukes videre av søknadsbehandlinga
     */
    data class NySøknad(
        override val journalpostId: JournalpostId,
        val søknadId: UUID,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker? = null,
        override val clock: Clock = Clock.systemUTC(),
        val søknadstype: Søknadstype,
    ) : OppgaveConfig() {
        override val saksreferanse = søknadId.toString()
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val oppgavetype = Oppgavetype.BEHANDLE_SAK
        override val behandlingstype = when (søknadstype) {
            Søknadstype.FØRSTEGANGSSØKNAD -> Behandlingstype.FØRSTEGANGSSØKNAD
            Søknadstype.NY_PERIODE -> Behandlingstype.NY_PERIODE
        }
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(30)
    }

    data class AttesterSøknadsbehandling(
        val søknadId: UUID,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker? = null,
        override val clock: Clock = Clock.systemUTC(),
        val søknadstype: Søknadstype,
    ) : OppgaveConfig() {
        override val saksreferanse = søknadId.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val oppgavetype = Oppgavetype.ATTESTERING
        override val behandlingstype = when (søknadstype) {
            Søknadstype.FØRSTEGANGSSØKNAD -> Behandlingstype.FØRSTEGANGSSØKNAD
            Søknadstype.NY_PERIODE -> Behandlingstype.NY_PERIODE
        }
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(30)
    }

    data class Revurderingsbehandling(
        val saksnummer: Saksnummer,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker? = null,
        override val clock: Clock = Clock.systemUTC(),
    ) : OppgaveConfig() {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.BEHANDLE_SAK
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(30)
    }

    data class AttesterRevurdering(
        val saksnummer: Saksnummer,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker? = null,
        override val clock: Clock = Clock.systemUTC(),
    ) : OppgaveConfig() {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.ATTESTERING
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(30)
    }

    data class Personhendelse(
        val saksnummer: Saksnummer,
        val personhendelsestype: no.nav.su.se.bakover.domain.hendelse.Personhendelse.Hendelse,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker? = null,
        override val clock: Clock = Clock.systemUTC(),
    ) : OppgaveConfig() {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKNING
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(7)
    }
}
