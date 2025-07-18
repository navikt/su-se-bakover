package no.nav.su.se.bakover.kontrollsamtale.domain

import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import dokument.domain.journalføring.QueryJournalpostClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.FantIkkeSak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.kontrollsamtale.application.KontrollsamtaleServiceImpl
import no.nav.su.se.bakover.kontrollsamtale.domain.hent.KunneIkkeHenteKontrollsamtale
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.dokumentUtenMetadataInformasjonViktig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.kontrollsamtale.gjennomførtKontrollsamtale
import no.nav.su.se.bakover.test.kontrollsamtale.innkaltKontrollsamtale
import no.nav.su.se.bakover.test.kontrollsamtale.planlagtKontrollsamtale
import no.nav.su.se.bakover.test.minimumPdfAzeroPadded
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.capture
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.KunneIkkeHentePerson
import person.domain.PersonService
import java.time.Clock
import java.util.UUID

internal class KontrollsamtaleServiceImplTest {

    private val sak = vedtakSøknadsbehandlingIverksattInnvilget().first
    private val person = person(fnr = sak.fnr)
    private val kontrollsamtale = planlagtKontrollsamtale(sakId = sak.id)

    @Test
    fun `feiler hvis vi ikke finner sak`() {
        shouldThrow<IllegalArgumentException> {
            ServiceOgMocks(
                sakService = mock {
                    on { hentSak(any<UUID>()) } doReturn FantIkkeSak.left()
                },
            ).kontrollsamtaleService.kallInn(
                kontrollsamtale = kontrollsamtale,
            )
        }.message shouldBe "Fant ikke sak for sakId ${sak.id}"
    }

