package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.veileder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

class OpprettManglendeJournalpostOgOppgaveTest {
    private val søknadInnhold = SøknadInnholdTestdataBuilder.build()
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
        søknadsbehandlinger = emptyList(),
        utbetalinger = emptyList(),
        type = Sakstype.UFØRE,
        uteståendeAvkorting = Avkortingsvarsel.Ingen,
    )
    private val person = Person(
        ident = Ident(
            fnr = fnr,
            aktørId = AktørId(aktørId = "123"),
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
    )

    @Test
    fun `ingen søknader`() {
        SøknadServiceOgMocks(
            mock {
                on { hentSøknaderUtenJournalpost() } doReturn emptyList()
                on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn emptyList()
            },
        ).also {
            it.service.opprettManglendeJournalpostOgOppgave() shouldBe OpprettManglendeJournalpostOgOppgaveResultat(emptyList(), emptyList())
            inOrder(*it.allMocks()) {
                verify(it.søknadRepo).hentSøknaderUtenJournalpost()
                verify(it.søknadRepo).hentSøknaderMedJournalpostMenUtenOppgave()
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
            },
            sakService = mock {
                on { hentSak(sakId) } doReturn FantIkkeSak.left()
            },
        ).also {
            it.service.opprettManglendeJournalpostOgOppgave() shouldBe OpprettManglendeJournalpostOgOppgaveResultat(
                journalpostResultat = listOf(KunneIkkeOppretteJournalpost(sakId, nySøknad.id, "Fant ikke sak").left()),
                oppgaveResultat = listOf(KunneIkkeOppretteOppgave(sakId, nySøknad.id, journalførtSøknad.journalpostId, "Fant ikke sak").left()),
            )

            inOrder(*it.allMocks()) {
                verify(it.søknadRepo).hentSøknaderUtenJournalpost()
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.søknadRepo).hentSøknaderMedJournalpostMenUtenOppgave()
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
            }
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `finner ikke person`() {
        SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknaderUtenJournalpost() } doReturn listOf(nySøknad)
                on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn listOf(journalførtSøknad)
            },
            sakService = mock {
                on { hentSak(sakId) } doReturn sak.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(fnr) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
            },
        ).also {
            it.service.opprettManglendeJournalpostOgOppgave() shouldBe OpprettManglendeJournalpostOgOppgaveResultat(
                journalpostResultat = listOf(KunneIkkeOppretteJournalpost(sakId, nySøknad.id, "Fant ikke person").left()),
                oppgaveResultat = listOf(KunneIkkeOppretteOppgave(sakId, journalførtSøknad.id, journalførtSøknad.journalpostId, "Fant ikke person").left()),
            )
            inOrder(*it.allMocks()) {
                verify(it.søknadRepo).hentSøknaderUtenJournalpost()
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.personService).hentPersonMedSystembruker(argThat { it shouldBe fnr })
                verify(it.søknadRepo).hentSøknaderMedJournalpostMenUtenOppgave()
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.personService).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            }
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `opprett journalpost feiler`() {
        SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknaderUtenJournalpost() } doReturn listOf(nySøknad)
                on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn emptyList()
            },
            sakService = mock {
                on { hentSak(sakId) } doReturn sak.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(fnr) } doReturn person.right()
            },
            pdfGenerator = mock {
                on { genererPdf(any<SøknadPdfInnhold>()) } doReturn ClientError(0, "").left()
            },
        ).also {
            it.service.opprettManglendeJournalpostOgOppgave() shouldBe OpprettManglendeJournalpostOgOppgaveResultat(
                journalpostResultat = listOf(KunneIkkeOppretteJournalpost(sakId, nySøknad.id, "Kunne ikke generere PDF").left()),
                oppgaveResultat = emptyList(),
            )
            inOrder(*it.allMocks()) {
                verify(it.søknadRepo).hentSøknaderUtenJournalpost()
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.personService).hentPersonMedSystembruker(argThat { it shouldBe fnr })
                verify(it.pdfGenerator).genererPdf(
                    argThat<SøknadPdfInnhold> {
                        it shouldBe SøknadPdfInnhold.create(
                            saksnummer = sak.saksnummer,
                            søknadsId = it.søknadsId,
                            navn = person.navn,
                            søknadOpprettet = nySøknad.opprettet,
                            søknadInnhold = søknadInnhold,
                            clock = fixedClock,
                        )
                    },
                )
                verify(it.søknadRepo).hentSøknaderMedJournalpostMenUtenOppgave()
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
            },
            sakService = mock {
                on { hentSak(sakId) } doReturn sak.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(fnr) } doReturn person.right()
            },
            oppgaveService = mock {
                on { opprettOppgaveMedSystembruker(any()) } doReturn no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeOppretteOppgave.left()
            },
        ).also {
            it.service.opprettManglendeJournalpostOgOppgave() shouldBe OpprettManglendeJournalpostOgOppgaveResultat(
                journalpostResultat = emptyList(),
                oppgaveResultat = listOf(KunneIkkeOppretteOppgave(sakId, journalførtSøknad.id, journalførtSøknad.journalpostId, "Kunne ikke opprette oppgave").left()),
            )

            inOrder(*it.allMocks()) {
                verify(it.søknadRepo).hentSøknaderUtenJournalpost()
                verify(it.søknadRepo).hentSøknaderMedJournalpostMenUtenOppgave()
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.personService).hentPersonMedSystembruker(argThat { it shouldBe fnr })
                verify(it.oppgaveService).opprettOppgaveMedSystembruker(
                    argThat {
                        it shouldBe OppgaveConfig.Søknad(
                            sakstype = Sakstype.UFØRE,
                            journalpostId = journalførtSøknad.journalpostId,
                            søknadId = journalførtSøknad.id,
                            aktørId = person.ident.aktørId,
                            clock = fixedClock,
                            tilordnetRessurs = null,
                        )
                    },
                )
            }
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy case`() {
        val oppgaveId = OppgaveId("1")
        val pdf = "pdf-data".toByteArray()

        SøknadServiceOgMocks(
            søknadRepo = mock {
                on { hentSøknaderUtenJournalpost() } doReturn listOf(nySøknad)
                on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn listOf(journalførtSøknad)
            },
            sakService = mock {
                on { hentSak(sakId) } doReturn sak.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(fnr) } doReturn person.right()
            },
            oppgaveService = mock {
                on { opprettOppgaveMedSystembruker(any()) } doReturn oppgaveId.right()
            },
            pdfGenerator = mock {
                on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
            },
            dokArkiv = mock {
                on { opprettJournalpost(any()) } doReturn journalførtSøknad.journalpostId.right()
            },
            søknadMetrics = mock(),
        ).also {
            it.service.opprettManglendeJournalpostOgOppgave() shouldBe OpprettManglendeJournalpostOgOppgaveResultat(
                journalpostResultat = listOf(journalførtSøknad.right()),
                oppgaveResultat = listOf(journalførtSøknad.medOppgave(oppgaveId).right()),
            )

            inOrder(*it.allMocks()) {
                verify(it.søknadRepo).hentSøknaderUtenJournalpost()
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.personService).hentPersonMedSystembruker(argThat { it shouldBe fnr })
                verify(it.pdfGenerator).genererPdf(
                    argThat<SøknadPdfInnhold> {
                        it shouldBe SøknadPdfInnhold.create(
                            saksnummer = sak.saksnummer,
                            søknadsId = it.søknadsId,
                            navn = person.navn,
                            søknadOpprettet = nySøknad.opprettet,
                            søknadInnhold = søknadInnhold,
                            clock = fixedClock,
                        )
                    },
                )
                verify(it.dokArkiv).opprettJournalpost(
                    argThat {
                        it shouldBe Journalpost.Søknadspost.from(
                            person = person,
                            saksnummer = Saksnummer(2021),
                            søknadInnhold = søknadInnhold,
                            pdf = pdf,
                        )
                    },
                )
                verify(it.søknadRepo).oppdaterjournalpostId(argThat { journalførtSøknad.id })
                verify(it.søknadMetrics).incrementNyCounter(SøknadMetrics.NyHandlinger.JOURNALFØRT)
                verify(it.søknadRepo).hentSøknaderMedJournalpostMenUtenOppgave()
                verify(it.sakService).hentSak(argThat<UUID> { it shouldBe sakId })
                verify(it.personService).hentPersonMedSystembruker(argThat { it shouldBe fnr })
                verify(it.oppgaveService).opprettOppgaveMedSystembruker(
                    argThat {
                        it shouldBe OppgaveConfig.Søknad(
                            sakstype = Sakstype.UFØRE,
                            journalpostId = journalførtSøknad.journalpostId,
                            søknadId = journalførtSøknad.id,
                            aktørId = person.ident.aktørId,
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
                verify(it.søknadMetrics).incrementNyCounter(SøknadMetrics.NyHandlinger.OPPRETTET_OPPGAVE)
            }
            it.verifyNoMoreInteractions()
        }
    }
}
