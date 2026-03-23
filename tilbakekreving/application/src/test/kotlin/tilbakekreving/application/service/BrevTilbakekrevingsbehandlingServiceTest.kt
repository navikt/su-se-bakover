import arrow.core.Nel
import arrow.core.nonEmptyListOf
import arrow.core.right
import dokument.domain.brev.Brevvalg
import io.kotest.assertions.arrow.core.shouldBeRight
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.domain.fritekst.FritekstDomain
import no.nav.su.se.bakover.domain.fritekst.FritekstService
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.correlationId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import tilbakekreving.application.service.vurder.BrevTilbakekrevingsbehandlingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.vedtaksbrev.OppdaterVedtaksbrevCommand
import tilgangstyring.application.TilgangstyringService
import java.util.UUID

class BrevTilbakekrevingsbehandlingServiceTest {
    private val tilgangstyringService = mock<TilgangstyringService> {
        on { assertHarTilgangTilSak(any()) } doReturn Unit.right()
    }
    private val sakService = mock<SakService> {}
    private val tilbakekrevingsbehandlingRepo = mock<TilbakekrevingsbehandlingRepo>()
    private val fritekstService = mock<FritekstService>()
    private val clock = fixedClock
    private val service = BrevTilbakekrevingsbehandlingService(
        tilgangstyring = tilgangstyringService,
        sakService = sakService,
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
        clock = clock,
        fritekstService = fritekstService,
    )

    private val sakId = UUID.randomUUID()
    private val behandlingId = TilbakekrevingsbehandlingId.generer()
    private val brukerroller: Nel<Brukerrolle> = nonEmptyListOf(Brukerrolle.Saksbehandler)

    @Test
    fun `lagrer fritekst når skal sende brev med fritekst`() {
        val command = OppdaterVedtaksbrevCommand(
            sakId = sakId,
            behandlingId = behandlingId,
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(fritekst = "fritekst"),
            utførtAv = saksbehandler,
            correlationId = correlationId(),
            brukerroller = brukerroller,
            klientensSisteSaksversjon = Hendelsesversjon(1),
        )
        service.lagreFritekstTilbakekreving(command).shouldBeRight()

        verify(fritekstService).lagreFritekst(
            argThat { fritekst ->
                fritekst == FritekstDomain(
                    referanseId = behandlingId.value,
                    sakId = sakId,
                    type = FritekstType.VEDTAKSBREV_TILBAKEKREVING,
                    fritekst = "fritekst",
                )
            },
        )
        verify(fritekstService, never()).slettFritekst(
            referanseId = any(),
            type = any(),
            sakId = any(),
        )
    }

    @Test
    fun `sletter fritekst når skal ikke sende brev`() {
        val command = OppdaterVedtaksbrevCommand(
            sakId = sakId,
            behandlingId = behandlingId,
            brevvalg = Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev(),
            utførtAv = saksbehandler,
            correlationId = correlationId(),
            brukerroller = brukerroller,
            klientensSisteSaksversjon = Hendelsesversjon(1),
        )
        service.lagreFritekstTilbakekreving(command).shouldBeRight()

        verify(fritekstService).slettFritekst(
            referanseId = behandlingId.value,
            type = FritekstType.VEDTAKSBREV_TILBAKEKREVING,
            sakId = sakId,
        )
        verify(fritekstService, never()).lagreFritekst(
            any(),
        )
    }
}
