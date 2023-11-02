package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.pdf.KunneIkkeGenererePdf
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.journalpost.JournalpostForSakCommand
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.sak.FantIkkeSak
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdUføre
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import java.util.UUID

class SøknadTest {

    private val søknadInnhold: SøknadsinnholdUføre = søknadinnholdUføre()
    private val fnr = søknadInnhold.personopplysninger.fnr
    private val person: Person = PersonOppslagStub.person(fnr).getOrFail()
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(2021)
    private val sak: Sak = Sak(
        id = sakId,
        saksnummer = saksnummer,
        opprettet = Tidspunkt.EPOCH,
        fnr = fnr,
        utbetalinger = Utbetalinger(),
        type = Sakstype.UFØRE,
        versjon = Hendelsesversjon(1),
        uteståendeKravgrunnlag = null,
    )
    private val pdf = PdfA("pdf-data".toByteArray())
    private val journalpostId = JournalpostId("1")
    private val oppgaveId = OppgaveId("2")
    private val innsender = NavIdentBruker.Veileder("navIdent")

    @Test
    fun `Fant ikke person`() {
        SøknadServiceOgMocks(
            personService = mock {
                on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
            },
        ).also {
            it.service.nySøknad(søknadInnhold, innsender) shouldBe KunneIkkeOppretteSøknad.FantIkkePerson.left()
        }
    }

