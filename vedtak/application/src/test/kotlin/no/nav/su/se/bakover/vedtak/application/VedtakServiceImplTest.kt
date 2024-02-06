package no.nav.su.se.bakover.vedtak.application

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.test.argShouldBe
import no.nav.su.se.bakover.test.enUkeEtterFixedClock
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattAvslagUtenBeregning
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.PersonService
import java.time.Clock
import java.util.UUID

class VedtakServiceImplTest {

    @Test
    fun `dersom vedtak er Avslagsvedtak, vil kan kunne starte en ny søknadsbehandling`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattAvslagUtenBeregning()

        val vedtakRepo = mock<VedtakRepo> { on { hentVedtakForId(any()) } doReturn vedtak }
        val sakService = mock<SakService> { on { hentSakForVedtak(any<UUID>()) } doReturn sak }
        val personService = mock<PersonService> { on { hentAktørId(any()) } doReturn AktørId("123").right() }
        val oppgaveService =
            mock<OppgaveService> { on { opprettOppgave(any()) } doReturn nyOppgaveHttpKallResponse().right() }

        val service = Services(vedtakRepo, sakService, oppgaveService, personService)

        service.testableService().startNySøknadsbehandlingForAvslag(vedtak.id, saksbehandler).shouldBeRight()

        verify(vedtakRepo).hentVedtakForId(argShouldBe(vedtak.id))
        verify(sakService).hentSakForVedtak(argShouldBe(vedtak.id))
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

        service.verifyNoMoreInteractions()
    }

    @Test
    fun `dersom vedtak ikke er Avslagsvedtak, får man feil`() {
        val (_, vedtak) = vedtakRevurdering()

        val vedtakRepo = mock<VedtakRepo> { on { hentVedtakForId(any()) } doReturn vedtak }
        val service = Services(vedtakRepo)
        service.testableService().startNySøknadsbehandlingForAvslag(vedtak.id, saksbehandler).shouldBeLeft()
        verify(vedtakRepo).hentVedtakForId(argShouldBe(vedtak.id))
        service.verifyNoMoreInteractions()
    }

    private data class Services(
        private val vedtakRepo: VedtakRepo = mock(),
        private val sakService: SakService = mock(),
        private val oppgaveService: OppgaveService = mock(),
        private val personService: PersonService = mock(),
        private val clock: Clock = enUkeEtterFixedClock,
    ) {
        fun testableService() = VedtakServiceImpl(
            vedtakRepo = vedtakRepo,
            sakService = sakService,
            oppgaveService = oppgaveService,
            personservice = personService,
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
