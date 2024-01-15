package tilbakekreving.infrastructure.repo.kravgrunnlag.utestående

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import tilbakekreving.domain.IverksattHendelse

internal class KravgrunnlagOgIverksatteTilbakekrevingerPostgresRepoTest {

    @Test
    fun `kan hente på tvers av saker`() {
        // For å feilutbetaling for januar.
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)

            val (sak1, _, _, _, hendelser1) = testDataHelper.persisterIverksattTilbakekrevingsbehandlingHendelse()
            val (sak2, _, _, _, hendelser2) = testDataHelper.persisterIverksattTilbakekrevingsbehandlingHendelse()

            val kravgrunnlagPåSakHendelser1 =
                testDataHelper.kravgrunnlagPostgresRepo.hentKravgrunnlagPåSakHendelser(sak1.id)
            val kravgrunnlagPåSakHendelser2 =
                testDataHelper.kravgrunnlagPostgresRepo.hentKravgrunnlagPåSakHendelser(sak2.id)
            testDataHelper.kravgrunnlagOgIverksatteTilbakekrevingerPostgresRepo.hentKravgrunnlagOgIverksatteTilbakekrevinger(
                null,
            ) shouldBe Pair(
                kravgrunnlagPåSakHendelser1.hendelser + kravgrunnlagPåSakHendelser2.hendelser,
                (hendelser1 + hendelser2).filterIsInstance<IverksattHendelse>(),
            )
        }
    }
}
