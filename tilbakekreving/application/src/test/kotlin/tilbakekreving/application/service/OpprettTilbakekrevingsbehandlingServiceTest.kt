package tilbakekreving.application.service

import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.correlationId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.kravgrunnlag.sakMedUteståendeKravgrunnlag
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.application.service.opprett.OpprettTilbakekrevingsbehandlingService
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.opprett.OpprettTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
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
        val tilgangstyringService = mock<TilbakekrevingsbehandlingTilgangstyringService> {
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
            argThat<UUID> {
                it shouldBe sakId
            },
        )
        verify(mocks.tilbakekrevingsbehandlingRepo).lagre(
            argThat {
                it shouldBe OpprettetTilbakekrevingsbehandlingHendelse(
                    hendelseId = it.hendelseId, // Denne blir generert av domenet.
                    sakId = sakId,
                    hendelsestidspunkt = it.hendelsestidspunkt, // vi bruker tikkende-klokke
                    versjon = Hendelsesversjon(value = 2),
                    meta = DefaultHendelseMetadata(
                        correlationId = correlationId,
                        ident = saksbehandler,
                        brukerroller = brukerroller,
                    ),
                    id = it.id, // Denne blir generert av domenet.
                    opprettetAv = opprettetAv,
                    kravgrunnlagsId = kravgrunnlag.eksternKravgrunnlagId,
                )
            },
            anyOrNull(),
        )
        mocks.verifyNoMoreInteractions()
    }

    private data class mockedServices(
        val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo = mock(),
        val tilgangstyringService: TilbakekrevingsbehandlingTilgangstyringService = mock(),
        val clock: Clock = fixedClock,
        val sakService: SakService = mock(),
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
