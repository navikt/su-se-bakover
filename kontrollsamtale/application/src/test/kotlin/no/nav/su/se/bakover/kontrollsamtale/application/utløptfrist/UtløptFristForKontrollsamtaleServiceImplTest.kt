package no.nav.su.se.bakover.kontrollsamtale.application.utløptfrist

import arrow.core.right
import dokument.domain.journalføring.QueryJournalpostClient
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleJobRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.kontrollsamtale.innkaltKontrollsamtale
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import person.domain.PersonService
import java.time.Clock

internal class UtløptFristForKontrollsamtaleServiceImplTest {

    @Test
    fun `bruker første dag i neste måned som stansdato dersom person er død`() {
        val clock = TikkendeKlokke(fixedClockAt(15.januar(2021)))
        val (sak, simulertStans) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(clock = clock)
        val kontrollsamtale = innkaltKontrollsamtale(
            sakId = sak.id,
            frist = 14.januar(2021),
        )
        val services = ServiceOgMocks(
            sakService = mock {
                on { hentSak(kontrollsamtale.sakId) } doReturn sak.right()
            },
            personService = mock {
                on { hentPersonMedSystembruker(sak.fnr, sak.type) } doReturn person(
                    fnr = sak.fnr,
                    dødsdato = 10.januar(2021),
                ).right()
            },
            kontrollsamtaleService = mock {
                on { hentFristUtløptFørEllerPåDato(15.januar(2021)) } doReturn kontrollsamtale.frist
                on { hentInnkalteKontrollsamtalerMedFristUtløptPåDato(kontrollsamtale.frist) } doReturn listOf(kontrollsamtale)
            },
            stansYtelseService = mock {
                on { stansAvYtelseITransaksjon(any(), any()) } doReturn simulertStans
            },
            clock = clock,
        )

        services.utløptFristForKontrollsamtaleService.stansStønadsperioderHvorKontrollsamtaleHarUtløptFrist()

        val requestCaptor = argumentCaptor<StansYtelseRequest>()
        verify(services.stansYtelseService).stansAvYtelseITransaksjon(
            requestCaptor.capture(),
            any(),
        )
        (requestCaptor.firstValue as StansYtelseRequest.Opprett).fraOgMed.dato shouldBe 1.februar(2021)
    }

    private data class ServiceOgMocks(
        val sakService: SakService = mock(),
        val queryJournalpostClient: QueryJournalpostClient = mock(),
        val kontrollsamtaleService: KontrollsamtaleService = mock(),
        val stansYtelseService: StansYtelseService = mock(),
        val sessionFactory: SessionFactory = TestSessionFactory(),
        val clock: Clock = fixedClockAt(),
        val oppgaveService: OppgaveService = mock(),
        val kontrollsamtaleJobRepo: KontrollsamtaleJobRepo = mock(),
        val kontrollsamtaleRepo: KontrollsamtaleRepo = mock(),
        val personService: PersonService = mock(),
    ) {
        val utløptFristForKontrollsamtaleService = UtløptFristForKontrollsamtaleServiceImpl(
            sakService = sakService,
            queryJournalpostClient = queryJournalpostClient,
            kontrollsamtaleService = kontrollsamtaleService,
            stansAvYtelseService = stansYtelseService,
            sessionFactory = sessionFactory,
            clock = clock,
            serviceUser = "Z123456",
            oppgaveService = oppgaveService,
            kontrollsamtaleJobRepo = kontrollsamtaleJobRepo,
            kontrollsamtaleRepo = kontrollsamtaleRepo,
            personService = personService,
        )
    }
}
