package no.nav.su.se.bakover.domain.oppgave

import arrow.core.NonEmptySet
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstema
import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstype
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse.TilknyttetSak.IkkeSendtTilOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun Sakstype.toBehandlingstema(): Behandlingstema =
    when (this) {
        Sakstype.ALDER -> Behandlingstema.SU_ALDER
        Sakstype.UFØRE -> Behandlingstema.SU_UFØRE_FLYKTNING
    }

internal fun Tidspunkt.toOppgaveFormat() = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    .withZone(zoneIdOslo).format(this)

sealed interface OppgaveConfig {
    val journalpostId: JournalpostId?
    val saksreferanse: String
    val fnr: Fnr
    val behandlingstema: Behandlingstema?
    val oppgavetype: Oppgavetype
    val behandlingstype: Behandlingstype
    val tilordnetRessurs: NavIdentBruker?
    val sakstype: Sakstype

    /**
     * Påkrevd dersom tilordnetRessurs brukes
     */
    val tildeltEnhetsnr: String? get() = if (tilordnetRessurs == null) null else "4815"
    val clock: Clock
    val aktivDato: LocalDate
    val fristFerdigstillelse: LocalDate
    val beskrivelse: String get() = "--- ${Tidspunkt.now(clock).toOppgaveFormat()} - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksreferanse"

    /**
     * Denne er knyttet til mottak av søknad (både førstegang og ny periode), men brukes videre av søknadsbehandlinga
     */
    data class Søknad(
        override val journalpostId: JournalpostId,
        val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val tilordnetRessurs: NavIdentBruker?,
        override val clock: Clock,
        override val sakstype: Sakstype,
    ) : OppgaveConfig {

        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }

