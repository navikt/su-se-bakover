package tilbakekreving.application.service

import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.correlationId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.nyKravgrunnlag
import no.nav.su.se.bakover.test.nyRåttKravgrunnlag
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.opprett.OpprettTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import java.time.Clock
import java.util.UUID

class OpprettTilbakekrevingsbehandlingServiceTest {

    @Test
    fun `oppretter ny manuell tilbakekrevingsbehandling`() {
        val clock = TikkendeKlokke()
        val sakId = UUID.randomUUID()
        val correlationId = correlationId()
        val opprettetAv = saksbehandler
        val brukerroller = nonEmptyListOf(Brukerrolle.Saksbehandler)
        val kravgrunnlag = nyKravgrunnlag()

        val kravgrunnlagRepo = mock<KravgrunnlagRepo> {
            on { hentRåttÅpentKravgrunnlagForSak(any(), anyOrNull()) } doReturn nyRåttKravgrunnlag()
        }
        val tilgangstyringService = mock<TilbakekrevingsbehandlingTilgangstyringService> {
            on { assertHarTilgangTilSak(any()) } doReturn Unit.right()
        }
        val hendelseRepo = mock<HendelseRepo> {
            on { hentSisteVersjonFraEntitetId(any(), anyOrNull()) } doReturn Hendelsesversjon(1)
        }

        val mocks = mockedServices(
            kravgrunnlagRepo = kravgrunnlagRepo,
            tilgangstyringService = tilgangstyringService,
            hendelseRepo = hendelseRepo,
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
            kravgrunnlagMapper = { kravgrunnlag.right() },
        ).shouldBeRight()

        verify(kravgrunnlagRepo).hentRåttÅpentKravgrunnlagForSak(argThat { it shouldBe sakId }, anyOrNull())
        verify(mocks.tilbakekrevingsbehandlingRepo).lagre(
            argThat {
                it shouldBe OpprettetTilbakekrevingsbehandlingHendelse(
                    hendelseId = it.hendelseId, // Denne blir generert av domenet.
                    sakId = sakId,
                    hendelsestidspunkt = it.hendelsestidspunkt, // vi bruker tikkende-klokke
                    versjon = Hendelsesversjon(value = 2),
                    meta = HendelseMetadata(
                        correlationId = correlationId,
                        ident = saksbehandler,
                        brukerroller = brukerroller,
                    ),
                    id = it.id, // Denne blir generert av domenet.
                    opprettetAv = opprettetAv,
                    kravgrunnlagsId = kravgrunnlag.kravgrunnlagId,
                )
            },
            anyOrNull(),
        )
        verify(mocks.hendelseRepo).hentSisteVersjonFraEntitetId(argThat { it shouldBe sakId }, anyOrNull())
        mocks.verifyNoMoreInteractions()
    }

    private data class mockedServices(
        val kravgrunnlagRepo: KravgrunnlagRepo = mock(),
        val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo = mock(),
        val tilgangstyringService: TilbakekrevingsbehandlingTilgangstyringService = mock(),
        val hendelseRepo: HendelseRepo = mock(),
        val clock: Clock = fixedClock,
    ) {
        fun service(): OpprettTilbakekrevingsbehandlingService =
            OpprettTilbakekrevingsbehandlingService(
                tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
                kravgrunnlagRepo = kravgrunnlagRepo,
                tilgangstyring = tilgangstyringService,
                clock = clock,
                hendelseRepo = hendelseRepo,
            )

        fun verifyNoMoreInteractions() {
            verify(tilgangstyringService).assertHarTilgangTilSak(any())
            org.mockito.kotlin.verifyNoMoreInteractions(kravgrunnlagRepo)
        }
    }
}
