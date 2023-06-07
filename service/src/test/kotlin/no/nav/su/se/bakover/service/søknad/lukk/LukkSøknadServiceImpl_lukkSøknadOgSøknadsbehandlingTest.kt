package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.startOfDay
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.domain.brev.søknad.lukk.AvvistSøknadBrevRequest
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.FantIkkeSak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.oppdaterSøknadsbehandling
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.avvisSøknadMedBrev
import no.nav.su.se.bakover.test.avvisSøknadUtenBrev
import no.nav.su.se.bakover.test.bortfallSøknad
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nySøknadsbehandlingshendelse
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknad.nySakMedJournalførtSøknadUtenOppgave
import no.nav.su.se.bakover.test.søknad.nySakMedLukketSøknad
import no.nav.su.se.bakover.test.søknad.nySakMedNySøknad
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingTrukket
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.trekkSøknad
import no.nav.su.se.bakover.test.veileder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Clock
import java.util.UUID

internal class LukkSøknadServiceImpl_lukkSøknadOgSøknadsbehandlingTest {

    @Test
    fun `fant ikke søknad`() {
        val søknadId = UUID.randomUUID()
        ServiceOgMocks(
            lukkSøknadCommand = trekkSøknad(søknadId),
            sakService = mock {
                on { hentSakForSøknad(any()) } doReturn FantIkkeSak.left()
            },
        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Fant ikke sak for søknadId $søknadId"

            serviceAndMocks.verifyHentSakForSøknad(søknadId)
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `fant ikke person`() {
        val (sak, søknadsbehandling) = søknadsbehandlingVilkårsvurdertUavklart()
        val søknad = søknadsbehandling.søknad
        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            søknadsbehandling = søknadsbehandling,
            lukkSøknadCommand = trekkSøknad(søknad.id),
            personService = mock {
                on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
            },
        ).let { serviceAndMocks ->
            assertThrows<IllegalStateException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Kunne ikke lukke søknad ${søknad.id} og søknadsbehandling. Underliggende grunn: FantIkkePerson"

            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyHentPerson()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kan ikke sette lukketDato tidligere enn da søknaden var opprettet`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave(
            clock = fixedClockAt(1.februar(2021)),
        )

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(
                søknadId = søknad.id,
                lukketTidspunkt = 1.februar(2021).startOfDay(),
                trukketDato = 20.januar(2021),
            ),
        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "trukketDato 2021-01-20 må være samtidig eller etter mottaksdato 2021-02-01 for søknad ${søknad.id}"

            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `søknadsbehandling til attestering skal ikke bli lukket`() {
        val (sak, søknadsbehandling) = søknadsbehandlingTilAttesteringInnvilget()
        val søknad = sak.søknader.first()

        ServiceOgMocks(
            sak = sak,
            søknadsbehandling = søknadsbehandling,
            søknad = søknad,
            lukkSøknadCommand = bortfallSøknad(
                søknadId = søknad.id,
            ),
        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Kunne ikke lukke søknad ${søknad.id} og søknadsbehandling. Underliggende feil: KanIkkeLukkeEnSøknadsbehandlingTilAttestering"
            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `iverksatt søknadsbehandling skal ikke bli lukket`() {
        val (sak, søknadsbehandling) = søknadsbehandlingIverksattInnvilget()
        val søknad = sak.søknader.first()

        ServiceOgMocks(
            sak = sak,
            søknadsbehandling = søknadsbehandling,
            søknad = søknad,
            lukkSøknadCommand = bortfallSøknad(
                søknadId = søknad.id,
            ),
        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Kunne ikke lukke søknad ${søknad.id} og søknadsbehandling. Underliggende feil: KanIkkeLukkeEnIverksattSøknadsbehandling"

            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `en søknad med lukket søknadsbehandling skal ikke kunne bli lukket igjen`() {
        val (sak, søknadsbehandling) = søknadsbehandlingTrukket()
        val søknad = sak.søknader.first() as Søknad.Journalført.MedOppgave

        ServiceOgMocks(
            sak = sak,
            søknadsbehandling = søknadsbehandling,
            søknad = søknad,
            lukkSøknadCommand = bortfallSøknad(
                søknadId = søknad.id,
            ),
        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Kunne ikke lukke søknad ${søknad.id} og søknadsbehandling. Underliggende feil: KanIkkeLukkeEnAlleredeLukketSøknadsbehandling"

            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `en allerede trukket søknad skal ikke bli trukket`() {
        val søknadId = UUID.randomUUID()
        val lukkSøknadCommand: LukkSøknadCommand = trekkSøknad(
            søknadId = søknadId,
        )
        val (sak, søknad) = nySakMedLukketSøknad(
            søknadId = søknadId,
            lukkSøknadCommand = lukkSøknadCommand,
        )
        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = lukkSøknadCommand,
        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Kunne ikke lukke søknad og søknadsbehandling. Søknaden må være i tilstanden: Søknad.Journalført.MedOppgave.IkkeLukket for sak ${sak.id} og søknad ${søknad.id}"

            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `skal ikke kunne lukke journalført søknad uten oppgave`() {
        val (sak, søknad) = nySakMedJournalførtSøknadUtenOppgave()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(søknad.id),
        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Kunne ikke lukke søknad og søknadsbehandling. Søknaden må være i tilstanden: Søknad.Journalført.MedOppgave.IkkeLukket for sak ${sak.id} og søknad ${søknad.id}"

            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `skal ikke kunne lukke søknad som mangler journalpost og oppgave`() {
        val (sak, søknad) = nySakMedNySøknad()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(søknad.id),
        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Kunne ikke lukke søknad og søknadsbehandling. Søknaden må være i tilstanden: Søknad.Journalført.MedOppgave.IkkeLukket for sak ${sak.id} og søknad ${søknad.id}"

            serviceAndMocks.verifyHentSakForSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `Feil ved generering av brev ved lukking`() {
        val (sak, søknadsbehandling) = søknadsbehandlingVilkårsvurdertUavklart()
        val søknad = sak.søknader.first()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            søknadsbehandling = søknadsbehandling,
            lukkSøknadCommand = trekkSøknad(søknad.id),
            brevService = mock {
                on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
            },
        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lukkSøknad()
            }.message shouldBe "Kunne ikke konvertere LagBrevRequest til dokument ved lukking av søknad ${søknad.id} og søknadsbehandling. Underliggende grunn: KunneIkkeGenererePdf"

            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                serviceAndMocks.verifyHentSakForSøknad()
                serviceAndMocks.verifyHentPerson()
                serviceAndMocks.verifyNavnForNavIdent()
                serviceAndMocks.verifyLagBrev()
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `trekker en søknad uten mangler`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(søknad.id),
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknad() shouldBe serviceAndMocks.expectedSak()
            serviceAndMocks.verifyAll()
        }
    }

    // TODO jah: Slett tilsvarende lukk søknad tester hvis den/de flyttes til regresjonslaget.
    @Test
    fun `lukker avvist søknad uten brev`() {
        val (sak, søknadsbehandling) = søknadsbehandlingVilkårsvurdertUavklart()
        val søknad = sak.søknader.first() as Søknad.Journalført.MedOppgave.IkkeLukket
        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            søknadsbehandling = søknadsbehandling,
            lukkSøknadCommand = avvisSøknadUtenBrev(søknad.id),
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknad() shouldBe serviceAndMocks.expectedSak()
            serviceAndMocks.verifyAll(includeBrev = false)
        }
    }

    @Test
    fun `lukker avvist søknad med brev`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = avvisSøknadMedBrev(
                søknadId = søknad.id,
                brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst("Fritekst"),
            ),
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknad() shouldBe serviceAndMocks.expectedSak()
            serviceAndMocks.verifyAll()
        }
    }

    @Test
    fun `Lukker søknad selvom vi ikke klarte lukke oppgaven`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(
                søknadId = søknad.id,
            ),
            oppgaveService = mock {
                on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
            },

        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknad() shouldBe serviceAndMocks.expectedSak()
            serviceAndMocks.verifyAll()
        }
    }

    private class ServiceOgMocks(
        val sak: Sak? = null,
        private val søknad: Søknad? = null,
        val søknadsbehandling: Søknadsbehandling? = null,
        private val lukkSøknadCommand: LukkSøknadCommand,
        private val sakService: SakService = mock {
            if (sak != null) {
                on { hentSakForSøknad(any()) } doReturn sak.right()
            }
        },
        private val brevService: BrevService = mock {
            on { lagBrev(any()) } doReturn "".toByteArray().right()
        },
        private val oppgaveService: OppgaveService = mock {
            on { lukkOppgave(any()) } doReturn Unit.right()
        },
        private val personService: PersonService = mock {
            if (sak != null) {
                on { hentPerson(any()) } doReturn person(sak.fnr).right()
            }
        },
        private val identClient: IdentClient = mock {
            on { hentNavnForNavIdent(any()) } doReturn "Testbruker, Lokal".right()
        },
        clock: Clock = fixedClock,
        sessionFactory: SessionFactory = TestSessionFactory(),
        private val lukkSøknadServiceObserver: StatistikkEventObserver = mock(),
    ) {
        init {
            søknad?.also {
                require(sak!!.søknader.contains(søknad))
            }
            søknadsbehandling?.also {
                require(sak!!.søknadsbehandlinger.contains(søknadsbehandling))
            }
        }

        private val søknadService: SøknadService = mock()
        private val søknadsbehandlingService: SøknadsbehandlingService = mock()

        private val lukkSøknadService = LukkSøknadServiceImpl(
            søknadService = søknadService,
            sakService = sakService,
            brevService = brevService,
            oppgaveService = oppgaveService,
            personService = personService,
            søknadsbehandlingService = søknadsbehandlingService,
            identClient = identClient,
            clock = clock,
            sessionFactory = sessionFactory,
        ).apply { addObserver(lukkSøknadServiceObserver) }

        fun lukkSøknad(): Sak = lukkSøknadService.lukkSøknad(
            lukkSøknadCommand,
        )

        val allMocks = listOf(
            søknadService,
            sakService,
            brevService,
            oppgaveService,
            personService,
            søknadsbehandlingService,
            lukkSøknadServiceObserver,
            identClient,
        ).toTypedArray()

        fun verifyHentSakForSøknad(søknadId: UUID = søknad!!.id) {
            verify(sakService).hentSakForSøknad(argThat { it shouldBe søknadId })
        }

        fun verifyHentPerson() {
            verify(personService).hentPerson(argThat { it shouldBe sak!!.fnr })
        }

        fun verifyNavnForNavIdent() {
            verify(identClient).hentNavnForNavIdent(argThat { it shouldBe lukkSøknadCommand.saksbehandler })
        }

        fun verifyLagBrev() {
            verify(brevService).lagBrev(
                argThat {
                    it shouldBe when (lukkSøknadCommand) {
                        is LukkSøknadCommand.MedBrev.AvvistSøknad -> AvvistSøknadBrevRequest(
                            person = person(fnr = sak!!.fnr),
                            saksbehandlerNavn = "Testbruker, Lokal",
                            dagensDato = fixedLocalDate,
                            saksnummer = sak.saksnummer,
                            brevvalg = lukkSøknadCommand.brevvalg as Brevvalg.SaksbehandlersValg.SkalSendeBrev,

                        )

                        is LukkSøknadCommand.MedBrev.TrekkSøknad -> TrukketSøknadBrevRequest(
                            person = person(fnr = sak!!.fnr),
                            trukketDato = 1.januar(2021),
                            saksbehandlerNavn = "Testbruker, Lokal",
                            dagensDato = fixedLocalDate,
                            saksnummer = sak.saksnummer,
                            søknadOpprettet = fixedTidspunkt,
                        )

                        is LukkSøknadCommand.UtenBrev -> fail("Skal ikke trigge brevService.lagBrev(...) i dette tilfellet.")
                    }
                },
            )
        }

        fun verifyPersisterLukketSøknad() {
            verify(søknadService).persisterSøknad(
                argThat {
                    it shouldBe expectedLukketSøknad()
                },
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
        }

        fun verifyPersisterLukketSøknadsbehandling() {
            verify(søknadsbehandlingService).persisterSøknadsbehandling(
                argThat {
                    it shouldBe expectedLukketSøknadsbehandling()
                },
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
        }

        fun verifyAll(includeBrev: Boolean = true) {
            inOrder(*allMocks) {
                verifyHentSakForSøknad()
                if (includeBrev) {
                    verifyHentPerson()
                    verifyNavnForNavIdent()
                    verifyLagBrev()
                }
                verifyPersisterLukketSøknad()
                if (søknadsbehandling != null) {
                    verifyPersisterLukketSøknadsbehandling()
                }
                if (includeBrev) {
                    verifyLagreDokument()
                }
                verifyLukkOppgave()
                verifyStatistikkhendelse()
            }
            verifyNoMoreInteractions()
        }

        fun expectedSak(): Sak {
            return sak!!.let {
                if (søknadsbehandling != null) {
                    it.oppdaterSøknadsbehandling(expectedLukketSøknadsbehandling())
                } else {
                    it
                }
            }.copy(søknader = listOf(expectedLukketSøknad()))
        }

        fun expectedLukketSøknadsbehandling() = LukketSøknadsbehandling.createFromPersistedState(
            søknadsbehandling = søknadsbehandling!!,
            søknad = expectedLukketSøknad(),
        ).copy(
            søknadsbehandlingsHistorikk = søknadsbehandling.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                nySøknadsbehandlingshendelse(
                    tidspunkt = fixedTidspunkt,
                    saksbehandler = saksbehandler,
                    handling = SøknadsbehandlingsHandling.Lukket,

                ),
            ),
        )

        fun expectedLukketSøknad(): Søknad.Journalført.MedOppgave.Lukket {
            return when (lukkSøknadCommand) {
                is LukkSøknadCommand.MedBrev.AvvistSøknad -> {
                    Søknad.Journalført.MedOppgave.Lukket.Avvist(
                        id = søknad!!.id,
                        opprettet = søknad.opprettet,
                        sakId = søknad.sakId,
                        søknadInnhold = søknad.søknadInnhold,
                        journalpostId = (søknad as Søknad.Journalført.MedOppgave.IkkeLukket).journalpostId,
                        oppgaveId = søknad.oppgaveId,
                        lukketTidspunkt = fixedTidspunkt,
                        lukketAv = saksbehandler,
                        brevvalg = lukkSøknadCommand.brevvalg,
                        innsendtAv = veileder,
                    )
                }

                is LukkSøknadCommand.MedBrev.TrekkSøknad -> {
                    Søknad.Journalført.MedOppgave.Lukket.TrukketAvSøker(
                        id = søknad!!.id,
                        opprettet = søknad.opprettet,
                        sakId = søknad.sakId,
                        søknadInnhold = søknad.søknadInnhold,
                        journalpostId = (søknad as Søknad.Journalført.MedOppgave.IkkeLukket).journalpostId,
                        oppgaveId = søknad.oppgaveId,
                        lukketTidspunkt = fixedTidspunkt,
                        lukketAv = saksbehandler,
                        trukketDato = lukkSøknadCommand.trukketDato,
                        innsendtAv = veileder,
                    )
                }

                is LukkSøknadCommand.UtenBrev.AvvistSøknad -> {
                    Søknad.Journalført.MedOppgave.Lukket.Avvist(
                        id = søknad!!.id,
                        opprettet = søknad.opprettet,
                        sakId = søknad.sakId,
                        søknadInnhold = søknad.søknadInnhold,
                        journalpostId = (søknad as Søknad.Journalført.MedOppgave.IkkeLukket).journalpostId,
                        oppgaveId = søknad.oppgaveId,
                        lukketTidspunkt = fixedTidspunkt,
                        lukketAv = saksbehandler,
                        brevvalg = Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev(),
                        innsendtAv = veileder,
                    )
                }

                is LukkSøknadCommand.UtenBrev.BortfaltSøknad -> {
                    Søknad.Journalført.MedOppgave.Lukket.Bortfalt(
                        id = søknad!!.id,
                        opprettet = søknad.opprettet,
                        sakId = søknad.sakId,
                        søknadInnhold = søknad.søknadInnhold,
                        journalpostId = (søknad as Søknad.Journalført.MedOppgave.IkkeLukket).journalpostId,
                        oppgaveId = søknad.oppgaveId,
                        lukketTidspunkt = fixedTidspunkt,
                        lukketAv = saksbehandler,
                        innsendtAv = veileder,
                    )
                }
            }
        }

        fun verifyLagreDokument() {
            verify(brevService).lagreDokument(
                argThat { dokument ->
                    dokument should beOfType<Dokument.MedMetadata.Informasjon.Annet>()
                    dokument.tittel shouldBe when (lukkSøknadCommand) {
                        is LukkSøknadCommand.MedBrev.AvvistSøknad -> "Søknaden din om supplerende stønad er avvist"
                        is LukkSøknadCommand.MedBrev.TrekkSøknad -> "Bekrefter at søknad er trukket"
                        is LukkSøknadCommand.UtenBrev -> fail("Bør ikke lagre dokument dersom vi ikke har brev.")
                    }
                    dokument.generertDokument shouldBe "".toByteArray()
                    dokument.generertDokumentJson shouldBe when (lukkSøknadCommand) {
                        is LukkSøknadCommand.MedBrev.AvvistSøknad -> """
                            {"personalia":{"dato":"01.01.2021","fødselsnummer":"${sak!!.fnr}","fornavn":"Tore","etternavn":"Strømøy","saksnummer":12345676},"saksbehandlerNavn":"Testbruker, Lokal","fritekst":"Fritekst","tittel":"Søknaden din om supplerende stønad er avvist","erAldersbrev":false}
                        """.trimIndent()

                        is LukkSøknadCommand.MedBrev.TrekkSøknad -> """
                            {"personalia":{"dato":"01.01.2021","fødselsnummer":"${sak!!.fnr}","fornavn":"Tore","etternavn":"Strømøy","saksnummer":12345676},"datoSøknadOpprettet":"01.01.2021","trukketDato":"01.01.2021","saksbehandlerNavn":"Testbruker, Lokal","erAldersbrev":false}
                        """.trimIndent()

                        is LukkSøknadCommand.UtenBrev -> fail("Bør ikke lagre dokument dersom vi ikke har brev.")
                    }
                    dokument.metadata shouldBe Dokument.Metadata(
                        sakId = sak.id,
                        søknadId = søknad!!.id,
                        vedtakId = null,
                    )
                },
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
        }

        fun verifyLukkOppgave() {
            verify(oppgaveService).lukkOppgave(argThat { it shouldBe (søknad as Søknad.Journalført.MedOppgave.IkkeLukket).oppgaveId })
        }

        fun verifyStatistikkhendelse() {
            if (søknadsbehandling == null) {
                verify(lukkSøknadServiceObserver).handle(
                    argThat {
                        it shouldBe StatistikkEvent.Søknad.Lukket(
                            søknad = expectedLukketSøknad(),
                            saksnummer = sak!!.saksnummer,
                        )
                    },
                )
            } else {
                verify(lukkSøknadServiceObserver).handle(
                    argThat {
                        it shouldBe StatistikkEvent.Behandling.Søknad.Lukket(
                            søknadsbehandling = expectedLukketSøknadsbehandling(),
                            saksbehandler = saksbehandler,
                        )
                    },
                )
            }
        }

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(*allMocks)
        }
    }
}
