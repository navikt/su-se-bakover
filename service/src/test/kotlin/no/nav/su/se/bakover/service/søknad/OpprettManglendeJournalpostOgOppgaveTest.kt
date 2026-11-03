package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import dokument.domain.KunneIkkeGenererePdf
import dokument.domain.forsteside.PostForstesideResponse
import dokument.domain.generer.SammenslåPdf
import dokument.domain.journalføring.søknad.JournalførSøknadCommand
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.sak.FantIkkeSak
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.opprett.opprettNySøknadsbehandling
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.oppgave.oppgaveId
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.veileder
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import person.domain.Person
import økonomi.domain.utbetaling.Utbetalinger
import java.time.Year
import java.time.temporal.ChronoUnit
import java.util.UUID

class OpprettManglendeJournalpostOgOppgaveTest {
    private val søknadInnhold = søknadinnholdUføre()
    private val sakId = UUID.randomUUID()
    private val nySøknad = Søknad.Ny(
        sakId = sakId,
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.EPOCH,
        søknadInnhold = søknadInnhold,
        innsendtAv = veileder,
    )
    private val fnr = Fnr.generer()
    private val journalførtSøknad = nySøknad.journalfør(JournalpostId("1"))
    private val sak = Sak(
        id = sakId,
        saksnummer = Saksnummer(2021),
        opprettet = Tidspunkt.EPOCH,
        fnr = fnr,
        søknader = emptyList(),
        utbetalinger = Utbetalinger(),
        type = Sakstype.UFØRE,
        versjon = Hendelsesversjon(1),
        uteståendeKravgrunnlag = null,
    )
    private val person = Person(
        ident = Ident(
            fnr = fnr,
            aktørId = AktørId(aktørId = "123"),
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
        fødsel = Person.Fødsel.MedFødselsår(
            år = Year.of(1956),
        ),
    )
    private val pdfBytes = requireNotNull(javaClass.classLoader.getResourceAsStream("FoerstesideSoknadUfor.pdf")).use { it.readAllBytes() }
    private val førstesideResponse = PostForstesideResponse(
        foersteside = pdfBytes,
        løpenummer = "løpenummer",
    )
    private val sammenslåttPdf = SammenslåPdf.leggTilVedlegg(dokument = PdfA(pdfBytes), vedlegg = pdfBytes).getOrFail()
    private val opprettetFør = fixedTidspunkt.minus(5, ChronoUnit.MINUTES)

    @Test
    fun `ingen søknader`() {
        SøknadServiceOgMocks(
            mock {
                on { hentSøknaderUtenJournalpost() } doReturn emptyList()
                on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn emptyList()
            },
        ).also {
            it.service.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling() shouldBe OpprettManglendeJournalpostOgOppgaveOgSøknadsbehandlingResultat(
                emptyList(),
                emptyList(),
                emptyList(),
            )
            inOrder(*it.allMocks()) {
                verify(it.søknadRepo).hentSøknaderUtenJournalpost()
                verify(it.søknadRepo).hentSøknaderMedJournalpostMenUtenOppgave()
                verify(it.søknadRepo).hentSøknaderUtenBehandling(opprettetFør)
            }
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `finner ikke saken`() {
        SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknaderUtenJournalpost() } doReturn listOf(nySøknad)
                on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn listOf(journalførtSøknad)
                on { hentSøknaderUtenBehandling(opprettetFør) } doReturn listOf(journalførtSøknad.medOppgave(oppgaveId))
            },
            sakService = mock {
                on { hentSak(sakId) } doReturn FantIkkeSak.left()
            },
        ).also {
            it.service.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling() shouldBe OpprettManglendeJournalpostOgOppgaveOgSøknadsbehandlingResultat(
                journalpostResultat = listOf(KunneIkkeOppretteJournalpost(sakId, nySøknad.id, "Fant ikke sak").left()),
                oppgaveResultat = listOf(
                    KunneIkkeOppretteOppgave(
                        sakId,
                        nySøknad.id,
                        journalførtSøknad.journalpostId,
                        "Fant ikke sak",
                    ).left(),
                ),
                behandlingResultat = listOf(
                    KunneIkkeOppretteManglendeSøknadsbehandling(sakId, nySøknad.id, "Fant ikke sak").left(),
                ),
            )

            inOrder(*it.allMocks()) {
                verify(it.søknadRepo).hentSøknaderUtenJournalpost()
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.søknadRepo).hentSøknaderMedJournalpostMenUtenOppgave()
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.søknadRepo).hentSøknaderUtenBehandling(opprettetFør)
                verify(it.sakService).hentSak(sakId)
            }
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `oppretter manglende behandling og statistikk i samme transaksjon`() {
        val søknad = journalførtSøknad.medOppgave(oppgaveId)
        val observer = mock<StatistikkEventObserver>()
        val serviceOgMocks = SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknaderUtenBehandling(opprettetFør) } doReturn listOf(søknad)
            },
            sakService = mock {
                on { hentSak(sakId) } doReturn sak.right()
            },
            søknadsbehandlingRepo = mock(),
        )
        serviceOgMocks.service.addObserver(observer)

        val resultat = serviceOgMocks.service.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling()
        val behandling = resultat.behandlingResultat.single().getOrFail()
            .shouldBeTypeOf<VilkårsvurdertSøknadsbehandling.Uavklart>()
        behandling.søknad shouldBe søknad
        behandling.oppgaveId shouldBe søknad.oppgaveId
        behandling.saksbehandler shouldBe null
        behandling.opprettet shouldBe fixedTidspunkt
        val event = StatistikkEvent.Behandling.Søknad.Opprettet(behandling, NavIdentBruker.Saksbehandler.systembruker())
        val transactionContext = argumentCaptor<TransactionContext>()
        inOrder(serviceOgMocks.søknadRepo, serviceOgMocks.søknadsbehandlingRepo, serviceOgMocks.sakStatistikkService, observer) {
            verify(serviceOgMocks.søknadsbehandlingRepo).hentForSøknad(søknad.id)
            verify(serviceOgMocks.søknadsbehandlingRepo).lagre(eq(behandling), transactionContext.capture())
            verify(serviceOgMocks.sakStatistikkService).lagre(event, transactionContext.firstValue)
            verify(observer).handle(event, transactionContext.firstValue)
        }
        verifyNoInteractions(serviceOgMocks.oppgaveService, serviceOgMocks.journalførSøknadClient)
    }

    @Test
    fun `oppretter ikke behandling eller statistikk på nytt hvis en annen kjøring allerede har reparert søknaden`() {
        val søknad = journalførtSøknad.medOppgave(oppgaveId)
        val behandling = sak.opprettNySøknadsbehandling(
            søknad = søknad,
            clock = fixedClock,
            saksbehandler = null,
        ).getOrFail().second
        val observer = mock<StatistikkEventObserver>()
        val serviceOgMocks = SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknaderUtenBehandling(opprettetFør) } doReturn listOf(søknad)
            },
            sakService = mock {
                on { hentSak(sakId) } doReturn sak.right()
            },
            søknadsbehandlingRepo = mock {
                on { hentForSøknad(søknad.id) } doReturn behandling
            },
        )
        serviceOgMocks.service.addObserver(observer)

        serviceOgMocks.service.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling().behandlingResultat shouldBe listOf(behandling.right())

        verify(serviceOgMocks.søknadsbehandlingRepo, never()).lagre(any(), any())
        verifyNoInteractions(serviceOgMocks.sakStatistikkService, observer)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, 5])
    fun `utsetter journalføring og oppgaveopprettelse til søknadene er eldre enn fem minutter`(alderIMinutter: Long) {
        val opprettet = fixedTidspunkt.minus(alderIMinutter, ChronoUnit.MINUTES)
        val søknadUtenJournalpost = nySøknad.copy(opprettet = opprettet)
        val søknadUtenOppgave = nySøknad.copy(id = UUID.randomUUID(), opprettet = opprettet).journalfør(journalførtSøknad.journalpostId)
        val serviceOgMocks = SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknaderUtenJournalpost() } doReturn listOf(søknadUtenJournalpost)
                on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn listOf(søknadUtenOppgave)
            },
        )

        serviceOgMocks.service.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling() shouldBe OpprettManglendeJournalpostOgOppgaveOgSøknadsbehandlingResultat(
            journalpostResultat = emptyList(),
            oppgaveResultat = emptyList(),
            behandlingResultat = emptyList(),
        )
        verify(serviceOgMocks.søknadRepo).hentSøknaderUtenBehandling(opprettetFør)
        verifyNoInteractions(
            serviceOgMocks.sakService,
            serviceOgMocks.journalførSøknadClient,
            serviceOgMocks.oppgaveService,
            serviceOgMocks.søknadsbehandlingRepo,
            serviceOgMocks.sakStatistikkService,
        )
    }

    @Test
    fun `opprett journalpost feiler`() {
        SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknaderUtenJournalpost() } doReturn listOf(nySøknad)
                on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn emptyList()
                on { hentSøknaderUtenBehandling(opprettetFør) } doReturn listOf(nySøknad)
            },
            sakService = mock {
                on { hentSak(sakId) } doReturn sak.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(eq(fnr), any()) } doReturn person.right()
            },
            pdfGenerator = mock {
                on { genererPdf(any<SøknadPdfInnhold>()) } doReturn KunneIkkeGenererePdf.left()
            },
        ).also {
            it.service.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling() shouldBe OpprettManglendeJournalpostOgOppgaveOgSøknadsbehandlingResultat(
                journalpostResultat = listOf(
                    KunneIkkeOppretteJournalpost(
                        sakId,
                        nySøknad.id,
                        "Kunne ikke generere PDF",
                    ).left(),
                ),
                oppgaveResultat = emptyList(),
                behandlingResultat = emptyList(),
            )
            inOrder(*it.allMocks()) {
                verify(it.søknadRepo).hentSøknaderUtenJournalpost()
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.personService).hentPersonMedSystembruker(argThat { it shouldBe fnr }, eq(Sakstype.UFØRE))
                verify(it.pdfGenerator).genererPdf(
                    argThat<SøknadPdfInnhold> {
                        it shouldBe SøknadPdfInnhold.create(
                            saksnummer = sak.saksnummer,
                            sakstype = sak.type,
                            søknadsId = it.søknadsId,
                            navn = person.navn,
                            søknadOpprettet = nySøknad.opprettet,
                            søknadInnhold = søknadInnhold,
                            clock = fixedClock,
                        )
                    },
                )
                verify(it.søknadRepo).hentSøknaderMedJournalpostMenUtenOppgave()
                verify(it.søknadRepo).hentSøknaderUtenBehandling(opprettetFør)
            }
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `opprett oppgave feiler`() {
        SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknaderUtenJournalpost() } doReturn emptyList()
                on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn listOf(journalførtSøknad)
                on { hentSøknaderUtenBehandling(opprettetFør) } doReturn listOf(journalførtSøknad)
            },
            sakService = mock {
                on { hentSak(sakId) } doReturn sak.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(eq(fnr), any()) } doReturn person.right()
            },
            oppgaveService = mock {
                on { opprettOppgaveMedSystembruker(any()) } doReturn no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave.left()
            },
        ).also {
            it.service.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling() shouldBe OpprettManglendeJournalpostOgOppgaveOgSøknadsbehandlingResultat(
                journalpostResultat = emptyList(),
                oppgaveResultat = listOf(
                    KunneIkkeOppretteOppgave(
                        sakId,
                        journalførtSøknad.id,
                        journalførtSøknad.journalpostId,
                        "Kunne ikke opprette oppgave",
                    ).left(),
                ),
                behandlingResultat = emptyList(),
            )

            inOrder(*it.allMocks()) {
                verify(it.søknadRepo).hentSøknaderUtenJournalpost()
                verify(it.søknadRepo).hentSøknaderMedJournalpostMenUtenOppgave()
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.oppgaveService).opprettOppgaveMedSystembruker(
                    argThat {
                        it shouldBe OppgaveConfig.Søknad(
                            sakstype = Sakstype.UFØRE,
                            journalpostId = journalførtSøknad.journalpostId,
                            saksnummer = sak.saksnummer,
                            fnr = person.ident.fnr,
                            clock = fixedClock,
                            tilordnetRessurs = null,
                        )
                    },
                )
                verify(it.søknadRepo).hentSøknaderUtenBehandling(opprettetFør)
            }
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy case`() {
        val oppgaveId = oppgaveId
        val pdf = PdfA(pdfBytes)

        SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknaderUtenJournalpost() } doReturn listOf(nySøknad)
                on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn listOf(journalførtSøknad)
            },
            sakService = mock {
                on { hentSak(sakId) } doReturn sak.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(eq(fnr), any()) } doReturn person.right()
            },
            oppgaveService = mock {
                on { opprettOppgaveMedSystembruker(any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
            pdfGenerator = mock {
                on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
            },
            forstesideGeneratorService = mock {
                on { genererForSøknadUføre(any()) } doReturn førstesideResponse.right()
            },
            journalførSøknadClient = mock {
                on { journalførSøknad(any()) } doReturn journalførtSøknad.journalpostId.right()
            },
        ).also {
            it.service.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling() shouldBe OpprettManglendeJournalpostOgOppgaveOgSøknadsbehandlingResultat(
                journalpostResultat = listOf(journalførtSøknad.right()),
                oppgaveResultat = listOf(journalførtSøknad.medOppgave(oppgaveId).right()),
                behandlingResultat = emptyList(),
            )

            inOrder(*it.allMocks()) {
                verify(it.søknadRepo).hentSøknaderUtenJournalpost()
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.personService).hentPersonMedSystembruker(argThat { it shouldBe fnr }, eq(Sakstype.UFØRE))
                verify(it.pdfGenerator).genererPdf(
                    argThat<SøknadPdfInnhold> {
                        it shouldBe SøknadPdfInnhold.create(
                            saksnummer = sak.saksnummer,
                            sakstype = sak.type,
                            søknadsId = it.søknadsId,
                            navn = person.navn,
                            søknadOpprettet = nySøknad.opprettet,
                            søknadInnhold = søknadInnhold,
                            clock = fixedClock,
                        )
                    },
                )
                verify(it.forstesideGeneratorService).genererForSøknadUføre(
                    argThat { it shouldBe nySøknad.fnr.toString() },
                )
                verify(it.journalførSøknadClient).journalførSøknad(
                    argThat { actual ->
                        actual.copy(pdf = sammenslåttPdf) shouldBe JournalførSøknadCommand(
                            saksnummer = Saksnummer(2021),
                            søknadInnholdJson = serialize(søknadInnhold),
                            pdf = sammenslåttPdf,
                            sakstype = Sakstype.UFØRE,
                            datoDokument = fixedTidspunkt,
                            fnr = person.ident.fnr,
                            navn = person.navn,
                            internDokumentId = nySøknad.id,
                        )

                        actual.pdf.getContent().size shouldBe sammenslåttPdf.getContent().size
                    },
                )
                verify(it.søknadRepo).oppdaterjournalpostId(argThat { journalførtSøknad.id })
                verify(it.søknadRepo).hentSøknaderMedJournalpostMenUtenOppgave()
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.oppgaveService).opprettOppgaveMedSystembruker(
                    argThat {
                        it shouldBe OppgaveConfig.Søknad(
                            sakstype = Sakstype.UFØRE,
                            journalpostId = journalførtSøknad.journalpostId,
                            saksnummer = sak.saksnummer,
                            fnr = sak.fnr,
                            clock = fixedClock,
                            tilordnetRessurs = null,
                        )
                    },
                )
                verify(it.søknadRepo).oppdaterOppgaveId(
                    argThat {
                        it shouldBe Søknad.Journalført.MedOppgave.IkkeLukket(
                            id = journalførtSøknad.id,
                            opprettet = journalførtSøknad.opprettet,
                            sakId = sakId,
                            søknadInnhold = søknadInnhold,
                            innsendtAv = veileder,
                            journalpostId = journalførtSøknad.journalpostId,
                            oppgaveId = oppgaveId,
                        )
                    },
                )
                verify(it.søknadRepo).hentSøknaderUtenBehandling(opprettetFør)
            }
            it.verifyNoMoreInteractions()
        }
    }
}
