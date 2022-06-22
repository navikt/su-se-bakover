package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.client.stubs.person.IdentClientStub
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.Søknad.Journalført.MedOppgave.Lukket.LukketType.AVVIST
import no.nav.su.se.bakover.domain.Søknad.Journalført.MedOppgave.Lukket.LukketType.TRUKKET
import no.nav.su.se.bakover.domain.brev.BrevConfig
import no.nav.su.se.bakover.domain.brev.søknad.lukk.AvvistSøknadBrevRequest
import no.nav.su.se.bakover.domain.brev.søknad.lukk.TrukketSøknadBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLukkeSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.FantIkkeSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySakMedJournalførtSøknadUtenOppgave
import no.nav.su.se.bakover.test.nySakMedNySøknad
import no.nav.su.se.bakover.test.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.søknadinnhold
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingLukket
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.trukketSøknad
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

internal class LukkSøknadServiceImplTest {

    private val saksbehandler = NavIdentBruker.Saksbehandler("Z993156")
    private val generertPdf = "".toByteArray()

    @Test
    fun `fant ikke søknad`() {
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn FantIkkeSøknad.left()
        }
        val søknadId = UUID.randomUUID()
        ServiceOgMocks(
            søknadService = søknadServiceMock,
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.MedBrev.TrekkSøknad(
                    søknadId = søknadId,
                    saksbehandler = saksbehandler,
                    trukketDato = 1.januar(2021),
                ),
            ) shouldBe KunneIkkeLukkeSøknad.FantIkkeSøknad.left()
            verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknadId })
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `fant ikke person`() {
        val (sak, søknadsbehandling) = søknadsbehandlingVilkårsvurdertUavklart()
        val søknad = sak.søknader.first()

        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(any()) } doReturn søknadsbehandling
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        ServiceOgMocks(
            søknadService = søknadServiceMock,
            personService = personServiceMock,
            søknadsbehandlingService = søknadsbehandlingServiceMock,
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.MedBrev.TrekkSøknad(
                    søknadId = søknad.id,
                    saksbehandler = saksbehandler,
                    trukketDato = 1.januar(2021),
                ),
            ) shouldBe KunneIkkeLukkeSøknad.FantIkkePerson.left()

            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknad.id })
                verify(serviceAndMocks.søknadsbehandlingService).hentForSøknad(argThat { it shouldBe søknad.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
            }
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `trekker en søknad uten mangler`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(any()) } doReturn null
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
            on { hentSakidOgSaksnummer(any()) } doReturn SakInfo(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                type = Sakstype.UFØRE,
            ).right()
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn generertPdf.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person(sak.fnr).right()
        }

        ServiceOgMocks(
            søknadService = søknadServiceMock,
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            identClient = IdentClientStub,
        ).let { serviceAndMocks ->
            val actual = serviceAndMocks.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.MedBrev.TrekkSøknad(
                    søknadId = søknad.id,
                    saksbehandler = saksbehandler,
                    trukketDato = 1.januar(2021),
                ),
            ).getOrFail("Uventet feil")

            actual shouldBe sak

            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknad.id })
                verify(serviceAndMocks.søknadsbehandlingService).hentForSøknad(argThat { it shouldBe søknad.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
                verify(sakServiceMock).hentSakidOgSaksnummer(argThat { it shouldBe sak.fnr })
                val expectedRequest = TrukketSøknadBrevRequest(
                    person = person(fnr = sak.fnr),
                    søknad = søknad,
                    trukketDato = 1.januar(2021),
                    saksbehandlerNavn = "Testbruker, Lokal",
                    dagensDato = fixedLocalDate,
                    saksnummer = sak.saksnummer,
                )
                verify(brevServiceMock).lagBrev(expectedRequest)
                verify(søknadServiceMock).lukkSøknad(
                    søknad = argThat {
                        it shouldBe Søknad.Journalført.MedOppgave.Lukket(
                            id = søknad.id,
                            opprettet = søknad.opprettet,
                            sakId = søknad.sakId,
                            søknadInnhold = søknad.søknadInnhold,
                            journalpostId = søknad.journalpostId,
                            oppgaveId = søknad.oppgaveId,
                            lukketAv = saksbehandler,
                            lukketType = TRUKKET,
                            lukketTidspunkt = fixedTidspunkt,
                        )
                    },
                    sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
                )
                verify(serviceAndMocks.brevService).lagreDokument(
                    argThat { dokument ->
                        dokument should beOfType<Dokument.MedMetadata.Informasjon.Annet>()
                        dokument.tittel shouldBe expectedRequest.brevInnhold.brevTemplate.tittel()
                        dokument.generertDokument shouldBe generertPdf
                        dokument.generertDokumentJson shouldBe expectedRequest.brevInnhold.toJson()
                        dokument.metadata shouldBe Dokument.Metadata(
                            sakId = sak.id,
                            søknadId = søknad.id,
                            vedtakId = null,
                            bestillBrev = true,
                        )
                    },
                    argThat { it shouldBe TestSessionFactory.transactionContext },
                )
                verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe søknad.oppgaveId })
                verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe søknad.sakId })
                verify(serviceAndMocks.lukkSøknadServiceObserver).handle(
                    argThat {
                        it shouldBe Event.Statistikk.SøknadStatistikk.SøknadLukket(
                            Søknad.Journalført.MedOppgave.Lukket(
                                id = søknad.id,
                                opprettet = søknad.opprettet,
                                sakId = søknad.sakId,
                                søknadInnhold = søknad.søknadInnhold,
                                journalpostId = søknad.journalpostId,
                                oppgaveId = søknad.oppgaveId,
                                lukketAv = saksbehandler,
                                lukketType = TRUKKET,
                                lukketTidspunkt = fixedTidspunkt,
                            ),
                            sak.saksnummer,
                        )
                    },
                )
            }
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `lukker avvist søknad uten brev`() {
        val (sak, søknadsbehandling) = søknadsbehandlingVilkårsvurdertUavklart()
        val søknad = sak.søknader.first() as Søknad.Journalført.MedOppgave.IkkeLukket
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(any()) } doReturn søknadsbehandling
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person(sak.fnr).right()
        }

        ServiceOgMocks(
            søknadService = søknadServiceMock,
            sakService = sakServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            søknadsbehandlingService = søknadsbehandlingServiceMock,
        ).let { serviceAndMocks ->
            val resultat = serviceAndMocks.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.UtenBrev.AvvistSøknad(
                    søknadId = søknad.id,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail("Uventet feil")

            resultat shouldBe sak

            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknad.id })
                verify(serviceAndMocks.søknadsbehandlingService).hentForSøknad(argThat { it shouldBe søknad.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
                verify(søknadServiceMock).lukkSøknad(
                    argThat {
                        it shouldBe søknad.lukk(
                            lukketAv = saksbehandler,
                            type = AVVIST,
                            lukketTidspunkt = fixedTidspunkt,
                        )
                    },
                    argThat { it shouldBe TestSessionFactory.transactionContext },
                )
                verify(søknadsbehandlingServiceMock).lukk(
                    argThat {
                        it shouldBe LukketSøknadsbehandling.create(
                            lukketSøknadsbehandling = søknadsbehandling,
                        )
                    },
                    argThat { it shouldBe TestSessionFactory.transactionContext },
                )
                verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe søknad.oppgaveId })
                verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe søknad.sakId })

                verify(serviceAndMocks.lukkSøknadServiceObserver).handle(
                    argThat {
                        it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingLukket(
                            søknadsbehandling = søknadsbehandling.lukkSøknadsbehandling().getOrFail(),
                        )
                    },
                )
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `lukker avvist søknad med brev`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(any()) } doReturn null
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
            on { hentSakidOgSaksnummer(any()) } doReturn SakInfo(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                type = Sakstype.UFØRE,
            ).right()
        }

        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn generertPdf.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person(sak.fnr).right()
        }

        ServiceOgMocks(
            søknadService = søknadServiceMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            identClient = IdentClientStub,
            søknadsbehandlingService = søknadsbehandlingServiceMock,
        ).let { serviceAndMocks ->
            val actual = serviceAndMocks.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.MedBrev.AvvistSøknad(
                    søknadId = søknad.id,
                    saksbehandler = saksbehandler,
                    brevConfig = BrevConfig.Fritekst("Fritekst"),
                ),
            ).getOrFail("Uventet feil")

            actual shouldBe sak

            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknad.id })
                verify(søknadsbehandlingServiceMock).hentForSøknad(argThat { it shouldBe søknad.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
                verify(sakServiceMock).hentSakidOgSaksnummer(argThat { it shouldBe sak.fnr })
                val expectedRequest = AvvistSøknadBrevRequest(
                    person = person(fnr = sak.fnr),
                    brevConfig = BrevConfig.Fritekst(
                        fritekst = "Fritekst",
                    ),
                    saksbehandlerNavn = "Testbruker, Lokal",
                    dagensDato = fixedLocalDate,
                    saksnummer = sak.saksnummer,
                )
                verify(brevServiceMock).lagBrev(expectedRequest)
                verify(søknadServiceMock).lukkSøknad(
                    argThat {
                        it shouldBe Søknad.Journalført.MedOppgave.Lukket(
                            id = søknad.id,
                            opprettet = søknad.opprettet,
                            sakId = søknad.sakId,
                            søknadInnhold = søknad.søknadInnhold,
                            journalpostId = søknad.journalpostId,
                            oppgaveId = søknad.oppgaveId,
                            lukketAv = saksbehandler,
                            lukketType = AVVIST,
                            lukketTidspunkt = fixedTidspunkt,
                        )
                    },
                    argThat { it shouldBe TestSessionFactory.transactionContext },
                )
                verify(serviceAndMocks.brevService).lagreDokument(
                    argThat { dokument ->
                        dokument should beOfType<Dokument.MedMetadata.Informasjon.Annet>()
                        dokument.tittel shouldBe expectedRequest.brevInnhold.brevTemplate.tittel()
                        dokument.generertDokument shouldBe generertPdf
                        dokument.generertDokumentJson shouldBe expectedRequest.brevInnhold.toJson()
                        dokument.metadata shouldBe Dokument.Metadata(
                            sakId = sak.id,
                            søknadId = søknad.id,
                            vedtakId = null,
                            bestillBrev = true,
                        )
                    },
                    argThat { it shouldBe TestSessionFactory.transactionContext },
                )
                verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe søknad.oppgaveId })
                verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe søknad.sakId })
                verify(serviceAndMocks.lukkSøknadServiceObserver).handle(
                    argThat {
                        it shouldBe Event.Statistikk.SøknadStatistikk.SøknadLukket(
                            Søknad.Journalført.MedOppgave.Lukket(
                                id = søknad.id,
                                opprettet = søknad.opprettet,
                                sakId = søknad.sakId,
                                søknadInnhold = søknad.søknadInnhold,
                                journalpostId = søknad.journalpostId,
                                oppgaveId = søknad.oppgaveId,
                                lukketAv = saksbehandler,
                                lukketType = AVVIST,
                                lukketTidspunkt = fixedTidspunkt,
                            ),
                            sak.saksnummer,
                        )
                    },
                )
            }
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kan ikke sette lukketDato tidligere enn da søknaden var opprettet`() {
        val treDagerGammelSøknad = Søknad.Ny(
            sakId = UUID.randomUUID(),
            id = UUID.randomUUID(),
            opprettet = LocalDateTime.now().minusDays(3).toTidspunkt(zoneIdOslo),
            søknadInnhold = søknadinnhold(),
        )
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn treDagerGammelSøknad.right()
        }

        ServiceOgMocks(
            søknadService = søknadServiceMock,
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.MedBrev.TrekkSøknad(
                    søknadId = treDagerGammelSøknad.id,
                    saksbehandler = saksbehandler,
                    trukketDato = fixedLocalDate.minusDays(4),
                ),
            ) shouldBe KunneIkkeLukkeSøknad.UgyldigTrukketDato.left()

            verify(søknadServiceMock).hentSøknad(argThat { it shouldBe treDagerGammelSøknad.id })
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `en søknad med søknadsbehandling til attestering skal ikke bli lukket`() {
        val (sak, søknadsbehandling) = søknadsbehandlingTilAttesteringInnvilget()
        val søknad = sak.søknader.first()
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(any()) } doReturn søknadsbehandling
        }
        ServiceOgMocks(
            søknadService = søknadServiceMock,
            søknadsbehandlingService = søknadsbehandlingServiceMock,
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                    søknadId = søknad.id,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe KunneIkkeLukkeSøknad.BehandlingErIFeilTilstand(KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnSøknadsbehandlingTilAttestering)
                .left()

            verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknad.id })
            verify(serviceAndMocks.søknadsbehandlingService).hentForSøknad(argThat { it shouldBe søknad.id })
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `en søknad med iverksatt søknadsbehandling skal ikke bli lukket`() {
        val (sak, søknadsbehandling) = søknadsbehandlingIverksattInnvilget()
        val søknad = sak.søknader.first()
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(any()) } doReturn søknadsbehandling
        }
        ServiceOgMocks(
            søknadService = søknadServiceMock,
            søknadsbehandlingService = søknadsbehandlingServiceMock,
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                    søknadId = søknad.id,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe KunneIkkeLukkeSøknad.BehandlingErIFeilTilstand(KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnIverksattSøknadsbehandling)
                .left()

            verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknad.id })
            verify(serviceAndMocks.søknadsbehandlingService).hentForSøknad(argThat { it shouldBe søknad.id })
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `en søknad med lukket søknadsbehandling skal ikke kunne bli lukket igjen`() {
        val (sak, søknadsbehandling) = søknadsbehandlingLukket()
        val søknad = sak.søknader.first() as Søknad.Journalført.MedOppgave
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(any()) } doReturn søknadsbehandling
        }
        ServiceOgMocks(
            søknadService = søknadServiceMock,
            søknadsbehandlingService = søknadsbehandlingServiceMock,
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                    søknadId = søknad.id,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe KunneIkkeLukkeSøknad.BehandlingErIFeilTilstand(KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnAlleredeLukketSøknadsbehandling)
                .left()

            verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknad.id })
            verify(serviceAndMocks.søknadsbehandlingService).hentForSøknad(argThat { it shouldBe søknad.id })
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `en allerede trukket søknad skal ikke bli trukket`() {
        val trukketSøknad = trukketSøknad
        val trekkSøknadRequest = LukkSøknadRequest.MedBrev.TrekkSøknad(
            søknadId = trukketSøknad.id,
            saksbehandler = saksbehandler,
            trukketDato = 1.januar(2021),
        )
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn trukketSøknad.right()
        }
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(any()) } doReturn null
        }
        ServiceOgMocks(
            søknadService = søknadServiceMock,
            søknadsbehandlingService = søknadsbehandlingServiceMock,
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknadService.lukkSøknad(trekkSøknadRequest) shouldBe KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()

            verify(søknadServiceMock).hentSøknad(argThat { it shouldBe trukketSøknad.id })
            verify(serviceAndMocks.søknadsbehandlingService).hentForSøknad(argThat { it shouldBe trukketSøknad.id })
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `lager brevutkast`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()
        val pdf = "".toByteArray()
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn pdf.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person(sak.fnr).right()
        }

        val sakServiceMock = mock<SakService> {
            on { hentSakidOgSaksnummer(any()) } doReturn SakInfo(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                type = Sakstype.UFØRE,
            ).right()
        }

        ServiceOgMocks(
            søknadService = søknadServiceMock,
            brevService = brevServiceMock,
            personService = personServiceMock,
            sakService = sakServiceMock,
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknadService.lagBrevutkast(
                LukkSøknadRequest.MedBrev.TrekkSøknad(
                    søknadId = søknad.id,
                    saksbehandler = saksbehandler,
                    trukketDato = 1.januar(2021),
                ),
            ) shouldBe pdf.right()

            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknad.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
                verify(sakServiceMock).hentSakidOgSaksnummer(argThat { it shouldBe sak.fnr })

                verify(brevServiceMock).lagBrev(
                    argThat {
                        it shouldBe TrukketSøknadBrevRequest(
                            person = person(fnr = sak.fnr),
                            søknad = søknad,
                            trukketDato = 1.januar(2021),
                            saksbehandlerNavn = "Testbruker, Lokal",
                            dagensDato = fixedLocalDate,
                            saksnummer = sak.saksnummer,
                        )
                    },
                )
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `lager brevutkast finner ikke person`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        ServiceOgMocks(
            søknadService = søknadServiceMock,
            personService = personServiceMock,
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknadService.lagBrevutkast(
                LukkSøknadRequest.MedBrev.TrekkSøknad(
                    søknadId = søknad.id,
                    saksbehandler = saksbehandler,
                    trukketDato = 1.januar(2021),
                ),
            ) shouldBe KunneIkkeLageBrevutkast.FantIkkePerson.left()

            inOrder(
                søknadServiceMock,
                personServiceMock,
            ) {
                verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknad.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `lager brevutkast finner ikke søknad`() {
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn FantIkkeSøknad.left()
        }
        ServiceOgMocks(
            søknadService = søknadServiceMock,
        ).let { serviceAndMocks ->
            val søknadId = UUID.randomUUID()
            serviceAndMocks.lukkSøknadService.lagBrevutkast(
                LukkSøknadRequest.MedBrev.TrekkSøknad(
                    søknadId = søknadId,
                    saksbehandler = saksbehandler,
                    trukketDato = 1.januar(2021),
                ),
            ) shouldBe KunneIkkeLageBrevutkast.FantIkkeSøknad.left()

            verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknadId })
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `lager brevutkast klarer ikke lage brev`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person(sak.fnr).right()
        }
        val sakServiceMock = mock<SakService> {
            on { hentSakidOgSaksnummer(any()) } doReturn SakInfo(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                type = Sakstype.UFØRE,
            ).right()
        }
        ServiceOgMocks(
            søknadService = søknadServiceMock,
            brevService = brevServiceMock,
            personService = personServiceMock,
            sakService = sakServiceMock,
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknadService.lagBrevutkast(
                LukkSøknadRequest.MedBrev.TrekkSøknad(
                    søknadId = søknad.id,
                    saksbehandler = saksbehandler,
                    trukketDato = 1.januar(2021),
                ),
            ) shouldBe KunneIkkeLageBrevutkast.KunneIkkeLageBrev.left()

            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknad.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
                verify(sakServiceMock).hentSakidOgSaksnummer(argThat { it shouldBe sak.fnr })
                verify(brevServiceMock).lagBrev(
                    argThat {
                        it shouldBe TrukketSøknadBrevRequest(
                            person(fnr = sak.fnr),
                            søknad,
                            1.januar(2021),
                            "Testbruker, Lokal",
                            dagensDato = fixedLocalDate,
                            saksnummer = sak.saksnummer,
                        )
                    },
                )
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `svarer med ukjentBrevType når det ikke skal lages brev`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person(sak.fnr).right()
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
            on { hentSakidOgSaksnummer(any()) } doReturn SakInfo(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                type = Sakstype.UFØRE,
            ).right()
        }
        ServiceOgMocks(
            søknadService = søknadServiceMock,
            personService = personServiceMock,
            sakService = sakServiceMock,
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknadService.lagBrevutkast(
                LukkSøknadRequest.UtenBrev.BortfaltSøknad(
                    søknadId = søknad.id,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe KunneIkkeLageBrevutkast.UkjentBrevtype.left()

            inOrder(
                søknadServiceMock,
                personServiceMock,
                sakServiceMock,
            ) {
                verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknad.id })
                verify(personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
                verify(sakServiceMock).hentSakidOgSaksnummer(argThat { it shouldBe sak.fnr })
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `skal ikke kunne lukke journalført søknad uten oppgave`() {
        val søknad = nySakMedJournalførtSøknadUtenOppgave().second

        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(any()) } doReturn null
        }
        ServiceOgMocks(
            søknadService = søknadServiceMock,
            søknadsbehandlingService = søknadsbehandlingServiceMock,
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.MedBrev.TrekkSøknad(
                    søknadId = søknad.id,
                    saksbehandler = saksbehandler,
                    trukketDato = 1.januar(2021),
                ),
            ) shouldBe KunneIkkeLukkeSøknad.SøknadManglerOppgave.left()

            verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknad.id })
            verify(serviceAndMocks.søknadsbehandlingService).hentForSøknad(argThat { it shouldBe søknad.id })
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `skal ikke kunne lukke søknad som mangler journalpost og oppgave`() {
        val nySøknad = nySakMedNySøknad().second
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn nySøknad.right()
        }
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(any()) } doReturn null
        }
        ServiceOgMocks(
            søknadService = søknadServiceMock,
            søknadsbehandlingService = søknadsbehandlingServiceMock,
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.MedBrev.TrekkSøknad(
                    søknadId = nySøknad.id,
                    saksbehandler = saksbehandler,
                    trukketDato = 1.januar(2021),
                ),
            ) shouldBe KunneIkkeLukkeSøknad.SøknadManglerOppgave.left()

            verify(søknadServiceMock).hentSøknad(argThat { it shouldBe nySøknad.id })
            verify(søknadsbehandlingServiceMock).hentForSøknad(argThat { it shouldBe nySøknad.id })
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `Lukker søknad selvom vi ikke klarte lukke oppgaven`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(any()) } doReturn null
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
            on { hentSakidOgSaksnummer(any()) } doReturn SakInfo(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                type = Sakstype.UFØRE,
            ).right()
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn generertPdf.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person(sak.fnr).right()
        }

        ServiceOgMocks(
            søknadService = søknadServiceMock,
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            sakService = sakServiceMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            identClient = IdentClientStub,
        ).let { serviceAndMocks ->
            val actual = serviceAndMocks.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.MedBrev.TrekkSøknad(
                    søknadId = søknad.id,
                    saksbehandler = saksbehandler,
                    trukketDato = 1.januar(2021),
                ),
            )
                .getOrFail("Ukjent feil")

            actual shouldBe sak

            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknad.id })
                verify(søknadsbehandlingServiceMock).hentForSøknad(argThat { it shouldBe søknad.id })

                verify(personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
                verify(sakServiceMock).hentSakidOgSaksnummer(argThat { it shouldBe sak.fnr })
                val expectedRequest = TrukketSøknadBrevRequest(
                    person = person(fnr = sak.fnr),
                    søknad = søknad,
                    trukketDato = 1.januar(2021),
                    saksbehandlerNavn = "Testbruker, Lokal",
                    dagensDato = fixedLocalDate,
                    saksnummer = sak.saksnummer,
                )
                verify(brevServiceMock).lagBrev(expectedRequest)
                verify(søknadServiceMock).lukkSøknad(
                    argThat {
                        it shouldBe Søknad.Journalført.MedOppgave.Lukket(
                            id = søknad.id,
                            opprettet = søknad.opprettet,
                            sakId = søknad.sakId,
                            søknadInnhold = søknad.søknadInnhold,
                            journalpostId = søknad.journalpostId,
                            oppgaveId = søknad.oppgaveId,
                            lukketAv = saksbehandler,
                            lukketType = TRUKKET,
                            lukketTidspunkt = fixedTidspunkt,
                        )
                    },
                    argThat { it shouldBe TestSessionFactory.transactionContext },
                )
                verify(serviceAndMocks.brevService).lagreDokument(
                    argThat { dokument ->
                        dokument should beOfType<Dokument.MedMetadata.Informasjon.Annet>()
                        dokument.tittel shouldBe expectedRequest.brevInnhold.brevTemplate.tittel()
                        dokument.generertDokument shouldBe generertPdf
                        dokument.generertDokumentJson shouldBe expectedRequest.brevInnhold.toJson()
                        dokument.metadata shouldBe Dokument.Metadata(
                            sakId = sak.id,
                            søknadId = søknad.id,
                            vedtakId = null,
                            bestillBrev = true,
                        )
                    },
                    argThat { it shouldBe TestSessionFactory.transactionContext },
                )
                verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe søknad.oppgaveId })
                verify(sakServiceMock).hentSak(argThat<UUID> { it shouldBe søknad.sakId })
                verify(serviceAndMocks.lukkSøknadServiceObserver).handle(
                    argThat {
                        it shouldBe Event.Statistikk.SøknadStatistikk.SøknadLukket(
                            Søknad.Journalført.MedOppgave.Lukket(
                                id = søknad.id,
                                opprettet = søknad.opprettet,
                                sakId = søknad.sakId,
                                søknadInnhold = søknad.søknadInnhold,
                                journalpostId = søknad.journalpostId,
                                oppgaveId = søknad.oppgaveId,
                                lukketAv = saksbehandler,
                                lukketType = TRUKKET,
                                lukketTidspunkt = fixedTidspunkt,
                            ),
                            sak.saksnummer,
                        )
                    },
                )
            }
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `Feil ved generering av brev ved lukking`() {
        val (sak, søknadsbehandling) = søknadsbehandlingVilkårsvurdertUavklart()
        val søknad = sak.søknader.first()
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person(sak.fnr).right()
        }
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(any()) } doReturn søknadsbehandling
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
            on { hentSakidOgSaksnummer(any()) } doReturn SakInfo(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                type = Sakstype.UFØRE,
            ).right()
        }
        ServiceOgMocks(
            søknadService = søknadServiceMock,
            brevService = brevServiceMock,
            personService = personServiceMock,
            identClient = IdentClientStub,
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            sakService = sakServiceMock,
        ).let { serviceAndMocks ->
            serviceAndMocks.lukkSøknadService.lukkSøknad(
                LukkSøknadRequest.MedBrev.TrekkSøknad(
                    søknadId = søknad.id,
                    saksbehandler = saksbehandler,
                    trukketDato = 1.januar(2021),
                ),
            ) shouldBe KunneIkkeLukkeSøknad.KunneIkkeGenerereDokument.left()

            inOrder(
                *serviceAndMocks.allMocks,
            ) {
                verify(søknadServiceMock).hentSøknad(argThat { it shouldBe søknad.id })
                verify(serviceAndMocks.søknadsbehandlingService).hentForSøknad(argThat { it shouldBe søknad.id })

                verify(personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
                verify(sakServiceMock).hentSakidOgSaksnummer(argThat { it shouldBe sak.fnr })
                verify(brevServiceMock).lagBrev(
                    TrukketSøknadBrevRequest(
                        person = person(fnr = sak.fnr),
                        søknad = søknad,
                        trukketDato = 1.januar(2021),
                        saksbehandlerNavn = "Testbruker, Lokal",
                        dagensDato = fixedLocalDate,
                        saksnummer = sak.saksnummer,
                    ),
                )
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    private class ServiceOgMocks(
        val søknadService: SøknadService = mock(),
        val sakService: SakService = mock(),
        val brevService: BrevService = mock(),
        val oppgaveService: OppgaveService = mock(),
        val personService: PersonService = mock(),
        val søknadsbehandlingService: SøknadsbehandlingService = mock(),
        val identClient: IdentClient = IdentClientStub,
        clock: Clock = fixedClock,
        sessionFactory: SessionFactory = TestSessionFactory(),
        val lukkSøknadServiceObserver: EventObserver = mock(),
    ) {
        val lukkSøknadService = LukkSøknadServiceImpl(
            søknadService = søknadService,
            sakService = sakService,
            brevService = brevService,
            oppgaveService = oppgaveService,
            personService = personService,
            identClient = identClient,
            clock = clock,
            sessionFactory = sessionFactory,
            søknadsbehandlingService = søknadsbehandlingService,
        ).apply { addObserver(lukkSøknadServiceObserver) }

        val allMocks = listOf(
            søknadService,
            sakService,
            brevService,
            oppgaveService,
            personService,
            søknadsbehandlingService,
            lukkSøknadServiceObserver,
        ).toTypedArray()

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(*allMocks)
        }
    }
}
