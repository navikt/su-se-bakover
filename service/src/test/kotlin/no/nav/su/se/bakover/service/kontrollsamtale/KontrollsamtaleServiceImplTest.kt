package no.nav.su.se.bakover.service.kontrollsamtale

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Clock
import java.util.UUID

internal class KontrollsamtaleServiceImplTest {

    val saksbehandler: NavIdentBruker = NavIdentBruker.Saksbehandler("Z999999")
    val sakId: UUID = UUID.randomUUID()
    val fnr = Fnr(fnr = "12345678901")
    val person = Person(
        ident = Ident(
            fnr = fnr,
            aktørId = AktørId(aktørId = "123"),
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = null, etternavn = "Strømøy"),
    )
    val sak = Sak(
        id = UUID.randomUUID(),
        saksnummer = Saksnummer(9999),
        opprettet = fixedTidspunkt,
        fnr = fnr,
        søknader = listOf(),
        søknadsbehandlinger = listOf(),
        utbetalinger = listOf(),
        revurderinger = listOf(),
        vedtakListe = listOf(),
    )

    @Test
    fun `feiler hvis vi ikke finner sak`() {
        val sakService = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn FantIkkeSak.left()
        }

        ServiceOgMocks(
            sakService = sakService,
        ).kontrollsamtaleService.kallInn(
            sakId,
            saksbehandler,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.FantIkkeSak.left()
    }

    @Test
    fun `feiler hvis vi ikke finner person`() {
        val personService = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            personService = personService,
        ).kontrollsamtaleService.kallInn(
            sakId,
            saksbehandler,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.FantIkkePerson.left()
    }

    @Test
    fun `feiler hvis vi ikke finner saksbehandler-navn`() {
        val graphApi = mock<MicrosoftGraphApiOppslag> {
            on { hentNavnForNavIdent(any()) } doReturn MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
        }

        ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            personService = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            microsoftGraphApiOppslag = graphApi,
        ).kontrollsamtaleService.kallInn(
            sakId,
            saksbehandler,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()
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
                on { hentPerson(any()) } doReturn person.right()
            },
            microsoftGraphApiOppslag = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Saksbehandlers Navn".right()
            },
            brevService = brevService,
            clock = Clock.systemUTC(),
        ).kontrollsamtaleService.kallInn(
            sakId,
            saksbehandler,
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
                on { hentPerson(any()) } doReturn person.right()
            },
            microsoftGraphApiOppslag = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Saksbehandlers Navn".right()
            },
            brevService = mock {
                on { lagBrev(any()) } doReturn ByteArray(1).right()
            },
            oppgaveService = oppgaveService,
            sessionFactory = TestSessionFactory(),
            clock = Clock.systemUTC(),
        ).kontrollsamtaleService.kallInn(
            sakId,
            saksbehandler,
        ) shouldBe KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeKalleInn.left()
    }

    @Test
    fun `lager brev og oppgave dersom alt går bra`() {
        val pdf = ByteArray(1)

        val services = ServiceOgMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            personService = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            microsoftGraphApiOppslag = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Saksbehandlers Navn".right()
            },
            brevService = mock {
                on { lagBrev(any()) } doReturn pdf.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
            },
            sessionFactory = TestSessionFactory(),
            clock = fixedClock,
        )

        services.kontrollsamtaleService.kallInn(
            sakId,
            saksbehandler,
        ) shouldBe Unit.right()

        val dokumentCaptor = ArgumentCaptor.forClass(Dokument.MedMetadata.Informasjon::class.java)
        verify(services.brevService).lagreDokument(capture<Dokument.MedMetadata.Informasjon>(dokumentCaptor), any())
        dokumentCaptor.value.opprettet shouldBe Tidspunkt.now(fixedClock)
        dokumentCaptor.value.generertDokument shouldBe pdf

        verify(services.oppgaveService).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Kontrollsamtale(
                    sak.saksnummer,
                    person.ident.aktørId,
                )
            },
        )
    }

    private data class ServiceOgMocks(
        val sakService: SakService = mock(),
        val personService: PersonService = mock(),
        val brevService: BrevService = mock(),
        val oppgaveService: OppgaveService = mock(),
        val microsoftGraphApiOppslag: MicrosoftGraphApiOppslag = mock(),
        val sessionFactory: SessionFactory = mock(),
        val clock: Clock = mock(),
    ) {
        val kontrollsamtaleService = KontrollsamtaleServiceImpl(
            sakService = sakService,
            personService = personService,
            brevService = brevService,
            oppgaveService = oppgaveService,
            microsoftGraphApiOppslag = microsoftGraphApiOppslag,
            sessionFactory = sessionFactory,
            clock = clock,
        )
    }
}
