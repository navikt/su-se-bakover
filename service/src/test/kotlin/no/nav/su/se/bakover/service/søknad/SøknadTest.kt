package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Personopplysninger
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.SøknadsinnholdUføre
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.domain.søknad.SøknadRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

class SøknadTest {

    private val søknadInnhold: SøknadsinnholdUføre = SøknadInnholdTestdataBuilder.build()
    private val fnr = søknadInnhold.personopplysninger.fnr
    private val person: Person = PersonOppslagStub.person(fnr).orNull()!!
    private val sakFactory: SakFactory = SakFactory(clock = fixedClock)
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val sak: Sak = Sak(
        id = sakId,
        saksnummer = saksnummer,
        opprettet = Tidspunkt.EPOCH,
        fnr = fnr,
        utbetalinger = emptyList(),
        type = Sakstype.UFØRE,
    )
    private val pdf = "pdf-data".toByteArray()
    private val journalpostId = JournalpostId("1")
    private val oppgaveId = OppgaveId("2")
    private val innsender = NavIdentBruker.Veileder("navIdent")

    @Test
    fun `Fant ikke person`() {
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }
        val søknadRepoMock: SøknadRepo = mock()
        val sakServiceMock: SakService = mock()
        val pdfGeneratorMock: PdfGenerator = mock()
        val dokArkivMock: DokArkiv = mock()
        val oppgaveServiceMock: OppgaveService = mock()
        val observerMock: EventObserver = mock()

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = sakFactory,
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
        ).apply { addObserver(observerMock) }

        søknadService.nySøknad(søknadInnhold, innsender) shouldBe KunneIkkeOppretteSøknad.FantIkkePerson.left()
        verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
        verifyNoMoreInteractions(
            personServiceMock,
            søknadRepoMock,
            sakServiceMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        )
        verifyNoInteractions(observerMock)
    }

    @Test
    fun `ny sak med søknad hvor pdf-generering feilet`() {
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }
        val sakServiceMock: SakService = mock {
            on { hentSakidOgSaksnummer(any()) } doReturn FantIkkeSak.left() doReturn SakInfo(sak.id, sak.saksnummer, fnr, Sakstype.UFØRE).right()
        }

        val pdfGeneratorMock: PdfGenerator = mock {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn ClientError(1, "").left()
        }
        val dokArkivMock: DokArkiv = mock()
        val søknadRepoMock: SøknadRepo = mock()
        val oppgaveServiceMock: OppgaveService = mock()
        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = sakFactory,
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
        )

        val actual = søknadService.nySøknad(søknadInnhold, innsender)

        inOrder(
            personServiceMock,
            sakServiceMock,
            pdfGeneratorMock
        ) {
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSakidOgSaksnummer(argThat { it shouldBe fnr })
            verify(sakServiceMock).opprettSak(
                argThat {
                    it shouldBe NySak(
                        id = it.id,
                        opprettet = it.opprettet,
                        fnr = fnr,
                        søknad = Søknad.Ny(
                            id = it.søknad.id,
                            opprettet = it.søknad.opprettet,
                            sakId = it.id,
                            søknadInnhold = søknadInnhold,
                        ),
                    )
                }
            )
            verify(sakServiceMock).hentSakidOgSaksnummer(argThat { it shouldBe fnr })
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold.create(
                        saksnummer = sak.saksnummer,
                        søknadsId = it.søknadsId,
                        navn = person.navn,
                        søknadOpprettet = actual.orNull()!!.second.opprettet,
                        søknadInnhold = søknadInnhold,
                        clock = fixedClock,
                    )
                }
            )
        }
        verifyNoMoreInteractions(
            personServiceMock,
            søknadRepoMock,
            sakServiceMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        )
        actual.orNull()!!.apply {
            first shouldBe sak.saksnummer
            second.shouldBeEqualToIgnoringFields(
                Søknad.Ny(
                    id = UUID.randomUUID(), // ignored
                    opprettet = fixedTidspunkt,
                    sakId = UUID.randomUUID(), // ignored
                    søknadInnhold = søknadInnhold,
                ),
                Søknad.Ny::id, Søknad.Ny::sakId
            )
        }
    }

    @Test
    fun `nye søknader bortsett fra den første skal ha en annerledes opprettet tidspunkt`() {
        val sak = sak.copy(
            søknader = listOf<Søknad>(
                Søknad.Ny(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.EPOCH,
                    sakId = sak.id,
                    søknadInnhold = søknadInnhold
                )
            )
        )
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }
        val sakServiceMock: SakService = mock {
            on { hentSakidOgSaksnummer(any()) } doReturn SakInfo(sak.id, sak.saksnummer, fnr, Sakstype.UFØRE).right()
        }
        val søknadRepoMock: SøknadRepo = mock()
        val pdfGeneratorMock: PdfGenerator = mock {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
        }
        val dokArkivMock: DokArkiv = mock {
            on { opprettJournalpost(any()) } doReturn journalpostId.right()
        }

        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgave(any()) } doReturn oppgaveId.right()
        }
        val observerMock = mock<EventObserver>()

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = sakFactory,
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
        ).apply { addObserver(observerMock) }

        val nySøknad = søknadService.nySøknad(søknadInnhold, innsender)

        inOrder(
            personServiceMock,
            sakServiceMock,
            søknadRepoMock,
            pdfGeneratorMock,
            dokArkivMock,
            observerMock
        ) {
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSakidOgSaksnummer(argThat { it shouldBe fnr })
            verify(søknadRepoMock).opprettSøknad(
                argThat {
                    it shouldBe Søknad.Ny(
                        id = it.id,
                        opprettet = it.opprettet,
                        sakId = sakId,
                        søknadInnhold = søknadInnhold,
                    )
                },
                argThat { it shouldBe innsender }
            )
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold.create(
                        saksnummer = sak.saksnummer,
                        søknadsId = it.søknadsId,
                        navn = person.navn,
                        søknadOpprettet = nySøknad.orNull()!!.second.opprettet,
                        søknadInnhold = søknadInnhold,
                        clock = fixedClock,
                    )
                }
            )
            verify(dokArkivMock).opprettJournalpost(
                argThat {
                    it shouldBe Journalpost.Søknadspost.from(
                        person = person,
                        saksnummer = saksnummer,
                        søknadInnhold = søknadInnhold,
                        pdf = pdf,
                    )
                },
            )
        }
        verify(observerMock).handle(argThat { it shouldBe Event.Statistikk.SøknadStatistikk.SøknadMottatt(nySøknad.orNull()!!.second, sak.saksnummer) })

        nySøknad.map { (_, søknad) ->
            søknad.opprettet shouldNotBe sak.søknader.first().opprettet
        }
    }

    @Test
    fun `eksisterende sak med søknad hvor journalføring feiler`() {
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }
        val sakServiceMock: SakService = mock {
            on { hentSakidOgSaksnummer(any()) } doReturn SakInfo(sak.id, sak.saksnummer, fnr, Sakstype.UFØRE).right()
        }
        val søknadRepoMock: SøknadRepo = mock()
        val pdfGeneratorMock: PdfGenerator = mock {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
        }
        val dokArkivMock: DokArkiv = mock {
            on { opprettJournalpost(any()) } doReturn ClientError(1, "").left()
        }

        val oppgaveServiceMock: OppgaveService = mock()

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = sakFactory,
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
        )

        val actual = søknadService.nySøknad(søknadInnhold, innsender)

        inOrder(
            personServiceMock,
            sakServiceMock,
            søknadRepoMock,
            pdfGeneratorMock,
            dokArkivMock
        ) {
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSakidOgSaksnummer(argThat { it shouldBe fnr })
            verify(søknadRepoMock).opprettSøknad(
                argThat {
                    it shouldBe Søknad.Ny(
                        id = it.id,
                        opprettet = it.opprettet,
                        sakId = sakId,
                        søknadInnhold = søknadInnhold,
                    )
                },
                argThat { it shouldBe innsender }
            )
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold.create(
                        saksnummer = sak.saksnummer,
                        søknadsId = it.søknadsId,
                        navn = person.navn,
                        søknadOpprettet = actual.orNull()!!.second.opprettet,
                        søknadInnhold = søknadInnhold,
                        clock = fixedClock,
                    )
                }
            )
            verify(dokArkivMock).opprettJournalpost(
                argThat {
                    it shouldBe Journalpost.Søknadspost.from(
                        person = person,
                        saksnummer = saksnummer,
                        søknadInnhold = søknadInnhold,
                        pdf = pdf,
                    )
                },
            )
        }
        verifyNoMoreInteractions(
            personServiceMock,
            søknadRepoMock,
            sakServiceMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        )

        actual.orNull()!!.apply {
            first shouldBe sak.saksnummer
            second.shouldBeEqualToIgnoringFields(
                Søknad.Ny(
                    id = UUID.randomUUID(), // ignored
                    opprettet = fixedTidspunkt,
                    sakId = UUID.randomUUID(), // ignored
                    søknadInnhold = søknadInnhold,
                ),
                Søknad.Ny::id, Søknad.Ny::sakId
            )
        }
    }

    @Test
    fun `eksisterende sak med søknad hvor oppgave feiler`() {

        val (sak, _) = søknadsbehandlingIverksattInnvilget()
        val person = PersonOppslagStub.person(sak.fnr).orNull()!!

        SøknadserviceOgMocks(
            sakService = mock {
                on { hentSakidOgSaksnummer(any()) } doReturn SakInfo(sak.id, sak.saksnummer, fnr, Sakstype.UFØRE).right()
            },
            pdfGenerator = mock {
                on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
            },
            dokArkiv = mock {
                on { opprettJournalpost(any()) } doReturn journalpostId.right()
            },
            personService = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
            },
        ) {
            val søknadInnhold = SøknadInnholdTestdataBuilder.build(personopplysninger = Personopplysninger(sak.fnr))
            val actual = service.nySøknad(søknadInnhold, innsender)
            inOrder(*allMocks()) {
                verify(personService).hentPerson(argThat { it shouldBe sak.fnr })
                verify(sakService).hentSakidOgSaksnummer(argThat { it shouldBe sak.fnr })
                verify(søknadRepo).opprettSøknad(
                    argThat {
                        it shouldBe Søknad.Ny(
                            id = it.id,
                            opprettet = it.opprettet,
                            sakId = sak.id,
                            søknadInnhold = søknadInnhold,
                        )
                    },
                    argThat { it shouldBe innsender }
                )
                verify(søknadMetrics).incrementNyCounter(SøknadMetrics.NyHandlinger.PERSISTERT)
                verify(pdfGenerator).genererPdf(
                    argThat<SøknadPdfInnhold> {
                        it shouldBe SøknadPdfInnhold.create(
                            saksnummer = sak.saksnummer,
                            søknadsId = it.søknadsId,
                            navn = person.navn,
                            søknadOpprettet = actual.orNull()!!.second.opprettet,
                            søknadInnhold = søknadInnhold,
                            clock = fixedClock,
                        )
                    },
                )
                verify(dokArkiv).opprettJournalpost(
                    argThat {
                        it shouldBe Journalpost.Søknadspost.from(
                            person = person,
                            saksnummer = sak.saksnummer,
                            søknadInnhold = søknadInnhold,
                            pdf = pdf,
                        )
                    },
                )
                verify(søknadRepo).oppdaterjournalpostId(
                    argThat {
                        it.shouldBeEqualToIgnoringFields(
                            Søknad.Journalført.UtenOppgave(
                                id = UUID.randomUUID(), // ignored
                                opprettet = fixedTidspunkt,
                                sakId = sak.id,
                                søknadInnhold = søknadInnhold,
                                journalpostId = journalpostId,
                            ),
                            Søknad.Journalført.UtenOppgave::id,
                        )
                    },
                )
                verify(søknadMetrics).incrementNyCounter(SøknadMetrics.NyHandlinger.JOURNALFØRT)
                verify(oppgaveService).opprettOppgave(
                    argThat {
                        it.shouldBeEqualToIgnoringFields(
                            OppgaveConfig.Søknad(
                                sakstype = Sakstype.UFØRE,
                                journalpostId = journalpostId,
                                søknadId = UUID.randomUUID(), // ignored
                                aktørId = person.ident.aktørId,
                                tilordnetRessurs = null,
                                clock = fixedClock,
                            ),
                            OppgaveConfig.Søknad::søknadId,
                            OppgaveConfig.Søknad::saksreferanse,
                        )
                    },
                )
            }
            actual.orNull()!!.apply {
                first shouldBe sak.saksnummer
                second.shouldBeEqualToIgnoringFields(
                    Søknad.Ny(
                        id = UUID.randomUUID(), // ignored
                        opprettet = fixedTidspunkt,
                        sakId = UUID.randomUUID(), // ignored
                        søknadInnhold = søknadInnhold,
                    ),
                    Søknad.Ny::id, Søknad.Ny::sakId,
                )
            }
        }
    }

    @Test
    fun `eksisterende sak med søknad hvor oppgavekallet går bra`() {
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }
        val sakServiceMock: SakService = mock {
            on { hentSakidOgSaksnummer(any()) } doReturn SakInfo(sak.id, sak.saksnummer, fnr, Sakstype.UFØRE).right()
        }
        val søknadRepoMock: SøknadRepo = mock()
        val pdfGeneratorMock: PdfGenerator = mock {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
        }
        val dokArkivMock: DokArkiv = mock {
            on { opprettJournalpost(any()) } doReturn journalpostId.right()
        }

        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgave(any()) } doReturn oppgaveId.right()
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = sakFactory,
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock(),
            toggleService = mock(),
            clock = fixedClock,
        )

        val actual = søknadService.nySøknad(søknadInnhold, innsender)
        inOrder(
            personServiceMock,
            sakServiceMock,
            søknadRepoMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        ) {
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSakidOgSaksnummer(argThat { it shouldBe fnr })
            verify(søknadRepoMock).opprettSøknad(
                argThat {
                    it shouldBe Søknad.Ny(
                        id = it.id,
                        opprettet = it.opprettet,
                        sakId = sakId,
                        søknadInnhold = søknadInnhold,
                    )
                },
                argThat { it shouldBe innsender }
            )
            verify(pdfGeneratorMock).genererPdf(
                argThat<SøknadPdfInnhold> {
                    it shouldBe SøknadPdfInnhold.create(
                        saksnummer = sak.saksnummer,
                        søknadsId = it.søknadsId,
                        navn = person.navn,
                        søknadOpprettet = actual.orNull()!!.second.opprettet,
                        søknadInnhold = søknadInnhold,
                        clock = fixedClock,
                    )
                }
            )
            verify(dokArkivMock).opprettJournalpost(
                argThat {
                    it shouldBe Journalpost.Søknadspost.from(
                        person = person,
                        saksnummer = saksnummer,
                        søknadInnhold = søknadInnhold,
                        pdf = pdf,
                    )
                },
            )
            verify(søknadRepoMock).oppdaterjournalpostId(
                argThat {
                    it.shouldBeEqualToIgnoringFields(
                        Søknad.Journalført.UtenOppgave(
                            id = UUID.randomUUID(), // ignored
                            opprettet = fixedTidspunkt,
                            sakId = sakId,
                            søknadInnhold = søknadInnhold,
                            journalpostId = journalpostId,
                        ),
                        Søknad.Journalført.MedOppgave::id
                    )
                }
            )
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it.shouldBeEqualToIgnoringFields(
                        OppgaveConfig.Søknad(
                            sakstype = Sakstype.UFØRE,
                            journalpostId = journalpostId,
                            søknadId = UUID.randomUUID(), // ignored
                            aktørId = person.ident.aktørId,
                            tilordnetRessurs = null,
                            clock = fixedClock,
                        ),
                        OppgaveConfig.Søknad::søknadId,
                        OppgaveConfig.Søknad::saksreferanse,
                    )
                },
            )
            verify(søknadRepoMock).oppdaterOppgaveId(
                argThat {
                    it.shouldBeEqualToIgnoringFields(
                        Søknad.Journalført.MedOppgave.IkkeLukket(
                            id = UUID.randomUUID(), // ignored
                            opprettet = fixedTidspunkt,
                            sakId = sakId,
                            søknadInnhold = søknadInnhold,
                            journalpostId = journalpostId,
                            oppgaveId = oppgaveId
                        ),
                        Søknad.Journalført.MedOppgave::id
                    )
                }
            )
        }
        verifyNoMoreInteractions(
            personServiceMock,
            søknadRepoMock,
            sakServiceMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        )

        actual.orNull()!!.apply {
            first shouldBe sak.saksnummer
            second.shouldBeEqualToIgnoringFields(
                Søknad.Ny(
                    id = UUID.randomUUID(), // ignored
                    opprettet = fixedTidspunkt,
                    sakId = UUID.randomUUID(), // ignored
                    søknadInnhold = søknadInnhold,
                ),
                Søknad.Ny::id, Søknad.Ny::sakId
            )
        }
    }
}
