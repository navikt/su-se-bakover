package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import dokument.domain.journalføring.søknad.JournalførSøknadCommand
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.dokument.infrastructure.client.KunneIkkeGenererePdf
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.sak.FantIkkeSak
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.veileder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import person.domain.Person
import økonomi.domain.utbetaling.Utbetalinger
import java.time.Year
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

    @Test
    fun `ingen søknader`() {
        SøknadServiceOgMocks(
            mock {
                on { hentSøknaderUtenJournalpost() } doReturn emptyList()
                on { hentSøknaderMedJournalpostMenUtenOppgave() } doReturn emptyList()
            },
        ).also {
            it.service.opprettManglendeJournalpostOgOppgave() shouldBe OpprettManglendeJournalpostOgOppgaveResultat(
                emptyList(),
                emptyList(),
            )
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
                oppgaveResultat = listOf(
                    KunneIkkeOppretteOppgave(
                        sakId,
                        nySøknad.id,
                        journalførtSøknad.journalpostId,
                        "Fant ikke sak",
                    ).left(),
                ),
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
                on { genererPdf(any<SøknadPdfInnhold>()) } doReturn KunneIkkeGenererePdf.left()
            },
        ).also {
            it.service.opprettManglendeJournalpostOgOppgave() shouldBe OpprettManglendeJournalpostOgOppgaveResultat(
                journalpostResultat = listOf(
                    KunneIkkeOppretteJournalpost(
                        sakId,
                        nySøknad.id,
                        "Kunne ikke generere PDF",
                    ).left(),
                ),
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
                on { opprettOppgaveMedSystembruker(any()) } doReturn no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave.left()
            },
        ).also {
            it.service.opprettManglendeJournalpostOgOppgave() shouldBe OpprettManglendeJournalpostOgOppgaveResultat(
                journalpostResultat = emptyList(),
                oppgaveResultat = listOf(
                    KunneIkkeOppretteOppgave(
                        sakId,
                        journalførtSøknad.id,
                        journalførtSøknad.journalpostId,
                        "Kunne ikke opprette oppgave",
                    ).left(),
                ),
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
                            søknadId = journalførtSøknad.id,
                            fnr = person.ident.fnr,
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
        val oppgaveId = OppgaveId("123")
        val pdf = PdfA("pdf-data".toByteArray())

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
                on { opprettOppgaveMedSystembruker(any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
            pdfGenerator = mock {
                on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
            },
            journalførSøknadClient = mock {
                on { journalførSøknad(any()) } doReturn journalførtSøknad.journalpostId.right()
            },
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
                            sakstype = sak.type,
                            søknadsId = it.søknadsId,
                            navn = person.navn,
                            søknadOpprettet = nySøknad.opprettet,
                            søknadInnhold = søknadInnhold,
                            clock = fixedClock,
                        )
                    },
                )
                verify(it.journalførSøknadClient).journalførSøknad(
                    argThat {
                        it shouldBe JournalførSøknadCommand(
                            saksnummer = Saksnummer(2021),
                            søknadInnholdJson = serialize(søknadInnhold),
                            pdf = pdf,
                            sakstype = Sakstype.UFØRE,
                            datoDokument = fixedTidspunkt,
                            fnr = person.ident.fnr,
                            navn = person.navn,
                            internDokumentId = nySøknad.id,
                        )
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
                            søknadId = journalførtSøknad.id,
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
            }
            it.verifyNoMoreInteractions()
        }
    }
}
