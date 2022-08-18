package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevRequest
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknad.FantIkkeSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.bortfallSøknad
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.trekkSøknad
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Clock
import java.util.UUID

private val pdf = "".toByteArray()
private const val saksbehandlernavn = "Testbruker, Lokal"

internal class LukkSøknadServiceImpl_lagBrevutkastTest {

    @Test
    fun `fant ikke søknad`() {
        val søknadId = UUID.randomUUID()
        ServiceOgMocks(
            søknadService = mock {
                on { hentSøknad(any()) } doReturn FantIkkeSøknad.left()
            },
            lukkSøknadCommand = trekkSøknad(søknadId),
        ).let { serviceAndMocks ->

            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lagBrevutkast()
            }.message shouldBe "Kunne ikke lage brevutkast for lukk søknad med søknadId $søknadId - Fant ikke sak. Underliggende feil: FantIkkeSøknad"

            serviceAndMocks.verifyHentSøknad(søknadId)
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med ukjentBrevType når det ikke skal lages brev`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = bortfallSøknad(søknad.id),
        ).let { serviceAndMocks ->
            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lagBrevutkast()
            }.message shouldBe "Kunne ikke lage brevutkast for lukk søknad med søknadId ${søknad.id} - kan ikke lages brev i denne tilstanden. Underliggende feil: KanIkkeLageBrevRequestForDenneTilstanden"
            serviceAndMocks.verifyHentSøknad()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `fant ikke person`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(søknad.id),
            personService = mock {
                on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
            },
        ).let { serviceAndMocks ->
            shouldThrow<RuntimeException> {
                serviceAndMocks.lagBrevutkast()
            }.message shouldBe "Kunne ikke lage brevutkast for lukk søknad med søknadId ${søknad.id} - fant ikke person. Underliggende feil: FantIkkePerson"
            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                serviceAndMocks.verifyHentSøknad()
                serviceAndMocks.verifyHentPerson()
            }
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `fant ikke saksnummer`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(søknad.id),
            sakService = mock {
                on { hentSakidOgSaksnummer(any()) } doReturn FantIkkeSak.left()
            },
        ).let { serviceAndMocks ->
            shouldThrow<RuntimeException> {
                serviceAndMocks.lagBrevutkast()
            }.message shouldBe "Kunne ikke lage brevutkast for lukk søknad med søknadId ${søknad.id} - fant ikke saksnummer. Underliggende feil: FantIkkeSak"
            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                serviceAndMocks.verifyHentSøknad()
                serviceAndMocks.verifyHentPerson()
                serviceAndMocks.verifyHentNavnForNavIdent()
                serviceAndMocks.verifyHentSakIdOgSaksnummer()
            }
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `lager brevutkast klarer ikke lage brev`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(søknad.id),
            brevService = mock {
                on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
            },

        ).let { serviceAndMocks ->

            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lagBrevutkast()
            }.message shouldBe "Kunne ikke lage brevutkast for lukk søknad med søknadId ${søknad.id} - feil ved generering av brev. Underliggende feil: KunneIkkeGenererePDF"
            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                serviceAndMocks.verifyHentSøknad()
                serviceAndMocks.verifyHentPerson()
                serviceAndMocks.verifyHentNavnForNavIdent()
                serviceAndMocks.verifyHentSakIdOgSaksnummer()
                serviceAndMocks.verifyLagBrev()
            }
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `lager brevutkast selvom vi ikke fant navn for nav ident`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(søknad.id),
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn KunneIkkeHenteNavnForNavIdent.FeilVedHentingAvOnBehalfOfToken.left()
            },
        ).let { serviceAndMocks ->
            serviceAndMocks.lagBrevutkast() shouldBe Pair(sak.fnr, pdf)

            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                serviceAndMocks.verifyHentSøknad()
                serviceAndMocks.verifyHentPerson()
                serviceAndMocks.verifyHentNavnForNavIdent()
                serviceAndMocks.verifyHentSakIdOgSaksnummer()
                serviceAndMocks.verifyLagBrev(saksbehandlerNavn = "")
            }
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `lager brevutkast`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        ServiceOgMocks(
            sak = sak,
            søknad = søknad,
            lukkSøknadCommand = trekkSøknad(søknad.id),
        ).let { serviceAndMocks ->
            serviceAndMocks.lagBrevutkast() shouldBe Pair(sak.fnr, pdf)

            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                serviceAndMocks.verifyHentSøknad()
                serviceAndMocks.verifyHentPerson()
                serviceAndMocks.verifyLagBrev()
                serviceAndMocks.verifyHentNavnForNavIdent()
                serviceAndMocks.verifyHentSakIdOgSaksnummer()
                serviceAndMocks.verifyLagBrev()
            }
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    private class ServiceOgMocks(
        val sak: Sak? = null,
        val søknad: Søknad? = null,
        private val lukkSøknadCommand: LukkSøknadCommand,
        private val søknadService: SøknadService = mock {
            if (søknad != null) {
                on { hentSøknad(any()) } doReturn søknad.right()
            }
        },
        private val sakService: SakService = mock {
            if (sak != null) {
                on { hentSakForSøknad(any()) } doReturn sak.right()
                on { hentSakidOgSaksnummer(any()) } doReturn SakInfo(
                    sakId = sak.id,
                    saksnummer = sak.saksnummer,
                    fnr = sak.fnr,
                    type = Sakstype.UFØRE,
                ).right()
            }
        },
        private val brevService: BrevService = mock {
            on { lagBrev(any()) } doReturn pdf.right()
        },
        private val personService: PersonService = mock {
            if (sak != null) {
                on { hentPerson(any()) } doReturn person(sak.fnr).right()
            }
        },
        private val identClient: IdentClient = mock {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandlernavn.right()
        },
        clock: Clock = fixedClock,
        sessionFactory: SessionFactory = TestSessionFactory(),
        private val lukkSøknadServiceObserver: StatistikkEventObserver = mock(),
    ) {
        init {
            søknad?.also {
                require(sak!!.søknader.contains(søknad))
            }
        }

        private val oppgaveService: OppgaveService = mock(defaultAnswer = { fail("Forventes ikke brukt.") })
        private val søknadsbehandlingService: SøknadsbehandlingService = mock(
            defaultAnswer = { fail("Forventes ikke brukt.") },
        )

        val lukkSøknadService = LukkSøknadServiceImpl(
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

        val allMocks = listOf(
            søknadService,
            sakService,
            brevService,
            oppgaveService,
            personService,
            søknadsbehandlingService,
            identClient,
            lukkSøknadServiceObserver,
        ).toTypedArray()

        fun lagBrevutkast(): Pair<Fnr, ByteArray> = lukkSøknadService.lagBrevutkast(
            lukkSøknadCommand,
        )

        fun verifyHentSøknad(søknadId: UUID = søknad!!.id) {
            verify(søknadService).hentSøknad(argThat { it shouldBe søknadId })
        }

        fun verifyHentNavnForNavIdent() {
            verify(identClient).hentNavnForNavIdent(argThat { it shouldBe lukkSøknadCommand.saksbehandler })
        }

        fun verifyHentPerson() {
            verify(personService).hentPerson(argThat { it shouldBe sak!!.fnr })
        }

        fun verifyHentSakIdOgSaksnummer() {
            verify(sakService).hentSakidOgSaksnummer(argThat { it shouldBe sak!!.fnr })
        }

        fun verifyLagBrev(
            saksbehandlerNavn: String = saksbehandlernavn
        ) {
            verify(brevService).lagBrev(
                argThat {
                    it shouldBe TrukketSøknadBrevRequest(
                        person = person(fnr = sak!!.fnr),
                        trukketDato = 1.januar(2021),
                        saksbehandlerNavn = saksbehandlerNavn,
                        dagensDato = fixedLocalDate,
                        saksnummer = sak.saksnummer,
                        søknadOpprettet = fixedTidspunkt,
                    )
                },
            )
        }

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(*allMocks)
        }
    }
}