        override val saksreferanse = saksnummer.toString()
        override val behandlingstema = sakstype.toBehandlingstema()
        override val oppgavetype = Oppgavetype.BEHANDLE_SAK
        override val behandlingstype = Behandlingstype.SØKNAD
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(30)
    }

    // TODO: Ikke i bruk?
    data class AttesterSøknadsbehandling(
        val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val tilordnetRessurs: NavIdentBruker?,
        override val clock: Clock,
        override val sakstype: Sakstype,
    ) : OppgaveConfig {
        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }

        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = sakstype.toBehandlingstema()
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
        override val sakstype: Sakstype,
    ) : OppgaveConfig {
        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }

        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = sakstype.toBehandlingstema()
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
        override val sakstype: Sakstype,
    ) : OppgaveConfig {
        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }

        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = sakstype.toBehandlingstema()
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
        override val sakstype: Sakstype,
    ) : OppgaveConfig {
        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }

        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val behandlingstema = sakstype.toBehandlingstema()
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
        override val sakstype: Sakstype,
    ) : OppgaveConfig {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val tilordnetRessurs: NavIdentBruker? = null
        override val behandlingstema = sakstype.toBehandlingstema()
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(7)
        override val beskrivelse: String
            get() = super.beskrivelse + "\nPersonhendelse: ${OppgavebeskrivelseMapper.map(personhendelse)}"

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
        override val sakstype: Sakstype,
    ) : OppgaveConfig {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val tilordnetRessurs: NavIdentBruker? = null
        override val behandlingstema = sakstype.toBehandlingstema()
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(3)
        override val beskrivelse: String
            get() = super.beskrivelse + "\nKontrollnotat/Dokumentasjon av oppfølgingssamtale ikke funnet for perioden: ${periode.fraOgMed}-${periode.tilOgMed}. Maskinell stans kunne ikke gjennomføres."

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
        override val sakstype: Sakstype,
    ) : OppgaveConfig {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val tilordnetRessurs: NavIdentBruker? = null
        override val behandlingstema = sakstype.toBehandlingstema()
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
        override val sakstype: Sakstype,
        override val fnr: Fnr,
        override val clock: Clock,
    ) : OppgaveConfig {
        override val saksreferanse = saksnummer.toString()
        override val journalpostId: JournalpostId? = null
        override val tilordnetRessurs: NavIdentBruker? = null
        override val behandlingstema = sakstype.toBehandlingstema()
        override val behandlingstype = Behandlingstype.REVURDERING
        override val oppgavetype = Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
        override val aktivDato: LocalDate = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate = aktivDato.plusDays(7)
        override val beskrivelse: String
            get() = super.beskrivelse + "\nEndring i institusjonsopphold"

        init {
            if (tilordnetRessurs != null) {
                require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
            }
        }
    }

    sealed interface Klage : OppgaveConfig {
        val saksnummer: Saksnummer
        override val saksreferanse: String get() = saksnummer.toString()
        override val sakstype: Sakstype
        override val behandlingstype get() = Behandlingstype.KLAGE
        override val aktivDato: LocalDate get() = LocalDate.now(clock)
        override val fristFerdigstillelse: LocalDate get() = aktivDato.plusDays(30)

        /**
         * Opprettes av en jobb som prosesserer hendelsene fra Klageinstans. Består av:
         * 1) Oppgaver som bara formidler informasjon, disse må lukkes av saksbehandler selv i gosys.
         * 2) Oppgaver som krever ytterliggere saksbehandling på klagen. Disse lukker systemet selv.
         * */
        sealed interface Klageinstanshendelse : Klage {
            val hendelsestype: String
            override val beskrivelse: String
                get() = super.beskrivelse + "\n ${OppgavebeskrivelseMapper.map(this)}"
            sealed interface AvsluttetKlageinstansUtfall : Klageinstanshendelse {
                val utfall: no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall
                val avsluttetTidspunkt: Tidspunkt
                val journalpostIDer: List<JournalpostId>

                // Kabal kan sende et eller flere brev. Så det er ikke lenger naturlig å knytte oppgaven til en spesifikk journalpost.
                override val journalpostId: Nothing? get() = null

                data class Handling(
                    override val saksnummer: Saksnummer,
                    override val fnr: Fnr,
                    override val tilordnetRessurs: NavIdentBruker?,
                    override val clock: Clock,
                    override val utfall: no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall,
                    override val avsluttetTidspunkt: Tidspunkt,
                    override val journalpostIDer: List<JournalpostId>,
                    override val hendelsestype: String,
                    override val sakstype: Sakstype,
                ) : AvsluttetKlageinstansUtfall {
                    init {
                        if (tilordnetRessurs != null) {
                            require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
                        }
                    }
                    override val behandlingstema: Behandlingstema = sakstype.toBehandlingstema()
                    override val oppgavetype = Oppgavetype.BEHANDLE_SAK
                }

                data class Informasjon(
                    override val saksnummer: Saksnummer,
                    override val fnr: Fnr,
                    override val tilordnetRessurs: NavIdentBruker?,
                    override val clock: Clock,
                    override val utfall: no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall,
                    override val avsluttetTidspunkt: Tidspunkt,
                    override val journalpostIDer: List<JournalpostId>,
                    override val hendelsestype: String,
                    override val sakstype: Sakstype,
                ) : AvsluttetKlageinstansUtfall {
                    init {
                        if (tilordnetRessurs != null) {
                            require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
                        }
                    }
                    override val behandlingstema: Behandlingstema = sakstype.toBehandlingstema()
                    override val oppgavetype = Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE
                }
            }

            data class BehandlingOpprettet(
                override val saksnummer: Saksnummer,
                override val fnr: Fnr,
                override val tilordnetRessurs: NavIdentBruker?,
                override val clock: Clock,
                val mottatt: Tidspunkt,
                override val hendelsestype: String,
                override val sakstype: Sakstype,
            ) : Klageinstanshendelse {
                init {
                    if (tilordnetRessurs != null) {
                        require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
                    }
                }

                override val journalpostId: Nothing? get() = null
                override val behandlingstema: Behandlingstema = sakstype.toBehandlingstema()
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
            override val sakstype: Sakstype,
        ) : Klage {
            init {
                if (tilordnetRessurs != null) {
                    require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
                }
            }
            override val behandlingstema: Behandlingstema = sakstype.toBehandlingstema()
            override val oppgavetype = Oppgavetype.BEHANDLE_SAK
        }

        /**
         * Dette er attesteringsoppgaven som opprettes:
         * 1) Når en klage sendes til attestering (dette kan skje flere ganger, se underkjenning)
         */
        // TODO: denne er ikke i bruk
        data class Attestering(
            override val saksnummer: Saksnummer,
            override val fnr: Fnr,
            override val journalpostId: JournalpostId,
            override val tilordnetRessurs: NavIdentBruker?,
            override val clock: Clock,
            override val sakstype: Sakstype,
        ) : Klage {
            init {
                if (tilordnetRessurs != null) {
                    require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må settes dersom tilordnetRessurs er satt" }
                }
            }
            override val behandlingstema: Behandlingstema = sakstype.toBehandlingstema()
            override val oppgavetype = Oppgavetype.ATTESTERING
        }
    }
}
