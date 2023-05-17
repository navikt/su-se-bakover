package no.nav.su.se.bakover.domain.søknad

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.søknad.lukk.AvvistSøknadBrevRequest
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevRequest
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.ForNav
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdAlder
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdUføre
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

sealed interface Søknad {
    val id: UUID
    val opprettet: Tidspunkt
    val sakId: UUID
    val søknadInnhold: SøknadInnhold
    val innsendtAv: NavIdentBruker

    fun lukk(lukkSøknadCommand: LukkSøknadCommand): Journalført.MedOppgave.Lukket =
        throw IllegalArgumentException("Kunne ikke lukke søknad og søknadsbehandling. Søknaden må være i tilstanden: Søknad.Journalført.MedOppgave.IkkeLukket for sak $sakId og søknad ${this.id}")

    val fnr: Fnr
        get() = søknadInnhold.personopplysninger.fnr

    val type: Sakstype
        get() = when (søknadInnhold) {
            is SøknadsinnholdAlder -> Sakstype.ALDER
            is SøknadsinnholdUføre -> Sakstype.UFØRE
        }

    /**
     * Når Nav mottok søknaden:
     * * _opprettet_ dersom søknaden er digital.
     * * _søknadInnhold.forNav.mottaksdatoForSøknad_ dersom det er en papirsøknad.
     */
    @get:JsonIgnore // TODO jah: La de som serialiserer Søknad ha sin egen DTO.
    val mottaksdato: LocalDate
        get() = when (val søknadstype = søknadInnhold.forNav) {
            is ForNav.DigitalSøknad -> opprettet.toLocalDate(zoneIdOslo)
            is ForNav.Papirsøknad -> søknadstype.mottaksdatoForSøknad
        }

    data class Ny(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val søknadInnhold: SøknadInnhold,
        override val innsendtAv: NavIdentBruker,
    ) : Søknad {

        fun journalfør(
            journalpostId: JournalpostId,
        ): Journalført.UtenOppgave {
            return Journalført.UtenOppgave(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                søknadInnhold = søknadInnhold,
                journalpostId = journalpostId,
                innsendtAv = innsendtAv,
            )
        }
    }

    sealed interface Journalført : Søknad {
        val journalpostId: JournalpostId

        data class UtenOppgave(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val søknadInnhold: SøknadInnhold,
            override val journalpostId: JournalpostId,
            override val innsendtAv: NavIdentBruker,
        ) : Journalført {

            fun medOppgave(
                oppgaveId: OppgaveId,
            ): MedOppgave.IkkeLukket {
                return MedOppgave.IkkeLukket(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    søknadInnhold = søknadInnhold,
                    journalpostId = journalpostId,
                    oppgaveId = oppgaveId,
                    innsendtAv = innsendtAv,
                )
            }
        }

        sealed interface MedOppgave : Journalført {
            val oppgaveId: OppgaveId

