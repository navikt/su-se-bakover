import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.iverksattAutomatiskRegulering
import org.junit.jupiter.api.Test

class VedtakInnvilgetReguleringTest {

    @Test
    fun `Skal bruke attestant fra attesteringen`() {
        val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler")
        val attestant = NavIdentBruker.Attestant("attestant")
        val regulering = iverksattAutomatiskRegulering(saksbehandler = saksbehandler, attestant = attestant)
        val vedtak = VedtakInnvilgetRegulering.from(
            regulering = regulering,
            utbetalingId = UUID30.randomUUID(),
            clock = fixedClock,
        )

        vedtak.saksbehandler shouldBe saksbehandler
        vedtak.attestant shouldBe attestant
    }
}
