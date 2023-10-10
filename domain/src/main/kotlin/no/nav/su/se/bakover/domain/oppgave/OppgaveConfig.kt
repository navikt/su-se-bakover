package no.nav.su.se.bakover.domain.oppgave

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.domain.Behandlingstema
import no.nav.su.se.bakover.domain.Behandlingstype
import no.nav.su.se.bakover.domain.Oppgavetype
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
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
    data class Søknad(
        override val journalpostId: JournalpostId,
        val søknadId: UUID,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker?,
        override val clock: Clock,
        val sakstype: Sakstype,
    ) : OppgaveConfig() {
        override val saksreferanse = søknadId.toString()
        override val behandlingstema = when (sakstype) {
            Sakstype.ALDER -> Behandlingstema.SU_ALDER
            Sakstype.UFØRE -> Behandlingstema.SU_UFØRE_FLYKTNING
        }
        override val oppgavetype = Oppgavetype.BEHANDLE_SAK
        override val behandlingstype = Behandlingstype.SØKNAD
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(30)
    }

    data class AttesterSøknadsbehandling(
        val søknadId: UUID,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker?,
        override val clock: Clock,
    ) : OppgaveConfig() {
        override val saksreferanse = søknadId.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKTNING
        override val oppgavetype = Oppgavetype.ATTESTERING
        override val behandlingstype = Behandlingstype.SØKNAD
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(30)
    }

    data class Revurderingsbehandling(
        val saksnummer: Saksnummer,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker?,
        override val clock: Clock,
    ) : OppgaveConfig() {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKTNING
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.BEHANDLE_SAK
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(30)
    }

    data class Tilbakekrevingsbehandling(
        val saksnummer: Saksnummer,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker?,
        override val clock: Clock,
    ) : OppgaveConfig() {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKTNING
        override val behandlingstype = Behandlingstype.TILBAKEKREVING
        override val oppgavetype = Oppgavetype.BEHANDLE_SAK
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(30)
    }

    data class AttesterRevurdering(
        val saksnummer: Saksnummer,
        override val aktørId: AktørId,
        override val tilordnetRessurs: NavIdentBruker?,
        override val clock: Clock,
    ) : OppgaveConfig() {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKTNING
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.ATTESTERING
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(30)
    }

    data class Personhendelse(
        val saksnummer: Saksnummer,
        val personhendelsestype: no.nav.su.se.bakover.domain.personhendelse.Personhendelse.Hendelse,
        override val aktørId: AktørId,
        override val clock: Clock,
    ) : OppgaveConfig() {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val tilordnetRessurs: NavIdentBruker? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKTNING
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(7)
    }

    data class KlarteIkkeÅStanseYtelseVedUtløpAvFristForKontrollsamtale(
        val saksnummer: Saksnummer,
        val periode: DatoIntervall,
        override val aktørId: AktørId,
        override val clock: Clock,
    ) : OppgaveConfig() {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val tilordnetRessurs: NavIdentBruker? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKTNING
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(3)
    }

    data class Kontrollsamtale(
        val saksnummer: Saksnummer,
        override val aktørId: AktørId,
        override val clock: Clock,
    ) : OppgaveConfig() {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val tilordnetRessurs: NavIdentBruker? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKTNING
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.FREMLEGGING
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(30)
    }

    data class Institusjonsopphold(
        val saksnummer: Saksnummer,
        val sakstype: Sakstype,
        override val aktørId: AktørId,
        override val clock: Clock,
    ) : OppgaveConfig() {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val tilordnetRessurs: NavIdentBruker? = null
        override val behandlingstema = when (sakstype) {
            Sakstype.ALDER -> Behandlingstema.SU_ALDER
            Sakstype.UFØRE -> Behandlingstema.SU_UFØRE_FLYKTNING
        }
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(7)
    }

    sealed class Klage : OppgaveConfig() {
        abstract val saksnummer: Saksnummer
        override val saksreferanse by lazy { saksnummer.toString() }
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKTNING
        override val behandlingstype = Behandlingstype.KLAGE
        override val aktivDato: LocalDate by lazy { LocalDate.now(clock) }
        override val fristFerdigstillelse: LocalDate by lazy { aktivDato.plusDays(30) }

        /**
         * Opprettes av en jobb som prosesserer hendelsene fra Klageinstans. Består av:
         * 1) Oppgaver som bara formidler informasjon, disse må lukkes av saksbehandler selv i gosys.
         * 2) Oppgaver som krever ytterliggere saksbehandling på klagen. Disse lukker systemet selv.
         * */
        sealed class Klageinstanshendelse : Klage() {
            abstract val utfall: KlageinstansUtfall
            abstract val avsluttetTidspunkt: Tidspunkt
            abstract val journalpostIDer: List<JournalpostId>

            // Kabal kan sende et eller flere brev. Så det er ikke lenger naturlig å knytte oppgaven til en spesifikk journalpost.
            override val journalpostId: Nothing? = null

            data class Handling(
                override val saksnummer: Saksnummer,
                override val aktørId: AktørId,
                override val tilordnetRessurs: NavIdentBruker?,
                override val clock: Clock,
                override val utfall: KlageinstansUtfall,
                override val avsluttetTidspunkt: Tidspunkt,
                override val journalpostIDer: List<JournalpostId>,
            ) : Klageinstanshendelse() {
                override val oppgavetype = Oppgavetype.BEHANDLE_SAK
            }

            data class Informasjon(
                override val saksnummer: Saksnummer,
                override val aktørId: AktørId,
                override val tilordnetRessurs: NavIdentBruker?,
                override val clock: Clock,
                override val utfall: KlageinstansUtfall,
                override val avsluttetTidspunkt: Tidspunkt,
                override val journalpostIDer: List<JournalpostId>,
            ) : Klageinstanshendelse() {

                override val oppgavetype = Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
            }
        }

        /**
         * Dette er saksbehandlingsoppgaven som opprettes:
         * 1) Når en klage opprettes
         * 2) Når en klage til attestering sendes tilbake til saksbehandler
         */
        data class Saksbehandler(
            override val saksnummer: Saksnummer,
            override val aktørId: AktørId,
            override val journalpostId: JournalpostId,
            override val tilordnetRessurs: NavIdentBruker?,
            override val clock: Clock,
        ) : Klage() {
            override val oppgavetype = Oppgavetype.BEHANDLE_SAK
        }

        /**
         * Dette er attesteringsoppgaven som opprettes:
         * 1) Når en klage sendes til attestering (dette kan skje flere ganger, se underkjenning)
         */
        data class Attestering(
            override val saksnummer: Saksnummer,
            override val aktørId: AktørId,
            override val journalpostId: JournalpostId,
            override val tilordnetRessurs: NavIdentBruker?,
            override val clock: Clock,
        ) : Klage() {
            override val oppgavetype = Oppgavetype.ATTESTERING
        }
    }
}