            data class IkkeLukket(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val søknadInnhold: SøknadInnhold,
                override val journalpostId: JournalpostId,
                override val oppgaveId: OppgaveId,
                override val innsendtAv: NavIdentBruker,
            ) : MedOppgave {

                override fun lukk(
                    lukkSøknadCommand: LukkSøknadCommand,
                ): Lukket {
                    require(id == lukkSøknadCommand.søknadId) {
                        "Søknaden's id $id må være lik lukkSøknadCommand sin id ${lukkSøknadCommand.søknadId}"
                    }
                    return when (lukkSøknadCommand) {
                        is LukkSøknadCommand.MedBrev.AvvistSøknad -> Lukket.Avvist(
                            id = id,
                            opprettet = opprettet,
                            sakId = sakId,
                            søknadInnhold = søknadInnhold,
                            journalpostId = journalpostId,
                            oppgaveId = oppgaveId,
                            lukketTidspunkt = lukkSøknadCommand.lukketTidspunkt,
                            lukketAv = lukkSøknadCommand.saksbehandler,
                            brevvalg = lukkSøknadCommand.brevvalg,
                            innsendtAv = innsendtAv,
                        )

                        is LukkSøknadCommand.UtenBrev.AvvistSøknad -> Lukket.Avvist(
                            id = id,
                            opprettet = opprettet,
                            sakId = sakId,
                            søknadInnhold = søknadInnhold,
                            journalpostId = journalpostId,
                            oppgaveId = oppgaveId,
                            lukketTidspunkt = lukkSøknadCommand.lukketTidspunkt,
                            lukketAv = lukkSøknadCommand.saksbehandler,
                            brevvalg = Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev(),
                            innsendtAv = innsendtAv,
                        )

                        is LukkSøknadCommand.MedBrev.TrekkSøknad -> Lukket.TrukketAvSøker(
                            id = id,
                            opprettet = opprettet,
                            sakId = sakId,
                            søknadInnhold = søknadInnhold,
                            journalpostId = journalpostId,
                            oppgaveId = oppgaveId,
                            lukketTidspunkt = lukkSøknadCommand.lukketTidspunkt,
                            lukketAv = lukkSøknadCommand.saksbehandler,
                            trukketDato = lukkSøknadCommand.trukketDato,
                            innsendtAv = innsendtAv,
                        )

                        is LukkSøknadCommand.UtenBrev.BortfaltSøknad -> Lukket.Bortfalt(
                            id = id,
                            opprettet = opprettet,
                            sakId = sakId,
                            søknadInnhold = søknadInnhold,
                            journalpostId = journalpostId,
                            oppgaveId = oppgaveId,
                            lukketTidspunkt = lukkSøknadCommand.lukketTidspunkt,
                            lukketAv = lukkSøknadCommand.saksbehandler,
                            innsendtAv = innsendtAv,
                        )
                    }
                }

                /**
                 * Mulighet for å generere et brevutkast før saksbehandler lukker søknaden.
                 *
                 * @return null dersom det ikke skal kunne lages brev.
                 */
                fun toBrevRequest(
                    lukkSøknadCommand: LukkSøknadCommand,
                    hentPerson: () -> Person,
                    clock: Clock,
                    hentSaksbehandlerNavn: (lukketAv: Saksbehandler) -> String,
                    hentSaksnummer: () -> Saksnummer,
                ): Either<Lukket.KanIkkeLageBrevRequestForDenneTilstanden, LagBrevRequest> =
                    this.lukk(lukkSøknadCommand).toBrevRequest(
                        hentPerson = hentPerson,
                        clock = clock,
                        hentSaksbehandlerNavn = hentSaksbehandlerNavn,
                        hentSaksnummer = hentSaksnummer,
                    )
            }

            /**
             * https://jira.adeo.no/browse/BEGREP-2320 og https://jira.adeo.no/browse/BEGREP-1733
             * https://kodeverk-web.nais.adeo.no/kodeverksoversikt/kodeverk/Avslutningsstatuser
             *
             * TODO: Avklar om vi skal rename denne til Avslutt eller Avbrutt for å stemme mer overens med behandlingene.
             */
            sealed interface Lukket : MedOppgave {
                override val id: UUID
                override val opprettet: Tidspunkt
                override val sakId: UUID
                override val søknadInnhold: SøknadInnhold
                override val journalpostId: JournalpostId
                override val oppgaveId: OppgaveId
                val lukketTidspunkt: Tidspunkt
                val lukketAv: Saksbehandler
                val brevvalg: Brevvalg

                /**
                 * @return null dersom det ikke skal kunne lages brev.
                 */
                fun toBrevRequest(
                    hentPerson: () -> Person,
                    clock: Clock,
                    hentSaksbehandlerNavn: (lukketAv: Saksbehandler) -> String,
                    hentSaksnummer: () -> Saksnummer,
                ): Either<KanIkkeLageBrevRequestForDenneTilstanden, LagBrevRequest>

                object KanIkkeLageBrevRequestForDenneTilstanden {
                    override fun toString() = this::class.simpleName!!
                }

                /**
                 * Saksbehandler velger om det sendes brev eller ikke.
                 * Dersom det skal sendes brev er det enten et informasjonsbrev med fritekst, eller et vedtaksbrev uten fritekst.
                 */
                data class Avvist(
                    override val id: UUID,
                    override val opprettet: Tidspunkt,
                    override val sakId: UUID,
                    override val søknadInnhold: SøknadInnhold,
                    override val journalpostId: JournalpostId,
                    override val oppgaveId: OppgaveId,
                    override val lukketTidspunkt: Tidspunkt,
                    override val lukketAv: Saksbehandler,
                    override val brevvalg: Brevvalg.SaksbehandlersValg,
                    override val innsendtAv: NavIdentBruker,
                ) : Lukket {
                    init {
                        // Vi får ikke kompilatorstøtte for dette ved å bruke en generisk type som Brevvalg.
                        require(brevvalg is Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev || brevvalg is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst || brevvalg is Brevvalg.SaksbehandlersValg.SkalSendeBrev.VedtaksbrevUtenFritekst)
                        require(lukketTidspunkt >= opprettet)
                    }

                    override fun toBrevRequest(
                        hentPerson: () -> Person,
                        clock: Clock,
                        hentSaksbehandlerNavn: (lukketAv: Saksbehandler) -> String,
                        hentSaksnummer: () -> Saksnummer,
                    ): Either<KanIkkeLageBrevRequestForDenneTilstanden, LagBrevRequest> {
                        return when (brevvalg) {
                            is Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev -> KanIkkeLageBrevRequestForDenneTilstanden.left()
                            is Brevvalg.SaksbehandlersValg.SkalSendeBrev -> AvvistSøknadBrevRequest(
                                person = hentPerson(),
                                brevvalg = brevvalg,
                                saksbehandlerNavn = hentSaksbehandlerNavn(lukketAv),
                                dagensDato = LocalDate.now(clock),
                                saksnummer = hentSaksnummer(),
                            ).right()
                        }
                    }
                }

