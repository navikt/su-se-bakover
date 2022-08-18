package no.nav.su.se.bakover.service.kontrollsamtale

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.kontrollsamtale.KontrollsamtaleRepo
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtalestatus
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.kontrollsamtale
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
import java.time.Clock
import java.util.UUID

internal class KontrollsamtaleServiceImplTest {

    private val sak = vedtakSøknadsbehandlingIverksattInnvilget().first
    private val person = person()
    private val pdf = ByteArray(1)
    private val kontrollsamtale = kontrollsamtale()

    @Test
    fun `feiler hvis vi ikke finner sak`() {
        ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn FantIkkeSak.left()
            },
        ).kontrollsamtaleService.kallInn(
            sakId = sak.id,
            kontrollsamtale = kontrollsamtale,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.FantIkkeSak.left()
    }

    @Test
    fun `feiler hvis vi ikke finner gjeldende stønadsperiode på sak`() {
        ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.copy(vedtakListe = emptyList()).right()
            },
        ).kontrollsamtaleService.kallInn(
            sakId = sak.id,
            kontrollsamtale = kontrollsamtale,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.FantIkkeGjeldendeStønadsperiode.left()
    }

    @Test
    fun `feiler hvis vi ikke finner person`() {
        val personService = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            personService = personService,
            clock = fixedClock,
        ).kontrollsamtaleService.kallInn(
            sakId = sak.id,
            kontrollsamtale = kontrollsamtale,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.FantIkkePerson.left()
    }

    @Test
    fun `feiler hvis vi ikke klarer å lage brev`() {
        val brevService = mock<BrevService> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
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
            sakId = sak.id,
            kontrollsamtale = kontrollsamtale,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeGenerereDokument.left()
    }

    @Test
    fun `feiler dersom vi ikke klarer å lage oppgave`() {
        val oppgaveService = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveFeil.KunneIkkeOppretteOppgave.left()
        }

        ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(any()) } doReturn person.right()
            },
            brevService = mock {
                on { lagBrev(any()) } doReturn ByteArray(1).right()
            },
            oppgaveService = oppgaveService,
            sessionFactory = TestSessionFactory(),
            clock = fixedClock,
        ).kontrollsamtaleService.kallInn(
            sakId = sak.id,
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
                on { lagBrev(any()) } doReturn pdf.right()
            },
            oppgaveService = mock {
                on { opprettOppgaveMedSystembruker(any()) } doReturn OppgaveId("oppgaveId").right()
            },
            kontrollsamtaleRepo = mock {
                on { lagre(any(), any()) } doThrow RuntimeException("Fikk ikke lagret kontrollsamtale")
            },
            sessionFactory = TestSessionFactory(),
            clock = fixedClock,
        ).kontrollsamtaleService.kallInn(
            sakId = sak.id,
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
                on { lagBrev(any()) } doReturn pdf.right()
            },
            oppgaveService = mock {
                on { opprettOppgaveMedSystembruker(any()) } doReturn OppgaveId("oppgaveId").right()
            },
            sessionFactory = TestSessionFactory(),
            clock = fixedClock,
        )

        services.kontrollsamtaleService.kallInn(
            sakId = sak.id,
            kontrollsamtale = kontrollsamtale,
        ) shouldBe Unit.right()

        val dokumentCaptor = ArgumentCaptor.forClass(Dokument.MedMetadata.Informasjon::class.java)
        verify(services.brevService).lagreDokument(capture<Dokument.MedMetadata.Informasjon>(dokumentCaptor), any())
        dokumentCaptor.value.opprettet shouldBe Tidspunkt.now(fixedClock)
        dokumentCaptor.value.generertDokument shouldBe pdf

        verify(services.oppgaveService).opprettOppgaveMedSystembruker(
            argThat {
                it shouldBe OppgaveConfig.Kontrollsamtale(
                    saksnummer = sak.saksnummer,
                    aktørId = person.ident.aktørId,
                    clock = fixedClock,
                )
            },
        )
    }

    @Test
    fun `hentNestePlanlagteKontrollsamtale skal returnere left om det ikke eksisterer data i databasen`() {
        val services = ServiceOgMocks(
            kontrollsamtaleRepo = mock {
                on { hentForSakId(any(), anyOrNull()) } doReturn emptyList()
                on { defaultSessionContext() } doReturn TestSessionFactory.sessionContext
            },
        )
        services.kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(
            sak.id,
        ) shouldBe KunneIkkeHenteKontrollsamtale.FantIkkeKontrollsamtale.left()
    }

    @Test
    fun `hentNestePlanlagteKontrollsamtale skal returnere left om det ikke finnes noen med status INNKALT`() {
        val services = ServiceOgMocks(
            kontrollsamtaleRepo = mock {
                on { hentForSakId(any(), anyOrNull()) } doReturn listOf(
                    kontrollsamtale(status = Kontrollsamtalestatus.INNKALT),
                    kontrollsamtale(status = Kontrollsamtalestatus.GJENNOMFØRT),
                )
                on { defaultSessionContext() } doReturn TestSessionFactory.sessionContext
            },
        )
        services.kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(
            sak.id,
        ) shouldBe KunneIkkeHenteKontrollsamtale.FantIkkeKontrollsamtale.left()
    }

    @Test
    fun `hentNestePlanlagteKontrollsamtale skal hente den neste planlagte innkallingen om det finnes flere`() {
        val expected =
            kontrollsamtale(status = Kontrollsamtalestatus.PLANLAGT_INNKALLING, innkallingsdato = fixedLocalDate)
        val services = ServiceOgMocks(
            kontrollsamtaleRepo = mock {
                on { hentForSakId(any(), anyOrNull()) } doReturn listOf(
                    kontrollsamtale(
                        status = Kontrollsamtalestatus.PLANLAGT_INNKALLING,
                        innkallingsdato = fixedLocalDate.plusMonths(1),
                    ),
                    kontrollsamtale(status = Kontrollsamtalestatus.GJENNOMFØRT),
                    expected,
                )
                on { defaultSessionContext() } doReturn TestSessionFactory.sessionContext
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
        services.kontrollsamtaleService.nyDato(
            sak.id,
            fixedLocalDate.plusMonths(2),
        ) shouldBe KunneIkkeSetteNyDatoForKontrollsamtale.FantIkkeSak.left()

        verify(services.sakService).hentSak(any<UUID>())
        verifyNoMoreInteractions(services.sakService, services.kontrollsamtaleRepo)
    }

    @Test
    fun `endre dato skal returnere left om det ikke er noen stønadsperioder fremover`() {
        val services = ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn søknadsbehandlingIverksattInnvilget().first.right()
            },
            clock = fixedClock
        )
        services.kontrollsamtaleService.nyDato(
            sak.id,
            fixedLocalDate.plusMonths(2),
        ) shouldBe KunneIkkeSetteNyDatoForKontrollsamtale.FantIkkeGjeldendeStønadsperiode.left()

        verify(services.sakService).hentSak(any<UUID>())
        verifyNoMoreInteractions(services.sakService, services.kontrollsamtaleRepo)
    }

    @Test
    fun `endre dato skal endre dato ved normal flyt`() {
        val services = ServiceOgMocks(
            kontrollsamtaleRepo = mock {
                on { hentForSakId(any(), anyOrNull()) } doReturn listOf(kontrollsamtale())
                on { defaultSessionContext() } doReturn TestSessionFactory.sessionContext
            },
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn vedtakSøknadsbehandlingIverksattInnvilget().first.right()
            },
            clock = fixedClock,
        )
        services.kontrollsamtaleService.nyDato(sak.id, fixedLocalDate.plusMonths(2)) shouldBe Unit.right()

        verify(services.sakService).hentSak(any<UUID>())
        verify(services.kontrollsamtaleRepo).hentForSakId(any(), anyOrNull())
        verify(services.kontrollsamtaleRepo).defaultSessionContext()
        verify(services.kontrollsamtaleRepo).lagre(any(), anyOrNull())
        verifyNoMoreInteractions(services.sakService, services.kontrollsamtaleRepo)
    }

    private data class ServiceOgMocks(
        val sakService: SakService = mock(),
        val personService: PersonService = mock(),
        val brevService: BrevService = mock(),
        val oppgaveService: OppgaveService = mock(),
        val sessionFactory: SessionFactory = TestSessionFactory(),
        val clock: Clock = mock(),
        val kontrollsamtaleRepo: KontrollsamtaleRepo = mock(),
    ) {
        val kontrollsamtaleService = KontrollsamtaleServiceImpl(
            sakService = sakService,
            personService = personService,
            brevService = brevService,
            oppgaveService = oppgaveService,
            sessionFactory = sessionFactory,
            clock = clock,
            kontrollsamtaleRepo = kontrollsamtaleRepo,
        )
    }
}