    @Test
    fun `feiler hvis vi ikke finner person`() {
        ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.copy(vedtakListe = emptyList()).right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
            },
        ).kontrollsamtaleService.kallInn(
            kontrollsamtale = kontrollsamtale,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.FantIkkePerson.left()
    }

    @Test
    fun `feiler dersom kontrollsamtale er i ugyldig tilstand for å annulleres`() {
        ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.copy(vedtakListe = emptyList()).right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person(
                    fnr = sak.fnr,
                    dødsdato = 1.januar(2021),
                ).right()
            },
        ).kontrollsamtaleService.kallInn(
            kontrollsamtale = kontrollsamtale.annuller().getOrNull()!!,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.UgyldigTilstand.left()
    }

    @Test
    fun `annullerer dersom person er død`() {
        ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.copy(vedtakListe = emptyList()).right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person(
                    fnr = sak.fnr,
                    dødsdato = 1.januar(2021),
                ).right()
            },
        ).kontrollsamtaleService.kallInn(
            kontrollsamtale = kontrollsamtale,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.PersonErDød.left()
    }

    @Test
    fun `feiler hvis vi ikke finner gjeldende stønadsperiode på sak`() {
        ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.copy(vedtakListe = emptyList()).right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person.right()
            },
        ).kontrollsamtaleService.kallInn(
            kontrollsamtale = kontrollsamtale,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.FantIkkeGjeldendeStønadsperiode.left()
    }

    @Test
    fun `feiler hvis vi ikke klarer å lage brev`() {
        val underliggendeFeil = KunneIkkeLageDokument.FeilVedGenereringAvPdf
        val brevService = mock<BrevService> {
            on { lagDokument(any(), anyOrNull()) } doReturn underliggendeFeil.left()
        }

        ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person.right()
            },
            brevService = brevService,
            clock = fixedClock,
        ).kontrollsamtaleService.kallInn(
            kontrollsamtale = kontrollsamtale,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeGenerereDokument(
            underliggendeFeil,
        ).left()
    }

    @Test
    fun `feiler dersom vi ikke klarer å lage oppgave`() {
        val oppgaveService = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }

        ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person.right()
            },
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadataInformasjonViktig().right()
            },
            oppgaveService = oppgaveService,
            sessionFactory = TestSessionFactory(),
            clock = fixedClock,
        ).kontrollsamtaleService.kallInn(
            kontrollsamtale = kontrollsamtale,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeKalleInn.left()
    }

    @Test
    fun `feiler hvis vi ikke klarer å lagre kontrollsamtaleinnkalling`() {
        ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person.right()
            },
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadataInformasjonViktig().right()
            },
            oppgaveService = mock {
                on { opprettOppgaveMedSystembruker(any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
            kontrollsamtaleRepo = mock {
                on { lagre(any(), any()) } doThrow RuntimeException("Fikk ikke lagret kontrollsamtale")
            },
            sessionFactory = TestSessionFactory(),
            clock = fixedClock,
        ).kontrollsamtaleService.kallInn(
            kontrollsamtale = kontrollsamtale,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeKalleInn.left()
    }

    @Test
    fun `lager brev og oppgave dersom alt går bra`() {
        val services = ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person.right()
            },
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadataInformasjonViktig().right()
            },
            oppgaveService = mock {
                on { opprettOppgaveMedSystembruker(any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
            sessionFactory = TestSessionFactory(),
            clock = fixedClock,
        )

        services.kontrollsamtaleService.kallInn(
            kontrollsamtale = kontrollsamtale,
        ) shouldBe Unit.right()

        val dokumentCaptor = ArgumentCaptor.forClass(Dokument.MedMetadata.Informasjon::class.java)
        verify(services.brevService).lagreDokument(capture<Dokument.MedMetadata.Informasjon>(dokumentCaptor), any())
        dokumentCaptor.value.opprettet shouldBe Tidspunkt.now(fixedClock)
        dokumentCaptor.value.generertDokument shouldBe minimumPdfAzeroPadded()

        verify(services.oppgaveService).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Kontrollsamtale(
                    saksnummer = sak.saksnummer,
                    fnr = sak.fnr,
                    clock = fixedClock,
                )
            },
        )
    }

    @Test
    fun `hentNestePlanlagteKontrollsamtale skal returnere left om det ikke eksisterer data i databasen`() {
        val services = ServiceOgMocks(
            kontrollsamtaleRepo = mock {
                on { hentForSakId(any(), anyOrNull()) } doReturn Kontrollsamtaler.empty(sak.id)
            },
        )
        services.kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(
            sak.id,
        ) shouldBe KunneIkkeHenteKontrollsamtale.FantIkkePlanlagtKontrollsamtale.left()
    }

    @Test
    fun `hentNestePlanlagteKontrollsamtale skal returnere left om det ikke finnes noen med status INNKALT`() {
        val services = ServiceOgMocks(
            kontrollsamtaleRepo = mock {
                on { hentForSakId(any(), anyOrNull()) } doReturn Kontrollsamtaler(
                    sak.id,
                    innkaltKontrollsamtale(sakId = sak.id),
                    gjennomførtKontrollsamtale(sakId = sak.id),
                )
            },
        )
        services.kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(
            sak.id,
        ) shouldBe KunneIkkeHenteKontrollsamtale.FantIkkePlanlagtKontrollsamtale.left()
    }

    @Test
    fun `hentNestePlanlagteKontrollsamtale skal hente den neste planlagte innkallingen om det finnes flere`() {
        val expected =
            planlagtKontrollsamtale(innkallingsdato = fixedLocalDate, sakId = sak.id)
        val services = ServiceOgMocks(
            kontrollsamtaleRepo = mock {
                on { hentForSakId(any(), anyOrNull()) } doReturn Kontrollsamtaler(
                    sak.id,
                    planlagtKontrollsamtale(innkallingsdato = fixedLocalDate.plusMonths(1), sakId = sak.id),
                    gjennomførtKontrollsamtale(sakId = sak.id),
                    expected,
                )
            },
        )
        services.kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(
            sak.id,
        ) shouldBe expected.right()
    }

    @Test
    fun `endre dato skal returnere left om det ikke finnes en sak`() {
        val services = ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn FantIkkeSak.left()
            },
        )
        shouldThrow<java.lang.IllegalArgumentException> {
            services.kontrollsamtaleService.nyDato(
                sak.id,
                fixedLocalDate.plusMonths(2),
            )
        }.message shouldBe "Fant ikke sak for sakId ${sak.id}"

        verify(services.sakService).hentSak(any<UUID>())
        verifyNoMoreInteractions(services.sakService, services.kontrollsamtaleRepo)
    }

    @Test
    fun `endre dato skal returnere left om det ikke er noen stønadsperioder fremover`() {
        val services = ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn søknadsbehandlingIverksattInnvilget().first.right()
            },
            clock = fixedClockAt(1.januar(2022)),
        )
        services.kontrollsamtaleService.nyDato(
            sak.id,
            1.januar(2022),
        ) shouldBe KunneIkkeSetteNyDatoForKontrollsamtale.FantIkkeGjeldendeStønadsperiode.left()

        verify(services.sakService).hentSak(any<UUID>())
        verifyNoMoreInteractions(services.sakService, services.kontrollsamtaleRepo)
    }

    @Test
    fun `endre dato skal endre dato ved normal flyt`() {
        val services = ServiceOgMocks(
            kontrollsamtaleRepo = mock {
                on { hentForSakId(any(), anyOrNull()) } doReturn Kontrollsamtaler(
                    sak.id,
                    planlagtKontrollsamtale(sakId = sak.id),
                )
            },
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn vedtakSøknadsbehandlingIverksattInnvilget().first.right()
            },
            clock = fixedClock,
        )
        services.kontrollsamtaleService.nyDato(sak.id, fixedLocalDate.plusMonths(2)) shouldBe Unit.right()

        verify(services.sakService).hentSak(any<UUID>())
        verify(services.kontrollsamtaleRepo).hentForSakId(any(), anyOrNull())
        verify(services.kontrollsamtaleRepo).lagre(any(), anyOrNull())
        verifyNoMoreInteractions(services.sakService, services.kontrollsamtaleRepo)
    }

    @Test
    fun `henter alle kontrollsamtaler`() {
        val sakId = UUID.randomUUID()
        val services = ServiceOgMocks(
            kontrollsamtaleRepo = mock {
                on { hentForSakId(any(), anyOrNull()) } doReturn Kontrollsamtaler(
                    sakId = sakId,
                    planlagtKontrollsamtale(sakId = sakId),
                    innkaltKontrollsamtale(sakId = sakId),
                )
            },
            clock = fixedClock,
        )

        services.kontrollsamtaleService.hentKontrollsamtaler(sakId).let {
            it.size shouldBe 2
            verify(services.kontrollsamtaleRepo).hentForSakId(argThat { it shouldBe sakId }, anyOrNull())
        }
    }

    private data class ServiceOgMocks(
        val sakService: SakService = mock(),
        val personService: PersonService = mock(),
        val brevService: BrevService = mock(),
        val oppgaveService: OppgaveService = mock(),
        val sessionFactory: SessionFactory = TestSessionFactory(),
        val clock: Clock = mock(),
        val kontrollsamtaleRepo: KontrollsamtaleRepo = mock(),
        val queryJournalpostClient: QueryJournalpostClient = mock(),
    ) {
        val kontrollsamtaleService = KontrollsamtaleServiceImpl(
            sakService = sakService,
            brevService = brevService,
            oppgaveService = oppgaveService,
            sessionFactory = sessionFactory,
            clock = clock,
            kontrollsamtaleRepo = kontrollsamtaleRepo,
            personService = personService,
            queryJournalpostClient = queryJournalpostClient,
        )
    }
}