                /**
                 * Søker har selv trukket søknaden sin.
                 * Dette fører ikke til et vedtak.
                 * Det sendes et informasjonsbrev uten fritekst.
                 *
                 * @param trukketDato Den faktiske datoen brukeren trakk søknaden. Må være før eller lik [lukketTidspunkt] og etter eller lik [mottaksdato]
                 */
                data class TrukketAvSøker(
                    override val id: UUID,
                    override val opprettet: Tidspunkt,
                    override val sakId: UUID,
                    override val søknadInnhold: SøknadInnhold,
                    override val journalpostId: JournalpostId,
                    override val oppgaveId: OppgaveId,
                    override val lukketTidspunkt: Tidspunkt,
                    override val lukketAv: Saksbehandler,
                    val trukketDato: LocalDate,
                    override val innsendtAv: NavIdentBruker,
                ) : Lukket {
                    override val brevvalg =
                        Brevvalg.SkalSendeBrev.InformasjonsbrevUtenFritekst("Saksbehandler får ikke per tidspunkt gjøre noen brevvalg dersom bruker trekker søknaden.")

                    init {
                        require(trukketDato <= lukketTidspunkt.toLocalDate(zoneIdOslo)) {
                            "trukketDato $trukketDato må være samtidig eller før lukketTidspunkt $lukketTidspunkt for søknad $id"
                        }
                        require(trukketDato >= mottaksdato) {
                            "trukketDato $trukketDato må være samtidig eller etter mottaksdato $mottaksdato for søknad $id"
                        }
                        require(lukketTidspunkt >= opprettet) {
                            "lukketTidspunkt $lukketTidspunkt må være samtidig eller etter opprettet $opprettet for søknad $id"
                        }
                    }

                    override fun toBrevRequest(
                        hentPerson: () -> Person,
                        clock: Clock,
                        hentSaksbehandlerNavn: (lukketAv: Saksbehandler) -> String,
                        hentSaksnummer: () -> Saksnummer,
                    ): Either<KanIkkeLageBrevRequestForDenneTilstanden, LagBrevRequest> = TrukketSøknadBrevRequest(
                        person = hentPerson(),
                        søknadOpprettet = opprettet,
                        trukketDato = trukketDato,
                        saksbehandlerNavn = hentSaksbehandlerNavn(lukketAv),
                        dagensDato = LocalDate.now(clock),
                        saksnummer = hentSaksnummer(),
                    ).right()
                }

                /**
                 * Det skal ikke kunne sendes brev når en søknad bortfaller.
                 */
                data class Bortfalt(
                    override val id: UUID,
                    override val opprettet: Tidspunkt,
                    override val sakId: UUID,
                    override val søknadInnhold: SøknadInnhold,
                    override val journalpostId: JournalpostId,
                    override val oppgaveId: OppgaveId,
                    override val lukketTidspunkt: Tidspunkt,
                    override val lukketAv: Saksbehandler,
                    override val innsendtAv: NavIdentBruker,
                ) : Lukket {
                    override val brevvalg =
                        Brevvalg.SkalIkkeSendeBrev("Saksbehandler får ikke per tidspunkt gjøre noen brevvalg dersom søknaden bortfaller.")

                    override fun toBrevRequest(
                        hentPerson: () -> Person,
                        clock: Clock,
                        hentSaksbehandlerNavn: (lukketAv: Saksbehandler) -> String,
                        hentSaksnummer: () -> Saksnummer,
                    ): Either<KanIkkeLageBrevRequestForDenneTilstanden, LagBrevRequest> =
                        KanIkkeLageBrevRequestForDenneTilstanden.left()

                    init {
                        require(lukketTidspunkt >= opprettet)
                    }
                }
            }
        }
    }
}
