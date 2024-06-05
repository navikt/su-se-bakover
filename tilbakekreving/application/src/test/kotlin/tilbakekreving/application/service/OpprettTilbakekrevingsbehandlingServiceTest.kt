package tilbakekreving.application.service

import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.correlationId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.hendelse.defaultHendelseMetadata
import no.nav.su.se.bakover.test.kravgrunnlag.sakMedUteståendeKravgrunnlag
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import person.domain.PersonService
import tilbakekreving.application.service.opprett.OpprettTilbakekrevingsbehandlingService
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.opprettelse.OpprettTilbakekrevingsbehandlingCommand
import tilgangstyring.application.TilgangstyringService
import java.time.Clock
import java.util.UUID

class OpprettTilbakekrevingsbehandlingServiceTest {

    @Test
    fun `oppretter ny manuell tilbakekrevingsbehandling`() {
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))

        val correlationId = correlationId()
        val opprettetAv = saksbehandler
        val brukerroller = nonEmptyListOf(Brukerrolle.Saksbehandler)

        val sakMedKravgrunnlag = sakMedUteståendeKravgrunnlag(clock = clock)
        val sakId = sakMedKravgrunnlag.id
        val kravgrunnlag = sakMedKravgrunnlag.uteståendeKravgrunnlag!!
        val tilgangstyringService = mock<TilgangstyringService> {
            on { assertHarTilgangTilSak(any()) } doReturn Unit.right()
        }
        val sakService = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sakMedKravgrunnlag.right()
        }

        val mocks = mockedServices(
            tilgangstyringService = tilgangstyringService,
            sakService = sakService,
            clock = clock,
        )
        mocks.service().opprett(
            command = OpprettTilbakekrevingsbehandlingCommand(
                sakId = sakId,
                opprettetAv = opprettetAv,
                correlationId = correlationId,
                brukerroller = brukerroller,
                klientensSisteSaksversjon = Hendelsesversjon(1),
            ),
        ).shouldBeRight()

        verify(mocks.sakService).hentSak(
            argThat<UUID> { it shouldBe sakId },
        )

        verify(mocks.tilbakekrevingsbehandlingRepo).lagre(
            argThat {
                it shouldBe OpprettetTilbakekrevingsbehandlingHendelse(
                    // Denne blir generert av domenet.
                    hendelseId = it.hendelseId,
                    sakId = sakId,
                    // vi bruker tikkende-klokke
                    hendelsestidspunkt = it.hendelsestidspunkt,
                    versjon = Hendelsesversjon(value = 2),
                    // Denne blir generert av domenet.
                    id = it.id,
                    opprettetAv = opprettetAv,
                    kravgrunnlagPåSakHendelseId = kravgrunnlag.hendelseId,
                )
            },
            argThat {
                it shouldBe defaultHendelseMetadata(
                    correlationId = correlationId,
                    brukerroller = brukerroller,
                )
            },
            anyOrNull(),
        )
        mocks.verifyNoMoreInteractions()
    }

    private data class mockedServices(
        val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo = mock(),
        val tilgangstyringService: TilgangstyringService = mock(),
        val clock: Clock = fixedClock,
        val sakService: SakService = mock(),
        val oppgaveService: OppgaveService = mock(),
        val personService: PersonService = mock(),
        val oppgaveHendelseRepo: OppgaveHendelseRepo = mock(),
        val sessionFactory: TestSessionFactory = TestSessionFactory(),
    ) {
        fun service(): OpprettTilbakekrevingsbehandlingService =
            OpprettTilbakekrevingsbehandlingService(
                tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
                tilgangstyring = tilgangstyringService,
                sakService = sakService,
                clock = clock,
            )

        fun verifyNoMoreInteractions() {
            verify(tilgangstyringService).assertHarTilgangTilSak(any())
            org.mockito.kotlin.verifyNoMoreInteractions(
                tilbakekrevingsbehandlingRepo,
                tilgangstyringService,
                sakService,
            )
        }
    }
}
