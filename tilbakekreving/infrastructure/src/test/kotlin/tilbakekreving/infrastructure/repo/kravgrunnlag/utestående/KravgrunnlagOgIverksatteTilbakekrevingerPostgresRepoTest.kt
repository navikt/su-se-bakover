package tilbakekreving.infrastructure.repo.kravgrunnlag.utestående

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

internal class KravgrunnlagOgIverksatteTilbakekrevingerPostgresRepoTest {

    @Test
    fun `kan hente på tvers av saker`() {
        // For å feilutbetaling for januar.
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)

            val (sak1, _, _, _, _) = testDataHelper.persisterIverksattTilbakekrevingsbehandlingHendelse()
            val (sak2, _, _, _, _) = testDataHelper.persisterIverksattTilbakekrevingsbehandlingHendelse()
            val (_, _, _, _, _, krav3, _, _) = testDataHelper.persisterOpprettetTilbakekrevingsbehandlingHendelse()
            val (_, _, _, _, _, krav4, _, _) = testDataHelper.persisterAvbruttTilbakekrevingsbehandlingHendelse()

            val krav1 =
                testDataHelper.kravgrunnlagPostgresRepo.hentKravgrunnlagPåSakHendelser(sak1.id).detaljerSortert.single()

            val krav2 =
                testDataHelper.kravgrunnlagPostgresRepo.hentKravgrunnlagPåSakHendelser(sak2.id).detaljerSortert.single()

            val actual =
                testDataHelper.kravgrunnlagOgIverksatteTilbakekrevingerPostgresRepo.hentBehandlingssammendrag(
                    null,
                ).sortedBy { it.saksnummer.nummer }
            actual shouldBe
                listOf(
                    Behandlingssammendrag(
                        saksnummer = krav1.saksnummer,
                        periode = krav1.kravgrunnlag.periode,
                        behandlingstype = Behandlingssammendrag.Behandlingstype.KRAVGRUNNLAG,
                        behandlingStartet = krav1.eksternTidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.IVERKSATT,
                    ),
                    Behandlingssammendrag(
                        saksnummer = krav2.saksnummer,
                        periode = krav2.kravgrunnlag.periode,
                        behandlingstype = Behandlingssammendrag.Behandlingstype.KRAVGRUNNLAG,
                        behandlingStartet = krav2.eksternTidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.IVERKSATT,
                    ),
                    Behandlingssammendrag(
                        saksnummer = krav3.saksnummer,
                        periode = krav3.kravgrunnlag.periode,
                        behandlingstype = Behandlingssammendrag.Behandlingstype.KRAVGRUNNLAG,
                        behandlingStartet = krav3.eksternTidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.ÅPEN,
                    ),
                    Behandlingssammendrag(
                        saksnummer = krav4.saksnummer,
                        periode = krav4.kravgrunnlag.periode,
                        behandlingstype = Behandlingssammendrag.Behandlingstype.KRAVGRUNNLAG,
                        behandlingStartet = krav4.eksternTidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.ÅPEN,
                    ),
                ).sortedBy { it.saksnummer.nummer }
        }
    }
}