    @Test
    fun `ny sak med søknad hvor pdf-generering feilet`() {
        SøknadServiceOgMocks(
            personService = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            sakService = mock {
                on { hentSakidOgSaksnummer(any()) } doReturn FantIkkeSak.left() doReturn SakInfo(sak.id, sak.saksnummer, fnr, Sakstype.UFØRE).right()
            },
            pdfGenerator = mock {
                on { genererPdf(any<SøknadPdfInnhold>()) } doReturn KunneIkkeGenererePdf.left()
            },
            søknadMetrics = mock(),
        ).also {
            val actual = it.service.nySøknad(søknadInnhold, innsender)

            inOrder(
                it.personService,
                it.sakService,
                it.pdfGenerator,
            ) {
                verify(it.personService).hentPerson(argThat { it shouldBe fnr })
                verify(it.sakService).hentSakidOgSaksnummer(argThat { it shouldBe fnr })
                verify(it.sakService).opprettSak(
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
                                innsendtAv = innsender,
                            ),
                        )
                    },
                )
                verify(it.sakService).hentSakidOgSaksnummer(argThat { it shouldBe fnr })
                verify(it.pdfGenerator).genererPdf(
                    argThat<SøknadPdfInnhold> {
                        it shouldBe SøknadPdfInnhold.create(
                            saksnummer = sak.saksnummer,
                            søknadsId = it.søknadsId,
                            navn = person.navn,
                            søknadOpprettet = actual.getOrFail().second.opprettet,
                            søknadInnhold = søknadInnhold,
                            clock = fixedClock,
                        )
                    },
                )
            }
            actual.getOrFail().apply {
                first shouldBe sak.saksnummer
                second.shouldBeEqualToIgnoringFields(
                    Søknad.Ny(
                        // ignored
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        // ignored
                        sakId = UUID.randomUUID(),
                        søknadInnhold = søknadInnhold,
                        innsendtAv = innsender,
                    ),
                    Søknad.Ny::id,
                    Søknad.Ny::sakId,
                )
            }
        }
    }

    @Test
    fun `eksisterende sak med søknad hvor journalføring feiler`() {
        SøknadServiceOgMocks(
            personService = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            sakService = mock {
                on { hentSakidOgSaksnummer(any()) } doReturn SakInfo(sak.id, sak.saksnummer, fnr, Sakstype.UFØRE).right()
            },
            pdfGenerator = mock {
                on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
            },
            søknadMetrics = mock(),
            dokArkiv = mock {
                on { opprettJournalpost(any()) } doReturn ClientError(1, "").left()
            },
            søknadRepo = mock(),
        ).also {
            val actual = it.service.nySøknad(søknadInnhold, innsender)

            inOrder(
                it.personService,
                it.sakService,
                it.søknadRepo,
                it.pdfGenerator,
                it.dokArkiv,
            ) {
                verify(it.personService).hentPerson(argThat { it shouldBe fnr })
                verify(it.sakService).hentSakidOgSaksnummer(argThat { it shouldBe fnr })
                verify(it.søknadRepo).opprettSøknad(
                    argThat {
                        it shouldBe Søknad.Ny(
                            id = it.id,
                            opprettet = it.opprettet,
                            sakId = sakId,
                            søknadInnhold = søknadInnhold,
                            innsendtAv = innsender,
                        )
                    },
                )
                verify(it.pdfGenerator).genererPdf(
                    argThat<SøknadPdfInnhold> {
                        it shouldBe SøknadPdfInnhold.create(
                            saksnummer = sak.saksnummer,
                            søknadsId = it.søknadsId,
                            navn = person.navn,
                            søknadOpprettet = actual.getOrFail().second.opprettet,
                            søknadInnhold = søknadInnhold,
                            clock = fixedClock,
                        )
                    },
                )
                verify(it.dokArkiv).opprettJournalpost(
                    argThat {
                        it shouldBe JournalpostForSakCommand.Søknadspost(
                            saksnummer = Saksnummer(2021),
                            søknadInnhold = søknadInnhold,
                            pdf = pdf,
                            sakstype = Sakstype.UFØRE,
                            datoDokument = fixedTidspunkt,
                            fnr = person.ident.fnr,
                            navn = person.navn,
                        )
                    },
                )
            }
            actual.getOrFail().apply {
                first shouldBe sak.saksnummer
                second.shouldBeEqualToIgnoringFields(
                    Søknad.Ny(
                        // ignored
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        // ignored
                        sakId = UUID.randomUUID(),
                        søknadInnhold = søknadInnhold,
                        innsendtAv = innsender,
                    ),
                    Søknad.Ny::id,
                    Søknad.Ny::sakId,
                )
            }
        }
    }

    @Test
    fun `eksisterende sak med søknad hvor oppgave feiler`() {
        val (sak, _) = søknadsbehandlingIverksattInnvilget()
        val person = PersonOppslagStub.person(sak.fnr).getOrFail()

        SøknadServiceOgMocks(
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
            søknadRepo = mock(),
            søknadMetrics = mock(),
        ).also {
            val søknadInnhold = søknadinnholdUføre(personopplysninger = Personopplysninger(sak.fnr))
            val actual = it.service.nySøknad(søknadInnhold, innsender)
            inOrder(*it.allMocks()) {
                verify(it.personService).hentPerson(argThat { it shouldBe sak.fnr })
                verify(it.sakService).hentSakidOgSaksnummer(argThat { it shouldBe sak.fnr })
                verify(it.søknadRepo).opprettSøknad(
                    argThat {
                        it shouldBe Søknad.Ny(
                            id = it.id,
                            opprettet = it.opprettet,
                            sakId = sak.id,
                            søknadInnhold = søknadInnhold,
                            innsendtAv = innsender,
                        )
                    },
                )
                verify(it.søknadMetrics).incrementNyCounter(SøknadMetrics.NyHandlinger.PERSISTERT)
                verify(it.pdfGenerator).genererPdf(
                    argThat<SøknadPdfInnhold> {
                        it shouldBe SøknadPdfInnhold.create(
                            saksnummer = sak.saksnummer,
                            søknadsId = it.søknadsId,
                            navn = person.navn,
                            søknadOpprettet = actual.getOrFail().second.opprettet,
                            søknadInnhold = søknadInnhold,
                            clock = fixedClock,
                        )
                    },
                )
                verify(it.dokArkiv).opprettJournalpost(
                    argThat {
                        it shouldBe JournalpostForSakCommand.Søknadspost(
                            saksnummer = sak.saksnummer,
                            søknadInnhold = søknadInnhold,
                            pdf = pdf,
                            sakstype = Sakstype.UFØRE,
                            datoDokument = fixedTidspunkt,
                            fnr = person.ident.fnr,
                            navn = person.navn,
                        )
                    },
                )
                verify(it.søknadRepo).oppdaterjournalpostId(
                    argThat {
                        it.shouldBeEqualToIgnoringFields(
                            Søknad.Journalført.UtenOppgave(
                                // ignored
                                id = UUID.randomUUID(),
                                opprettet = fixedTidspunkt,
                                sakId = sak.id,
                                søknadInnhold = søknadInnhold,
                                innsendtAv = innsender,
                                journalpostId = journalpostId,
                            ),
                            Søknad.Journalført.UtenOppgave::id,
                        )
                    },
                )
                verify(it.søknadMetrics).incrementNyCounter(SøknadMetrics.NyHandlinger.JOURNALFØRT)
                verify(it.oppgaveService).opprettOppgave(
                    argThat {
                        it.shouldBeEqualToIgnoringFields(
                            OppgaveConfig.Søknad(
                                sakstype = Sakstype.UFØRE,
                                journalpostId = journalpostId,
                                // ignored
                                søknadId = UUID.randomUUID(),
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
            actual.getOrFail().apply {
                first shouldBe sak.saksnummer
                second.shouldBeEqualToIgnoringFields(
                    Søknad.Ny(
                        // ignored
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        // ignored
                        sakId = UUID.randomUUID(),
                        søknadInnhold = søknadInnhold,
                        innsendtAv = innsender,
                    ),
                    Søknad.Ny::id,
                    Søknad.Ny::sakId,
                )
            }
        }
    }

    @Test
    fun `eksisterende sak med søknad hvor oppgavekallet går bra`() {
        SøknadServiceOgMocks(
            personService = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            sakService = mock {
                on { hentSakidOgSaksnummer(any()) } doReturn SakInfo(sak.id, sak.saksnummer, fnr, Sakstype.UFØRE).right()
            },
            pdfGenerator = mock {
                on { genererPdf(any<SøknadPdfInnhold>()) } doReturn pdf.right()
            },
            dokArkiv = mock {
                on { opprettJournalpost(any()) } doReturn journalpostId.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn oppgaveId.right()
            },
            søknadRepo = mock(),
            søknadMetrics = mock(),
        ).also {
            val actual = it.service.nySøknad(søknadInnhold, innsender)

            inOrder(
                it.personService,
                it.sakService,
                it.søknadRepo,
                it.pdfGenerator,
                it.dokArkiv,
                it.oppgaveService,
            ) {
                verify(it.personService).hentPerson(argThat { it shouldBe fnr })
                verify(it.sakService).hentSakidOgSaksnummer(argThat { it shouldBe fnr })
                verify(it.søknadRepo).opprettSøknad(
                    argThat {
                        it shouldBe Søknad.Ny(
                            id = it.id,
                            opprettet = it.opprettet,
                            sakId = sakId,
                            søknadInnhold = søknadInnhold,
                            innsendtAv = innsender,
                        )
                    },
                )
                verify(it.pdfGenerator).genererPdf(
                    argThat<SøknadPdfInnhold> {
                        it shouldBe SøknadPdfInnhold.create(
                            saksnummer = sak.saksnummer,
                            søknadsId = it.søknadsId,
                            navn = person.navn,
                            søknadOpprettet = actual.getOrFail().second.opprettet,
                            søknadInnhold = søknadInnhold,
                            clock = fixedClock,
                        )
                    },
                )
                verify(it.dokArkiv).opprettJournalpost(
                    argThat {
                        it shouldBe JournalpostForSakCommand.Søknadspost(
                            saksnummer = Saksnummer(2021),
                            søknadInnhold = søknadInnhold,
                            pdf = pdf,
                            sakstype = Sakstype.UFØRE,
                            datoDokument = fixedTidspunkt,
                            fnr = person.ident.fnr,
                            navn = person.navn,
                        )
                    },
                )
                verify(it.søknadRepo).oppdaterjournalpostId(
                    argThat {
                        it.shouldBeEqualToIgnoringFields(
                            Søknad.Journalført.UtenOppgave(
                                // ignored
                                id = UUID.randomUUID(),
                                opprettet = fixedTidspunkt,
                                sakId = sakId,
                                søknadInnhold = søknadInnhold,
                                innsendtAv = innsender,
                                journalpostId = journalpostId,
                            ),
                            Søknad.Journalført.MedOppgave::id,
                        )
                    },
                )
                verify(it.oppgaveService).opprettOppgave(
                    argThat {
                        it.shouldBeEqualToIgnoringFields(
                            OppgaveConfig.Søknad(
                                sakstype = Sakstype.UFØRE,
                                journalpostId = journalpostId,
                                // ignored
                                søknadId = UUID.randomUUID(),
                                aktørId = person.ident.aktørId,
                                tilordnetRessurs = null,
                                clock = fixedClock,
                            ),
                            OppgaveConfig.Søknad::søknadId,
                            OppgaveConfig.Søknad::saksreferanse,
                        )
                    },
                )
                verify(it.søknadRepo).oppdaterOppgaveId(
                    argThat {
                        it.shouldBeEqualToIgnoringFields(
                            Søknad.Journalført.MedOppgave.IkkeLukket(
                                // ignored
                                id = UUID.randomUUID(),
                                opprettet = fixedTidspunkt,
                                sakId = sakId,
                                søknadInnhold = søknadInnhold,
                                innsendtAv = innsender,
                                journalpostId = journalpostId,
                                oppgaveId = oppgaveId,
                            ),
                            Søknad.Journalført.MedOppgave::id,
                        )
                    },
                )
            }
            verifyNoMoreInteractions(
                it.personService,
                it.søknadRepo,
                it.sakService,
                it.pdfGenerator,
                it.dokArkiv,
                it.oppgaveService,
            )

            actual.getOrFail().apply {
                first shouldBe sak.saksnummer
                second.shouldBeEqualToIgnoringFields(
                    Søknad.Ny(
                        // ignored
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        // ignored
                        sakId = UUID.randomUUID(),
                        søknadInnhold = søknadInnhold,
                        innsendtAv = innsender,
                    ),
                    Søknad.Ny::id,
                    Søknad.Ny::sakId,
                )
            }
        }
    }
}
