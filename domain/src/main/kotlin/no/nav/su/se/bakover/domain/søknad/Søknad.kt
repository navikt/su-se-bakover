package no.nav.su.se.bakover.domain.søknad

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonIgnore
import dokument.domain.Dokumenttilstand
import dokument.domain.GenererDokumentCommand
import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.Avbrutt
import no.nav.su.se.bakover.common.domain.Avsluttet
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.ident.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.brev.command.AvvistSøknadDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.TrukketSøknadDokumentCommand
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.ForNav
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdAlder
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdUføre
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
                            dokumenttilstand = Dokumenttilstand.IKKE_GENERERT_ENDA,
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
                            dokumenttilstand = Dokumenttilstand.SKAL_IKKE_GENERERE,
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
                            dokumenttilstand = Dokumenttilstand.IKKE_GENERERT_ENDA,
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
                    hentSak: () -> SakInfo,
                ): Either<Lukket.KanIkkeLageBrevRequestForDenneTilstanden, GenererDokumentCommand> =
                    this.lukk(lukkSøknadCommand).lagGenererDokumentKommando(
                        hentSak = hentSak,
                    )
            }

            /**
             * https://jira.adeo.no/browse/BEGREP-2320 og https://jira.adeo.no/browse/BEGREP-1733
             * https://kodeverk-web.nais.adeo.no/kodeverksoversikt/kodeverk/Avslutningsstatuser
             *
             * En Lukket søknad kan enten føre til et vedtak eller et avbrudd.
             */
            sealed interface Lukket :
                MedOppgave,
                Avsluttet {
                override val id: UUID
                override val opprettet: Tidspunkt
                override val sakId: UUID
                override val søknadInnhold: SøknadInnhold
                override val journalpostId: JournalpostId
                override val oppgaveId: OppgaveId
                val lukketTidspunkt: Tidspunkt
                val lukketAv: Saksbehandler
                val brevvalg: Brevvalg
                val dokumenttilstand: Dokumenttilstand get() = brevvalg.tilDokumenttilstand()

                /**
                 * @return null dersom det ikke skal kunne lages brev.
                 */
                fun lagGenererDokumentKommando(
                    hentSak: () -> SakInfo,
                ): Either<KanIkkeLageBrevRequestForDenneTilstanden, GenererDokumentCommand>

                data object KanIkkeLageBrevRequestForDenneTilstanden

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
                    override val dokumenttilstand: Dokumenttilstand = brevvalg.tilDokumenttilstand(),
                ) : Lukket {
                    override val avsluttetTidspunkt: Tidspunkt = lukketTidspunkt
                    override val avsluttetAv: NavIdentBruker = lukketAv

                    /**
                     * TODO jah: I dette tilfellet klarer vi ikke avgjøre om Behandlingen er iverksatt eller avbrutt.Som i de fleste tilfeller er/skal være et vedtak/iverksetting, men vi har bare kodet det som en avbrutt søknad/søknadsbehandling.
                     */
                    override fun erAvbrutt() = null

                    init {
                        when (brevvalg) {
                            is Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev,
                            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst,
                            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst,
                            -> Unit
                            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.UtenFritekst -> throw IllegalStateException("Saksbehandler kan ikke velge å sende vedtaksbrev uten fritekst.")
                        }
                        require(lukketTidspunkt >= opprettet)
                    }

                    override fun lagGenererDokumentKommando(
                        hentSak: () -> SakInfo,
                    ): Either<KanIkkeLageBrevRequestForDenneTilstanden, GenererDokumentCommand> {
                        return when (brevvalg) {
                            is Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev -> KanIkkeLageBrevRequestForDenneTilstanden.left()
                            is Brevvalg.SaksbehandlersValg.SkalSendeBrev -> AvvistSøknadDokumentCommand(
                                fødselsnummer = fnr,
                                saksnummer = hentSak().saksnummer,
                                sakstype = hentSak().type,
                                brevvalg = brevvalg,
                                saksbehandler = lukketAv,
                            ).right()
                        }
                    }
                }

                /**
                 * Søker har selv trukket søknaden sin.
                 * Dette fører ikke til et vedtak.
                 * Det sendes et informasjonsbrev uten fritekst.
                 *
                 * @param trukketDato Den faktiske datoen brukeren trakk søknaden. Må være før eller lik [avsluttetTidspunkt] og etter eller lik [mottaksdato]
                 */
                data class TrukketAvSøker(
                    override val id: UUID,
                    override val opprettet: Tidspunkt,
                    override val sakId: UUID,
                    override val søknadInnhold: SøknadInnhold,
                    override val journalpostId: JournalpostId,
                    override val oppgaveId: OppgaveId,
                    override val lukketAv: Saksbehandler,
                    val trukketDato: LocalDate,
                    override val innsendtAv: NavIdentBruker,
                    override val lukketTidspunkt: Tidspunkt,
                    override val dokumenttilstand: Dokumenttilstand = Dokumenttilstand.IKKE_GENERERT_ENDA,
                ) : Lukket,
                    Avbrutt {
                    override val avsluttetTidspunkt: Tidspunkt = lukketTidspunkt
                    override val avsluttetAv: NavIdentBruker = lukketAv
                    override val brevvalg =
                        Brevvalg.SkalSendeBrev.InformasjonsbrevUtenFritekst("Saksbehandler får ikke per tidspunkt gjøre noen brevvalg dersom bruker trekker søknaden.")

                    init {
                        require(trukketDato <= avsluttetTidspunkt.toLocalDate(zoneIdOslo)) {
                            "trukketDato $trukketDato må være samtidig eller før avsluttet $avsluttetTidspunkt for søknad $id"
                        }
                        require(trukketDato >= mottaksdato) {
                            "trukketDato $trukketDato må være samtidig eller etter mottaksdato $mottaksdato for søknad $id"
                        }
                        require(avsluttetTidspunkt >= opprettet) {
                            "avsluttet $avsluttetTidspunkt må være samtidig eller etter opprettet $opprettet for søknad $id"
                        }
                    }

                    override fun lagGenererDokumentKommando(
                        hentSak: () -> SakInfo,
                    ): Either<KanIkkeLageBrevRequestForDenneTilstanden, GenererDokumentCommand> =
                        TrukketSøknadDokumentCommand(
                            søknadOpprettet = opprettet,
                            trukketDato = trukketDato,
                            saksbehandler = lukketAv,
                            fødselsnummer = fnr,
                            saksnummer = hentSak().saksnummer,
                            sakstype = hentSak().type,
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
                ) : Lukket,
                    Avbrutt {
                    override val avsluttetTidspunkt: Tidspunkt = lukketTidspunkt
                    override val avsluttetAv: NavIdentBruker = lukketAv
                    override val brevvalg =
                        Brevvalg.SkalIkkeSendeBrev("Saksbehandler får ikke per tidspunkt gjøre noen brevvalg dersom søknaden bortfaller.")
                    override val dokumenttilstand: Dokumenttilstand = Dokumenttilstand.SKAL_IKKE_GENERERE

                    override fun lagGenererDokumentKommando(
                        hentSak: () -> SakInfo,
                    ): Either<KanIkkeLageBrevRequestForDenneTilstanden, GenererDokumentCommand> =
                        KanIkkeLageBrevRequestForDenneTilstanden.left()

                    init {
                        require(lukketTidspunkt >= opprettet)
                    }
                }
            }
        }
    }
}
