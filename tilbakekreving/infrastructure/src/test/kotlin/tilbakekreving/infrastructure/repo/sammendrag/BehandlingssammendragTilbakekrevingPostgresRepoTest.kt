package tilbakekreving.infrastructure.repo.sammendrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import tilbakekreving.domain.BrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.ForhåndsvarsletTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.IverksattHendelse
import tilbakekreving.domain.TilAttesteringHendelse
import tilbakekreving.domain.VurdertTilbakekrevingsbehandlingHendelse

internal class BehandlingssammendragTilbakekrevingPostgresRepoTest {

    @Test
    fun `hent ferdige`() {
        // For å feilutbetaling for januar.
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)

            val (sak1, _, _, _, h1) = testDataHelper.tilbakekreving.persisterIverksattTilbakekrevingsbehandlingHendelse()
            val (sak2, _, _, _, h2) = testDataHelper.tilbakekreving.persisterAvbruttTilbakekrevingsbehandlingHendelse()
            testDataHelper.tilbakekreving.persisterOpprettetTilbakekrevingsbehandlingHendelse()
            testDataHelper.tilbakekreving.persisterTilbakekrevingsbehandlingTilAttesteringHendelse()
            testDataHelper.tilbakekreving.persisterForhåndsvarsletTilbakekrevingsbehandlingHendelse()
            testDataHelper.tilbakekreving.persisterVedtaksbrevTilbakekrevingsbehandlingHendelse()
            testDataHelper.tilbakekreving.persisterVurdertTilbakekrevingsbehandlingHendelse()
            // TODO jah: Bør ha en for underkjent og oppdatert kravgrunnlag

            val actual =
                testDataHelper.tilbakekreving.behandlingssammendragTilbakekrevingPostgresRepo.hentFerdige(
                    null,
                ).sortedBy { it.saksnummer.nummer }
            actual shouldBe
                listOf(
                    Behandlingssammendrag(
                        saksnummer = sak1.saksnummer,
                        periode = null,
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h1.filterIsInstance<IverksattHendelse>().single().hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.IVERKSATT,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak2.saksnummer,
                        periode = null,
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h2.hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.AVBRUTT,
                    ),
                ).sortedBy { it.saksnummer.nummer }
        }
    }

    @Test
    fun `hent åpne`() {
        // For å feilutbetaling for januar.
        val clock = TikkendeKlokke(fixedClockAt(1.februar(2021)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)

            val (sak1, _, _, _, h1) = testDataHelper.tilbakekreving.persisterOpprettetTilbakekrevingsbehandlingHendelse()
            val (sak2, _, _, _, h2) = testDataHelper.tilbakekreving.persisterTilbakekrevingsbehandlingTilAttesteringHendelse()
            val (sak3, _, _, _, h3) = testDataHelper.tilbakekreving.persisterForhåndsvarsletTilbakekrevingsbehandlingHendelse()
            val (sak4, _, _, _, h4) = testDataHelper.tilbakekreving.persisterVedtaksbrevTilbakekrevingsbehandlingHendelse()
            val (sak5, _, _, _, h5) = testDataHelper.tilbakekreving.persisterVurdertTilbakekrevingsbehandlingHendelse()
            // TODO jah: Bør ha en for underkjent og oppdatert kravgrunnlag
            testDataHelper.tilbakekreving.persisterIverksattTilbakekrevingsbehandlingHendelse()
            testDataHelper.tilbakekreving.persisterAvbruttTilbakekrevingsbehandlingHendelse()

            val actual =
                testDataHelper.tilbakekreving.behandlingssammendragTilbakekrevingPostgresRepo.hentÅpne(
                    null,
                ).sortedBy { it.saksnummer.nummer }
            actual shouldBe
                listOf(
                    Behandlingssammendrag(
                        saksnummer = sak1.saksnummer,
                        periode = null,
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h1.hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak2.saksnummer,
                        periode = null,
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h2.filterIsInstance<TilAttesteringHendelse>().single().hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.TIL_ATTESTERING,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak3.saksnummer,
                        periode = null,
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h3.filterIsInstance<ForhåndsvarsletTilbakekrevingsbehandlingHendelse>().single().hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak4.saksnummer,
                        periode = null,
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h4.filterIsInstance<BrevTilbakekrevingsbehandlingHendelse>().single().hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak5.saksnummer,
                        periode = null,
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h5.filterIsInstance<VurdertTilbakekrevingsbehandlingHendelse>().single().hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    ),
                ).sortedBy { it.saksnummer.nummer }
        }
    }
}
