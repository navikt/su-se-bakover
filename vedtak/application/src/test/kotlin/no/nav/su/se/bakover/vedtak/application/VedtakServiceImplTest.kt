package no.nav.su.se.bakover.vedtak.application

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.test.argShouldBe
import no.nav.su.se.bakover.test.enUkeEtterFixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattAvslagUtenBeregning
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import person.domain.PersonService
import java.time.Clock
import java.util.UUID

class VedtakServiceImplTest {

    @Test
    fun `dersom vedtak er Avslagsvedtak, vil kan kunne starte en ny søknadsbehandling`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattAvslagUtenBeregning()

        val sakService = mock<SakService> { on { hentSak(any<UUID>()) } doReturn sak.right() }
        val personService = mock<PersonService> { on { hentAktørId(any()) } doReturn AktørId("123").right() }
        val oppgaveService =
            mock<OppgaveService> { on { opprettOppgave(any()) } doReturn nyOppgaveHttpKallResponse().right() }
        val søknadsbehandlingService = mock<SøknadsbehandlingService> {
            doNothing().whenever(it).lagre(any())
        }
        val service = Services(
            sakService = sakService,
            oppgaveService = oppgaveService,
            personService = personService,
            søknadsbehandlingService = søknadsbehandlingService,
        )

        val actual = service.testableService().startNySøknadsbehandlingForAvslag(sak.id, vedtak.id, saksbehandler).getOrFail()

        verify(sakService).hentSak(argShouldBe(sak.id))
        verify(personService).hentAktørId(argShouldBe(sak.fnr))
        verify(oppgaveService).opprettOppgave(
            argShouldBe(
                OppgaveConfig.Søknad(
                    journalpostId = vedtak.behandling.søknad.journalpostId,
                    søknadId = vedtak.behandling.søknad.id,
                    aktørId = AktørId("123"),
                    tilordnetRessurs = saksbehandler,
                    clock = enUkeEtterFixedClock,
                    sakstype = Sakstype.UFØRE,
                ),
            ),
        )
        verify(søknadsbehandlingService).lagre(argShouldBe(actual))

        service.verifyNoMoreInteractions()
    }

    @Test
    fun `dersom vedtak ikke er Avslagsvedtak, får man feil`() {
        val (sak, vedtak) = vedtakRevurdering()

        val sakService = mock<SakService> { on { hentSak(any<UUID>()) } doReturn sak.right() }
        val service = Services(sakService = sakService)
        service.testableService().startNySøknadsbehandlingForAvslag(sak.id, vedtak.id, saksbehandler).shouldBeLeft()
        verify(sakService).hentSak(argShouldBe(sak.id))
        service.verifyNoMoreInteractions()
    }

    private data class Services(
        private val vedtakRepo: VedtakRepo = mock(),
        private val sakService: SakService = mock(),
        private val oppgaveService: OppgaveService = mock(),
        private val personService: PersonService = mock(),
        private val søknadsbehandlingService: SøknadsbehandlingService = mock(),
        private val clock: Clock = enUkeEtterFixedClock,
    ) {
        fun testableService() = VedtakServiceImpl(
            vedtakRepo = vedtakRepo,
            sakService = sakService,
            oppgaveService = oppgaveService,
            personservice = personService,
            søknadsbehandlingService = søknadsbehandlingService,
            clock = clock,
        )

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(vedtakRepo)
            verifyNoMoreInteractions(sakService)
            verifyNoMoreInteractions(oppgaveService)
            verifyNoMoreInteractions(personService)
        }
    }
}
