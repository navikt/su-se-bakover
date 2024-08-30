package no.nav.su.se.bakover.domain.oppgave

import arrow.core.NonEmptySet
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstema
import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstype
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse.TilknyttetSak.IkkeSendtTilOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

sealed interface OppgaveConfig {
    val journalpostId: JournalpostId?
    val saksreferanse: String
    val fnr: Fnr
    val behandlingstema: Behandlingstema?
    val oppgavetype: Oppgavetype
    val behandlingstype: Behandlingstype
    val tilordnetRessurs: NavIdentBruker?

    /**
     * Påkrevd dersom tilordnetRessurs brukes
     */
    val tildeltEnhetsnr: String? get() = if (tilordnetRessurs == null) null else "4815"
    val clock: Clock
    val aktivDato: LocalDate
    val fristFerdigstillelse: LocalDate

    /**
     * Denne er knyttet til mottak av søknad (både førstegang og ny periode), men brukes videre av søknadsbehandlinga
     */
    data class Søknad(
        override val journalpostId: JournalpostId,
        val søknadId: UUID,
        override val fnr: Fnr,
        override val tilordnetRessurs: NavIdentBruker?,
        override val clock: Clock,
        val sakstype: Sakstype,
    ) : OppgaveConfig {

        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }

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
        override val fnr: Fnr,
        override val tilordnetRessurs: NavIdentBruker?,
        override val clock: Clock,
    ) : OppgaveConfig {
        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }

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
        override val fnr: Fnr,
        override val tilordnetRessurs: NavIdentBruker?,
        override val clock: Clock,
    ) : OppgaveConfig {
        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }

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
        override val fnr: Fnr,
        override val tilordnetRessurs: NavIdentBruker?,
        override val clock: Clock,
    ) : OppgaveConfig {
        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }

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
        override val fnr: Fnr,
        override val tilordnetRessurs: NavIdentBruker?,
        override val clock: Clock,
    ) : OppgaveConfig {
        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }

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
        val personhendelse: NonEmptySet<IkkeSendtTilOppgave>,
        override val fnr: Fnr,
        override val clock: Clock,
    ) : OppgaveConfig {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val tilordnetRessurs: NavIdentBruker? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKTNING
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(7)

        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }
    }

    data class KlarteIkkeÅStanseYtelseVedUtløpAvFristForKontrollsamtale(
        val saksnummer: Saksnummer,
        val periode: DatoIntervall,
        override val fnr: Fnr,
        override val clock: Clock,
    ) : OppgaveConfig {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val tilordnetRessurs: NavIdentBruker? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKTNING
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(3)

        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }
    }

    data class Kontrollsamtale(
        val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val clock: Clock,
    ) : OppgaveConfig {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val tilordnetRessurs: NavIdentBruker? = null
        override val behandlingstema = Behandlingstema.SU_UFØRE_FLYKTNING
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.FREMLEGGING
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(30)

        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }
    }

    data class Institusjonsopphold(
        val saksnummer: Saksnummer,
        val sakstype: Sakstype,
        override val fnr: Fnr,
        override val clock: Clock,
    ) : OppgaveConfig {
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

        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }
    }

    sealed interface Klage : OppgaveConfig {
        val saksnummer: Saksnummer
        override val saksreferanse: String get() = saksnummer.toString()
        override val behandlingstema get() = Behandlingstema.SU_UFØRE_FLYKTNING
        override val behandlingstype get() = Behandlingstype.KLAGE
        override val aktivDato: LocalDate get() = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate get() = aktivDato.plusDays(30)

        /**
         * Opprettes av en jobb som prosesserer hendelsene fra Klageinstans. Består av:
         * 1) Oppgaver som bara formidler informasjon, disse må lukkes av saksbehandler selv i gosys.
         * 2) Oppgaver som krever ytterliggere saksbehandling på klagen. Disse lukker systemet selv.
         * */
        sealed interface Klageinstanshendelse : Klage {

            sealed interface KlagebehandlingAvsluttet : Klageinstanshendelse {
                val utfall: AvsluttetKlageinstansUtfall
                val avsluttetTidspunkt: Tidspunkt
                val journalpostIDer: List<JournalpostId>

                // Kabal kan sende et eller flere brev. Så det er ikke lenger naturlig å knytte oppgaven til en spesifikk journalpost.
                override val journalpostId: Nothing? get() = null

                data class Handling(
                    override val saksnummer: Saksnummer,
                    override val fnr: Fnr,
                    override val tilordnetRessurs: NavIdentBruker?,
                    override val clock: Clock,
                    override val utfall: AvsluttetKlageinstansUtfall,
                    override val avsluttetTidspunkt: Tidspunkt,
                    override val journalpostIDer: List<JournalpostId>,
                ) : KlagebehandlingAvsluttet {
                    init {
                        if (tilordnetRessurs != null) {
                            require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
                        }
                    }

                    override val oppgavetype = Oppgavetype.BEHANDLE_SAK
                }

                data class Informasjon(
                    override val saksnummer: Saksnummer,
                    override val fnr: Fnr,
                    override val tilordnetRessurs: NavIdentBruker?,
                    override val clock: Clock,
                    override val utfall: AvsluttetKlageinstansUtfall,
                    override val avsluttetTidspunkt: Tidspunkt,
                    override val journalpostIDer: List<JournalpostId>,
                ) : KlagebehandlingAvsluttet {
                    init {
                        if (tilordnetRessurs != null) {
                            require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
                        }
                    }

                    override val oppgavetype = Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
                }
            }

            data class AnkebehandlingOpprettet(
                override val saksnummer: Saksnummer,
                override val fnr: Fnr,
                override val tilordnetRessurs: NavIdentBruker?,
                override val clock: Clock,
                val mottattKlageinstans: Tidspunkt,
            ) : Klageinstanshendelse {
                init {
                    if (tilordnetRessurs != null) {
                        require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
                    }
                }

                override val journalpostId: Nothing? get() = null

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
            override val fnr: Fnr,
            override val journalpostId: JournalpostId,
            override val tilordnetRessurs: NavIdentBruker?,
            override val clock: Clock,
        ) : Klage {
            init {
                if (tilordnetRessurs != null) {
                    require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
                }
            }

            override val oppgavetype = Oppgavetype.BEHANDLE_SAK
        }

        /**
         * Dette er attesteringsoppgaven som opprettes:
         * 1) Når en klage sendes til attestering (dette kan skje flere ganger, se underkjenning)
         */
        data class Attestering(
            override val saksnummer: Saksnummer,
            override val fnr: Fnr,
            override val journalpostId: JournalpostId,
            override val tilordnetRessurs: NavIdentBruker?,
            override val clock: Clock,
        ) : Klage {
            init {
                if (tilordnetRessurs != null) {
                    require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
                }
            }

            override val oppgavetype = Oppgavetype.ATTESTERING
        }
    }
}
