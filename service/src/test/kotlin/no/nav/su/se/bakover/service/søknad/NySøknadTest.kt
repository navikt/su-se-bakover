package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PdlFeil
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.util.UUID

class NySøknadTest {

    private val søknadInnhold: SøknadInnhold = SøknadInnholdTestdataBuilder.build()
    private val fnr = søknadInnhold.personopplysninger.fnr
    private val person: Person = PersonOppslagStub.person(fnr).orNull()!!
    private val sakFactory: SakFactory = SakFactory()
    private val sakId = UUID.randomUUID()
    private val sak: Sak = Sak(
        id = sakId,
        opprettet = Tidspunkt.EPOCH,
        fnr = fnr,
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            utbetalinger = emptyList()
        )
    )
    private val pdf = "pdf-data".toByteArray()
    private val journalpostId = JournalpostId("1")
    private val oppgaveId = OppgaveId("2")

    @Test
    fun `Fant ikke person`() {
        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn PdlFeil.FantIkkePerson.left()
        }
        val søknadRepoMock: SøknadRepo = mock()
        val sakServiceMock: SakService = mock()
        val pdfGeneratorMock: PdfGenerator = mock()
        val dokArkivMock: DokArkiv = mock()
        val oppgaveServiceMock: OppgaveService = mock()
        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = sakFactory,
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock()
        )

        søknadService.nySøknad(søknadInnhold) shouldBe KunneIkkeOppretteSøknad.FantIkkePerson.left()
        verify(personOppslagMock).person(argThat { it shouldBe fnr })
        verifyNoMoreInteractions(
            personOppslagMock,
            søknadRepoMock,
            sakServiceMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        )
    }

    @Test
    fun `ny sak med søknad hvor pdf-generering feilet`() {
        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn person.right()
        }
        val sakServiceMock: SakService = mock {
            on { hentSak(any<Fnr>()) } doReturn FantIkkeSak.left()
            on { opprettSak(any()) }.doNothing()
        }

        val pdfGeneratorMock: PdfGenerator = mock {
            on { genererPdf(any<SøknadInnhold>()) } doReturn ClientError(1, "").left()
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
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock()
        )

        val actual = søknadService.nySøknad(søknadInnhold)

        lateinit var expected: Søknad
        inOrder(
            personOppslagMock,
            sakServiceMock,
            pdfGeneratorMock
        ) {
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSak(argThat<Fnr> { it shouldBe fnr })
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
                        oppdrag = Oppdrag(
                            id = it.oppdrag.id,
                            opprettet = it.oppdrag.opprettet,
                            sakId = it.id,
                            utbetalinger = emptyList()
                        )
                    ).also { nySak ->
                        expected = nySak.toSak().søknader().first()
                    }
                }
            )
            verify(pdfGeneratorMock).genererPdf(argThat<SøknadInnhold> { it shouldBe søknadInnhold })
        }
        verifyNoMoreInteractions(
            personOppslagMock,
            søknadRepoMock,
            sakServiceMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        )
        actual shouldBe expected.right()
    }

    @Test
    fun `eksisterende sak med søknad hvor journalføring feiler`() {
        val sak = sak.copy()
        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn person.right()
        }
        val sakServiceMock: SakService = mock {
            on { hentSak(any<Fnr>()) } doReturn sak.right()
        }
        val søknadRepoMock: SøknadRepo = mock {
            on { opprettSøknad(any()) }.doNothing()
        }
        val pdfGeneratorMock: PdfGenerator = mock {
            on { genererPdf(any<SøknadInnhold>()) } doReturn pdf.right()
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
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock()
        )

        val actual = søknadService.nySøknad(søknadInnhold)
        lateinit var expectedSøknad: Søknad
        inOrder(
            personOppslagMock,
            sakServiceMock,
            søknadRepoMock,
            pdfGeneratorMock,
            dokArkivMock
        ) {
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSak(argThat<Fnr> { it shouldBe fnr })
            verify(søknadRepoMock).opprettSøknad(
                argThat {
                    it shouldBe Søknad.Ny(
                        id = it.id,
                        opprettet = it.opprettet,
                        sakId = sakId,
                        søknadInnhold = søknadInnhold,
                    ).also { søknad ->
                        expectedSøknad = søknad
                    }
                }
            )
            verify(pdfGeneratorMock).genererPdf(argThat<SøknadInnhold> { it shouldBe søknadInnhold })
            verify(dokArkivMock).opprettJournalpost(
                argThat {
                    it shouldBe Journalpost.Søknadspost(
                        person = person,
                        sakId = sakId.toString(),
                        søknadInnhold = søknadInnhold,
                        pdf = pdf
                    )
                }
            )
        }
        verifyNoMoreInteractions(
            personOppslagMock,
            søknadRepoMock,
            sakServiceMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        )

        actual shouldBe expectedSøknad.right()
    }

    @Test
    fun `eksisterende sak med søknad hvor oppgave feiler`() {
        val sak = sak.copy()
        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn person.right()
        }
        val sakServiceMock: SakService = mock {
            on { hentSak(any<Fnr>()) } doReturn sak.right()
        }
        val søknadRepoMock: SøknadRepo = mock {
            on { opprettSøknad(any()) }.doNothing()
            on { oppdaterjournalpostId(any(), any()) }.doNothing()
        }
        val pdfGeneratorMock: PdfGenerator = mock {
            on { genererPdf(any<SøknadInnhold>()) } doReturn pdf.right()
        }
        val dokArkivMock: DokArkiv = mock {
            on { opprettJournalpost(any()) } doReturn journalpostId.right()
        }

        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }

        val søknadService = SøknadServiceImpl(
            søknadRepo = søknadRepoMock,
            sakService = sakServiceMock,
            sakFactory = sakFactory,
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock()
        )

        val actual = søknadService.nySøknad(søknadInnhold)
        lateinit var expectedSøknad: Søknad
        inOrder(
            personOppslagMock,
            sakServiceMock,
            søknadRepoMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        ) {
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSak(argThat<Fnr> { it shouldBe fnr })
            verify(søknadRepoMock).opprettSøknad(
                argThat {
                    it shouldBe Søknad.Ny(
                        id = it.id,
                        opprettet = it.opprettet,
                        sakId = sakId,
                        søknadInnhold = søknadInnhold,
                    ).also { søknad ->
                        expectedSøknad = søknad
                    }
                }
            )
            verify(pdfGeneratorMock).genererPdf(argThat<SøknadInnhold> { it shouldBe søknadInnhold })
            verify(dokArkivMock).opprettJournalpost(
                argThat {
                    it shouldBe Journalpost.Søknadspost(
                        person = person,
                        sakId = sakId.toString(),
                        søknadInnhold = søknadInnhold,
                        pdf = pdf
                    )
                }
            )
            verify(søknadRepoMock).oppdaterjournalpostId(
                søknadId = argThat { it shouldBe expectedSøknad.id },
                journalpostId = argThat { it shouldBe journalpostId }
            )
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Saksbehandling(
                        journalpostId = journalpostId,
                        sakId = sakId,
                        aktørId = person.ident.aktørId
                    )
                }
            )
        }
        verifyNoMoreInteractions(
            personOppslagMock,
            søknadRepoMock,
            sakServiceMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        )

        actual shouldBe expectedSøknad.right()
    }

    @Test
    fun `eksisterende sak med søknad hvor oppgavekallet går bra`() {
        val sak = sak.copy()
        val personOppslagMock: PersonOppslag = mock {
            on { person(any()) } doReturn person.right()
        }
        val sakServiceMock: SakService = mock {
            on { hentSak(any<Fnr>()) } doReturn sak.right()
        }
        val søknadRepoMock: SøknadRepo = mock {
            on { opprettSøknad(any()) }.doNothing()
            on { oppdaterjournalpostId(any(), any()) }.doNothing()
            on { oppdaterOppgaveId(any(), any()) }.doNothing()
        }
        val pdfGeneratorMock: PdfGenerator = mock {
            on { genererPdf(any<SøknadInnhold>()) } doReturn pdf.right()
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
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            søknadMetrics = mock()
        )

        val actual = søknadService.nySøknad(søknadInnhold)
        lateinit var expectedSøknad: Søknad
        inOrder(
            personOppslagMock,
            sakServiceMock,
            søknadRepoMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        ) {
            verify(personOppslagMock).person(argThat { it shouldBe fnr })
            verify(sakServiceMock).hentSak(argThat<Fnr> { it shouldBe fnr })
            verify(søknadRepoMock).opprettSøknad(
                argThat {
                    it shouldBe Søknad.Ny(
                        id = it.id,
                        opprettet = it.opprettet,
                        sakId = sakId,
                        søknadInnhold = søknadInnhold,
                    ).also { søknad ->
                        expectedSøknad = søknad
                    }
                }
            )
            verify(pdfGeneratorMock).genererPdf(argThat<SøknadInnhold> { it shouldBe søknadInnhold })
            verify(dokArkivMock).opprettJournalpost(
                argThat {
                    it shouldBe Journalpost.Søknadspost(
                        person = person,
                        sakId = sakId.toString(),
                        søknadInnhold = søknadInnhold,
                        pdf = pdf
                    )
                }
            )
            verify(søknadRepoMock).oppdaterjournalpostId(
                søknadId = argThat { it shouldBe expectedSøknad.id },
                journalpostId = argThat { it shouldBe journalpostId }
            )
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Saksbehandling(
                        journalpostId = journalpostId,
                        sakId = sakId,
                        aktørId = person.ident.aktørId
                    )
                }
            )
            verify(søknadRepoMock).oppdaterOppgaveId(
                søknadId = argThat { it shouldBe expectedSøknad.id },
                oppgaveId = argThat { it shouldBe oppgaveId }
            )
        }
        verifyNoMoreInteractions(
            personOppslagMock,
            søknadRepoMock,
            sakServiceMock,
            pdfGeneratorMock,
            dokArkivMock,
            oppgaveServiceMock
        )

        actual shouldBe expectedSøknad.right()
    }
}
