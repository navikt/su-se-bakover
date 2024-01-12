package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.left
import arrow.core.right
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.zoneIdOslo
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.command.TrukketSøknadDokumentCommand
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.FantIkkeSak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknad.FantIkkeSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.bortfallSøknad
import no.nav.su.se.bakover.test.dokumentUtenMetadataVedtak
import no.nav.su.se.bakover.test.pdfATom
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.trekkSøknad
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

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
                on { lagDokument(any(), anyOrNull()) } doReturn KunneIkkeLageDokument.FeilVedGenereringAvPdf.left()
            },

        ).let { serviceAndMocks ->

            shouldThrow<IllegalArgumentException> {
                serviceAndMocks.lagBrevutkast()
            }.message shouldBe "Kunne ikke lage brevutkast for lukk søknad med søknadId ${søknad.id} - feil ved generering av brev. Underliggende feil: FeilVedGenereringAvPdf"
            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                serviceAndMocks.verifyHentSøknad()
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
        ).let { serviceAndMocks ->
            serviceAndMocks.lagBrevutkast() shouldBe Pair(sak.fnr, pdfATom())

            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                serviceAndMocks.verifyHentSøknad()
                serviceAndMocks.verifyHentSakIdOgSaksnummer()
                serviceAndMocks.verifyLagBrev() // saksbehandlerNavn = ""
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
            serviceAndMocks.lagBrevutkast() shouldBe Pair(sak.fnr, pdfATom())

            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                serviceAndMocks.verifyHentSøknad()
                serviceAndMocks.verifyLagBrev()
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
            on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadataVedtak().right()
        },
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
            søknadsbehandlingService = søknadsbehandlingService,
            sessionFactory = sessionFactory,
        ).apply { addObserver(lukkSøknadServiceObserver) }

        val allMocks = listOf(
            søknadService,
            sakService,
            brevService,
            oppgaveService,
            søknadsbehandlingService,
            lukkSøknadServiceObserver,
        ).toTypedArray()

        fun lagBrevutkast(): Pair<Fnr, PdfA> = lukkSøknadService.lagBrevutkast(
            lukkSøknadCommand,
        )

        fun verifyHentSøknad(søknadId: UUID = søknad!!.id) {
            verify(søknadService).hentSøknad(argThat { it shouldBe søknadId })
        }

        fun verifyHentSakIdOgSaksnummer() {
            verify(sakService).hentSakidOgSaksnummer(argThat { it shouldBe sak!!.fnr })
        }

        fun verifyLagBrev(
            // saksbehandlerNavn: String = saksbehandlernavn,
        ) {
            verify(brevService).lagDokument(
                argThat {
                    it shouldBe TrukketSøknadDokumentCommand(
                        fødselsnummer = sak!!.fnr,
                        saksnummer = sak.saksnummer,
                        saksbehandler = lukkSøknadCommand.saksbehandler,
                        trukketDato = lukkSøknadCommand.lukketTidspunkt.toLocalDate(zoneIdOslo),
                        søknadOpprettet = søknad!!.opprettet,
                    )
                },
                anyOrNull(),
            )
        }

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(*allMocks)
        }
    }
}
