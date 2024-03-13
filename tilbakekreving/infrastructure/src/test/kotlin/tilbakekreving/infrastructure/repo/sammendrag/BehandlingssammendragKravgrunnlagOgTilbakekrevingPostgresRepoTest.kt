package tilbakekreving.infrastructure.repo.sammendrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.common.tid.periode.september
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import tilbakekreving.domain.BrevTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.ForhåndsvarsletTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.IverksattHendelse
import tilbakekreving.domain.NotatTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.OppdatertKravgrunnlagPåTilbakekrevingHendelse
import tilbakekreving.domain.TilAttesteringHendelse
import tilbakekreving.domain.UnderkjentHendelse
import tilbakekreving.domain.VurdertTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus

internal class BehandlingssammendragKravgrunnlagOgTilbakekrevingPostgresRepoTest {

    @Test
    fun `hent ferdige`() {
        // For å feilutbetaling for januar.
        val clock = TikkendeKlokke(fixedClockAt(1.januar(2022)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)

            // Siden vi har fjernet behandlingsIDene bruker vi perioden for å identifisere behandlingene.
            // De første behandlingene er åpne, og forventes filtrert bort.
            testDataHelper.tilbakekreving.persisterOpprettetTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(januar(2021)),
            )
            testDataHelper.tilbakekreving.persisterForhåndsvarsletTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(februar(2021)),
            )
            testDataHelper.tilbakekreving.persisterVurdertTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(mars(2021)),
            )
            testDataHelper.tilbakekreving.persisterVedtaksbrevTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(april(2021)),
            )
            testDataHelper.tilbakekreving.persisterNotatTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(mai(2021)),
            )
            testDataHelper.tilbakekreving.persisterTilbakekrevingsbehandlingTilAttesteringHendelse(
                stønadsperiode = Stønadsperiode.create(juni(2021)),
            )
            testDataHelper.tilbakekreving.persisterUnderkjentTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(juli(2021)),
            )
            testDataHelper.tilbakekreving.persisterOppdatertKravgrunnlagPåTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(august(2021)),
            )
            // Åpent kravgrunnlag der behandlingen ikke er startet. Denne skal ikke dukke opp.
            testDataHelper.persisterRevurderingIverksattOpphørt(
                periode = september(2021),
            )

            // Disse 2 behandlingene vil dekkes av ferdig
            val (sak1, _, _, _, h1) = testDataHelper.tilbakekreving.persisterIverksattTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(oktober(2021)),
            )
            val (sak2, _, _, _, _, _, h2) = testDataHelper.tilbakekreving.persisterAvbruttTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(november(2021)),
            )

            // Avsluttet kravgrunnlag der behandlingen ikke er startet. Denne skal dukke opp.
            val (sak3) = testDataHelper.persisterRevurderingIverksattOpphørt(
                periode = desember(2021),
            )
            val (_, h3) = testDataHelper.emulerViMottarKravgrunnlagstatusendring(
                sak = sak3,
                status = Kravgrunnlagstatus.Avsluttet,
            )

            val actual =
                testDataHelper.tilbakekreving.behandlingssammendragTilbakekrevingPostgresRepo.hentFerdige(
                    null,
                ).sortedBy { it.saksnummer.nummer }
            actual shouldBe
                listOf(
                    Behandlingssammendrag(
                        saksnummer = sak1.saksnummer,
                        periode = oktober(2021),
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h1.filterIsInstance<IverksattHendelse>().single().hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.IVERKSATT,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak2.saksnummer,
                        periode = november(2021),
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h2.hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.AVBRUTT,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak3.saksnummer,
                        periode = desember(2021),
                        behandlingstype = Behandlingssammendrag.Behandlingstype.KRAVGRUNNLAG,
                        behandlingStartet = h3.eksternTidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.AVSLUTTET,
                    ),
                ).sortedBy { it.saksnummer.nummer }
        }
    }

    @Test
    fun `hent åpne`() {
        // For å feilutbetaling
        val clock = TikkendeKlokke(fixedClockAt(1.januar(2022)))
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource = dataSource, clock = clock)
            // Siden vi har fjernet behandlingsIDene bruker vi perioden for å identifisere behandlingene.
            val (sak1, _, _, _, _, _, h1) = testDataHelper.tilbakekreving.persisterOpprettetTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(januar(2021)),
            )
            val (sak2, _, _, _, h2) = testDataHelper.tilbakekreving.persisterForhåndsvarsletTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(februar(2021)),
            )
            val (sak3, _, _, _, h3) = testDataHelper.tilbakekreving.persisterVurdertTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(mars(2021)),
            )
            val (sak4, _, _, _, h4) = testDataHelper.tilbakekreving.persisterVedtaksbrevTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(april(2021)),
            )
            val (sak5, _, _, _, h5) = testDataHelper.tilbakekreving.persisterNotatTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(mai(2021)),
            )
            val (sak6, _, _, _, h6) = testDataHelper.tilbakekreving.persisterTilbakekrevingsbehandlingTilAttesteringHendelse(
                stønadsperiode = Stønadsperiode.create(juni(2021)),
            )
            val (sak7, _, _, _, h7) = testDataHelper.tilbakekreving.persisterUnderkjentTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(juli(2021)),
            )
            val (sak8, _, _, _, h8) = testDataHelper.tilbakekreving.persisterOppdatertKravgrunnlagPåTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(august(2021)),
            )
            // Åpent kravgrunnlag der behandlingen ikke er startet. Denne forventer vi.
            val (sak9, _, _, _, _, h9) = testDataHelper.persisterRevurderingIverksattOpphørt(
                periode = september(2021),
            )
            // Disse 2 behandlingene vil dekkes av ferdig, ikke åpen. Vi forventer ikke de.
            testDataHelper.tilbakekreving.persisterIverksattTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(oktober(2021)),
            )
            testDataHelper.tilbakekreving.persisterAvbruttTilbakekrevingsbehandlingHendelse(
                stønadsperiode = Stønadsperiode.create(november(2021)),
            )
            // Avsluttet kravgrunnlag der behandlingen ikke er startet. Vi forventer ikke at denne er åpen.
            val (sak10) = testDataHelper.persisterRevurderingIverksattOpphørt(
                periode = desember(2021),
            )
            testDataHelper.emulerViMottarKravgrunnlagstatusendring(
                sak = sak10,
                status = Kravgrunnlagstatus.Avsluttet,
            )

            val actual =
                testDataHelper.tilbakekreving.behandlingssammendragTilbakekrevingPostgresRepo.hentÅpne(
                    null,
                ).sortedBy { it.saksnummer.nummer }
            actual shouldBe
                listOf(
                    Behandlingssammendrag(
                        saksnummer = sak1.saksnummer,
                        periode = januar(2021),
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h1.hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak2.saksnummer,
                        periode = februar(2021),
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h2.filterIsInstance<ForhåndsvarsletTilbakekrevingsbehandlingHendelse>()
                            .single().hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak3.saksnummer,
                        periode = mars(2021),
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h3.filterIsInstance<VurdertTilbakekrevingsbehandlingHendelse>()
                            .single().hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak4.saksnummer,
                        periode = april(2021),
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h4.filterIsInstance<BrevTilbakekrevingsbehandlingHendelse>()
                            .single().hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak5.saksnummer,
                        periode = mai(2021),
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h5.filterIsInstance<NotatTilbakekrevingsbehandlingHendelse>()
                            .single().hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak6.saksnummer,
                        periode = juni(2021),
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h6.filterIsInstance<TilAttesteringHendelse>()
                            .single().hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.TIL_ATTESTERING,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak7.saksnummer,
                        periode = juli(2021),
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h7.filterIsInstance<UnderkjentHendelse>()
                            .single().hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.UNDERKJENT,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak8.saksnummer,
                        periode = august(2021),
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = h8.filterIsInstance<OppdatertKravgrunnlagPåTilbakekrevingHendelse>()
                            .single().hendelsestidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    ),
                    Behandlingssammendrag(
                        saksnummer = sak9.saksnummer,
                        periode = september(2021),
                        behandlingstype = Behandlingssammendrag.Behandlingstype.KRAVGRUNNLAG,
                        behandlingStartet = h9!!.eksternTidspunkt,
                        status = Behandlingssammendrag.Behandlingsstatus.ÅPEN,
                    ),
                ).sortedBy { it.saksnummer.nummer }
        }
    }
}
